package com.homepedia.pipeline.dvf;

import com.homepedia.common.city.CityRepository;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import com.homepedia.pipeline.shared.ParseUtils;
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

	private static final DateTimeFormatter DVF_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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

	private int importCsvStream(ZipInputStream zis) throws IOException {
		final var reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
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

	private Optional<RealEstateTransaction> parseLine(String line) {
		try {
			final var fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
			if (fields.length < 17) {
				return Optional.empty();
			}

			final var raw = new DvfRawRecord(clean(fields[0]), clean(fields[1]), clean(fields[2]), clean(fields[3]),
					clean(fields[4]), clean(fields[5]), clean(fields[6]), clean(fields[7]), clean(fields[8]),
					clean(fields[9]), clean(fields[10]), clean(fields[11]), clean(fields[12]), clean(fields[13]),
					clean(fields[14]), clean(fields[15]), clean(fields[16]));

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
