package com.homepedia.pipeline.review;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class ReviewDataGenerator {

	private static final List<String> POSITIVE_TEMPLATES = List.of(
			"Ville très agréable pour vivre en famille. Les espaces verts sont magnifiques et bien entretenus.",
			"Quartier calme et propre, les voisins sont accueillants. Excellent cadre de vie.",
			"Le réseau de transport est pratique et moderne. On se déplace facilement partout.",
			"Cadre de vie parfait, ville dynamique avec beaucoup d'activités culturelles.",
			"Belle ville avec des écoles de qualité. L'environnement est verdoyant et tranquille.",
			"Très bien desservie par les transports en commun. Ville agréable et sécuritaire.",
			"Excellent rapport qualité de vie. Les commerces sont pratiques et le centre-ville est magnifique.",
			"Ville accueillante avec un marché formidable. Les parcs sont propres et bien aménagés.",
			"Cadre verdoyant et calme, parfait pour les enfants. Les écoles sont excellentes.",
			"Dynamique et moderne, cette ville offre un cadre de vie agréable et tranquille.");

	private static final List<String> NEGATIVE_TEMPLATES = List.of(
			"Quartier bruyant et sale. Il y a un vrai problème d'insécurité le soir.",
			"Ville trop chère pour ce qu'elle offre. L'environnement est pollué et dégradé.",
			"Manque cruel de transports. Les routes sont en mauvais état, c'est horrible.",
			"Insécurité grandissante, quartier dangereux la nuit. Abandon total des autorités.",
			"Le centre-ville est nul, tout est dégradé. Les commerces ferment les uns après les autres.",
			"Trop de bruit et de pollution. Le stationnement est un problème permanent.",
			"Ville difficile à vivre au quotidien. Le manque de médecins est un vrai problème.",
			"Le pire endroit où j'ai vécu. Sale, bruyant, dangereux. Rien ne fonctionne.",
			"Loyers chers pour des logements en mauvais état. Quartier dégradé et pollué.",
			"Abandon complet des espaces publics. Insécurité et problèmes de voisinage constants.");

	private static final List<String> NEUTRAL_TEMPLATES = List.of(
			"Ville correcte dans l'ensemble. Quelques commerces pratiques mais le stationnement reste compliqué.",
			"Le quartier est acceptable. Pas de problème majeur mais rien d'exceptionnel non plus.",
			"Quelques points positifs comme le marché, mais les transports pourraient être améliorés.",
			"Ville moyenne. Le cadre est correct mais il manque des infrastructures sportives.",
			"On y vit correctement. Les écoles sont acceptables mais le centre manque de dynamisme.",
			"Bonne desserte en transport mais les espaces verts sont insuffisants. Quartier convenable.",
			"Ville en transition. Certains quartiers sont agréables, d'autres un peu dégradés.",
			"Le rapport qualité-prix est moyen. Quelques nuisances sonores mais un voisinage correct.",
			"Pas mal pour les familles mais il manque d'activités culturelles le week-end.",
			"Ville qui a du potentiel mais qui souffre de quelques problèmes d'aménagement.");

	private static final List<String> AUTHORS = List.of("Marie L.", "Pierre D.", "Sophie M.", "Jean-Paul R.",
			"Isabelle F.", "François B.", "Nathalie G.", "Laurent T.", "Catherine V.", "Michel P.", "Anne-Marie S.",
			"Thierry H.", "Véronique C.", "Christophe A.", "Brigitte N.", "Patrick E.", "Monique J.", "Alain K.",
			"Martine W.", "Gérard Z.");

	private final Random random = new Random(42);

	public List<GeneratedReview> generateForCity(final String cityInseeCode) {
		final var count = 5 + random.nextInt(11);
		final var reviews = new ArrayList<GeneratedReview>(count);

		for (var i = 0; i < count; i++) {
			final var sentiment = random.nextDouble();
			final String content;
			final double rating;

			if (sentiment < 0.4) {
				content = POSITIVE_TEMPLATES.get(random.nextInt(POSITIVE_TEMPLATES.size()));
				rating = 3.5 + random.nextDouble() * 1.5;
			} else if (sentiment < 0.7) {
				content = NEUTRAL_TEMPLATES.get(random.nextInt(NEUTRAL_TEMPLATES.size()));
				rating = 2.5 + random.nextDouble();
			} else {
				content = NEGATIVE_TEMPLATES.get(random.nextInt(NEGATIVE_TEMPLATES.size()));
				rating = 1.0 + random.nextDouble() * 1.5;
			}

			final var author = AUTHORS.get(random.nextInt(AUTHORS.size()));
			final var daysAgo = random.nextInt(365 * 4);
			final var publishedAt = LocalDate.of(2024, 6, 1).minusDays(daysAgo);

			reviews.add(
					new GeneratedReview(cityInseeCode, content, Math.round(rating * 10.0) / 10.0, author, publishedAt));
		}

		return reviews;
	}

	public record GeneratedReview(String cityInseeCode, String content, double rating, String author,
			LocalDate publishedAt) {
	}
}
