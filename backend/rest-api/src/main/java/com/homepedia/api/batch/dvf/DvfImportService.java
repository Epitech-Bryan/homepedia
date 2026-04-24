package com.homepedia.api.batch.dvf;

import com.homepedia.api.batch.shared.ParseUtils;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DvfImportService {

	private static final DateTimeFormatter DVF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final int BATCH_SIZE = 1000;

	private final TransactionRepository transactionRepository;
	private final CityRepository cityRepository;

	@Transactional
	public int importFromZip(Path zipPath) throws IOException {
		log.info("Starting DVF import from {}", zipPath);
		var totalImported = 0;

		try (final var zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().endsWith(".csv")) {
					totalImported += importCsvStream(zis);
				}
				zis.closeEntry();
			}
		}

		log.info("DVF import complete: {} transactions imported", totalImported);
		return totalImported;
	}

	@Transactional
	public int importFromCsv(Path csvPath) throws IOException {
		log.info("Starting DVF import from CSV {}", csvPath);
		int totalImported;

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			totalImported = importFromReader(reader);
		}

		log.info("DVF import complete: {} transactions imported", totalImported);
		return totalImported;
	}

	@Transactional
	public int importFromGzip(Path gzipPath) throws IOException {
		log.info("Starting DVF import from gzipped CSV {}", gzipPath);
		int totalImported;

		try (final var gis = new java.util.zip.GZIPInputStream(Files.newInputStream(gzipPath));
				final var reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
			totalImported = importFromReader(reader);
		}

		log.info("DVF import complete: {} transactions imported", totalImported);
		return totalImported;
	}

	private int importFromReader(BufferedReader reader) throws IOException {
		final var headerLine = reader.readLine();
		if (headerLine == null) {
			return 0;
		}

		final var batch = new ArrayList<RealEstateTransaction>(BATCH_SIZE);
		var count = 0;
		String line;

		while ((line = reader.readLine()) != null) {
			parseLine(line).ifPresent(batch::add);

			if (batch.size() >= BATCH_SIZE) {
				transactionRepository.saveAll(batch);
				count += batch.size();
				batch.clear();
				if (count % 10000 == 0) {
					log.info("Imported {} transactions...", count);
				}
			}
		}

		if (!batch.isEmpty()) {
			transactionRepository.saveAll(batch);
			count += batch.size();
		}

		return count;
	}

	private int importCsvStream(ZipInputStream zis) throws IOException {
		final var reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		return importFromReader(reader);
	}

	private Optional<RealEstateTransaction> parseLine(String line) {
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
				cityRepository.findByInseeCode(inseeCode).ifPresent(transaction::setCity);
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
