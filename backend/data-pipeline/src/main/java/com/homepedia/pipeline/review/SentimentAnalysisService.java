package com.homepedia.pipeline.review;

import com.homepedia.common.review.SentimentResult;
import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SentimentAnalysisService {

	private static final Set<String> POSITIVE_WORDS = Set.of("agreable", "calme", "propre", "securitaire", "dynamique",
			"belle", "bien", "excellent", "parfait", "magnifique", "tranquille", "verdoyant", "accueillant", "pratique",
			"moderne", "formidable", "qualite", "bon", "bonne", "super");

	private static final Set<String> NEGATIVE_WORDS = Set.of("bruyant", "sale", "dangereux", "cher", "pollue",
			"degrade", "insecurite", "probleme", "difficile", "mauvais", "nul", "horrible", "pire", "manque", "abandon",
			"bruit", "nuisance", "cruel");

	private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	public SentimentResult analyze(final String text) {
		if (StringUtils.isBlank(text)) {
			return new SentimentResult(0.0, "NEUTRAL");
		}

		final var normalized = removeDiacritics(text.toLowerCase());
		final var words = normalized.split("[^a-zA-Z]+");

		var positiveCount = 0;
		var negativeCount = 0;
		var totalWords = 0;

		for (final var word : words) {
			if (StringUtils.isBlank(word) || word.length() < 3) {
				continue;
			}
			totalWords++;
			if (POSITIVE_WORDS.contains(word)) {
				positiveCount++;
			} else if (NEGATIVE_WORDS.contains(word)) {
				negativeCount++;
			}
		}

		if (totalWords == 0) {
			return new SentimentResult(0.0, "NEUTRAL");
		}

		final var rawScore = (double) (positiveCount - negativeCount) / totalWords;
		final var score = Math.max(-1.0, Math.min(1.0, rawScore * 10));

		final String label;
		if (score > 0.1) {
			label = "POSITIVE";
		} else if (score < -0.1) {
			label = "NEGATIVE";
		} else {
			label = "NEUTRAL";
		}

		return new SentimentResult(Math.round(score * 1000.0) / 1000.0, label);
	}

	private String removeDiacritics(final String input) {
		final var normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
		return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
	}
}
