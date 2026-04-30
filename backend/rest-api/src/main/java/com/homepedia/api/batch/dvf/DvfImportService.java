package com.homepedia.api.batch.dvf;

import com.homepedia.api.batch.shared.ParseUtils;
import com.homepedia.common.city.City;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.RealEstateTransaction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Drives a DVF import for a given year. The output goes into a shadow partition
 * that's swapped into the {@code transactions} parent only once the file has
 * been fully processed — see {@link DvfBatchPersister}. Rows whose
 * {@code mutationDate} falls outside the requested year are dropped (DVF
 * occasionally bleeds a few days across calendar boundaries).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DvfImportService {

	private static final DateTimeFormatter DVF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * COPY FROM STDIN scales near-linearly up to ~50k rows per network round-trip.
	 * The previous value of 1000 left ~30% of the throughput on the table.
	 */
	private static final int BATCH_SIZE = 50_000;

	private final DvfBatchPersister persister;
	private final CityCacheLoader cityCacheLoader;

	public int importFromZip(int year, Path zipPath) throws IOException {
		log.info("Starting DVF import for year {} from {}", year, zipPath);
		persister.prepareShadow(year);
		final var citiesByInsee = cityCacheLoader.load();
		var totalImported = 0;

		try (final var zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().endsWith(".csv")) {
					totalImported += importCsvStream(year, zis, citiesByInsee);
				}
				zis.closeEntry();
			}
		}

		persister.swapPartition(year);
		persister.analyzePartition(year);
		log.info("DVF import for year {} complete: {} transactions imported", year, totalImported);
		return totalImported;
	}

	public int importFromCsv(int year, Path csvPath) throws IOException {
		log.info("Starting DVF import for year {} from CSV {}", year, csvPath);
		persister.prepareShadow(year);
		final var citiesByInsee = cityCacheLoader.load();
		final int totalImported;

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			totalImported = importFromReader(year, reader, citiesByInsee);
		}

		persister.swapPartition(year);
		persister.analyzePartition(year);
		log.info("DVF import for year {} complete: {} transactions imported", year, totalImported);
		return totalImported;
	}

	public int importFromGzip(int year, Path gzipPath) throws IOException {
		log.info("Starting DVF import for year {} from gzipped CSV {}", year, gzipPath);
		persister.prepareShadow(year);
		final var citiesByInsee = cityCacheLoader.load();
		final int totalImported;

		try (final var gis = new java.util.zip.GZIPInputStream(Files.newInputStream(gzipPath));
				final var reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
			totalImported = importFromReader(year, reader, citiesByInsee);
		}

		persister.swapPartition(year);
		persister.analyzePartition(year);
		log.info("DVF import for year {} complete: {} transactions imported", year, totalImported);
		return totalImported;
	}

	/**
	 * Stream-import from an arbitrary {@link InputStream} (e.g. HTTP body). Lets
	 * callers skip the download-to-temp-file roundtrip entirely; the COPY starts as
	 * soon as the first bytes arrive over the network.
	 *
	 * @param gzip
	 *            whether the stream is gzipped
	 */
	public int importFromStream(int year, InputStream raw, boolean gzip) throws IOException {
		log.info("Starting DVF import for year {} from streaming source (gzip={})", year, gzip);
		persister.prepareShadow(year);
		final var citiesByInsee = cityCacheLoader.load();
		final int totalImported;

		try (final var decoded = gzip ? new java.util.zip.GZIPInputStream(raw) : raw;
				final var reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
			totalImported = importFromReader(year, reader, citiesByInsee);
		}

		persister.swapPartition(year);
		persister.analyzePartition(year);
		log.info("DVF import for year {} complete: {} transactions imported", year, totalImported);
		return totalImported;
	}

	private int importFromReader(int year, BufferedReader reader, Map<String, City> citiesByInsee) throws IOException {
		final var headerLine = reader.readLine();
		if (headerLine == null) {
			return 0;
		}

		final var batch = new ArrayList<RealEstateTransaction>(BATCH_SIZE);
		var count = 0;
		String line;

		while ((line = reader.readLine()) != null) {
			parseLine(line, citiesByInsee).filter(t -> matchesYear(t, year)).ifPresent(batch::add);

			if (batch.size() >= BATCH_SIZE) {
				persister.saveBatch(year, batch);
				count += batch.size();
				batch.clear();
				if (count % 100_000 == 0) {
					log.info("Imported {} transactions for year {}...", count, year);
				}
			}
		}

		if (!batch.isEmpty()) {
			persister.saveBatch(year, batch);
			count += batch.size();
		}

		return count;
	}

	private int importCsvStream(int year, ZipInputStream zis, Map<String, City> citiesByInsee) throws IOException {
		final var reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		return importFromReader(year, reader, citiesByInsee);
	}

	private static boolean matchesYear(RealEstateTransaction tx, int year) {
		final var d = tx.getMutationDate();
		return d != null && d.getYear() == year;
	}

	private Optional<RealEstateTransaction> parseLine(String line, Map<String, City> citiesByInsee) {
		try {
			final var fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
			if (fields.length < 38) {
				return Optional.empty();
			}

			final var idParcelle = clean(fields[15]);
			String section = null;
			String noPlan = null;
			if (idParcelle != null && idParcelle.length() >= 6) {
				section = idParcelle.substring(idParcelle.length() - 8, idParcelle.length() - 4).trim();
				noPlan = idParcelle.substring(idParcelle.length() - 4);
			}

			final var raw = new DvfRawRecord(clean(fields[1]), clean(fields[3]), clean(fields[4]), clean(fields[5]),
					clean(fields[8]), clean(fields[9]), clean(fields[11]), clean(fields[12]), clean(fields[10]),
					section, noPlan, clean(fields[28]), clean(fields[30]), clean(fields[31]), clean(fields[32]),
					clean(fields[37]), null);

			final var transaction = RealEstateTransaction.builder()
					.mutationDate(LocalDate.parse(raw.dateMutation(), DVF_DATE_FORMAT))
					.mutationNature(raw.natureMutation()).propertyValue(raw.parsedValeurFonciere())
					.streetNumber(raw.noVoie()).postalCode(raw.codePostal()).section(raw.section())
					.planNumber(raw.noPlan()).streetType(raw.typeVoie()).propertyType(mapPropertyType(raw.typeLocal()))
					.builtSurface(ParseUtils.parseDouble(raw.surfaceReelleBati()))
					.roomCount(ParseUtils.parseInteger(raw.nombrePiecesPrincipales()))
					.landSurface(ParseUtils.parseDouble(raw.surfaceTerrain()))
					.lotCount(ParseUtils.parseInteger(raw.nombreDeLots())).build();

			final var inseeCode = raw.fullInseeCode();
			if (StringUtils.isNotBlank(inseeCode)) {
				final var city = citiesByInsee.get(inseeCode);
				if (city != null) {
					transaction.setCity(city);
				}
			}

			return Optional.of(transaction);
		} catch (Exception e) {
			log.debug("Skipping invalid DVF line: {}", e.getMessage());
			return Optional.empty();
		}
	}

	private static String clean(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		final var trimmed = value.trim().replace("\"", "");
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static PropertyType mapPropertyType(String typeLocal) {
		if (StringUtils.isBlank(typeLocal)) {
			return null;
		}
		return switch (typeLocal.toLowerCase()) {
			case "maison" -> PropertyType.MAISON;
			case "appartement" -> PropertyType.APPARTEMENT;
			case "dépendance", "dependance" -> PropertyType.DEPENDANCE;
			case "local industriel. commercial ou assimilé", "local industriel commercial ou assimile", "local" ->
				PropertyType.LOCAL;
			default -> null;
		};
	}
}
