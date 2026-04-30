package com.homepedia.common.transaction;

import com.homepedia.common.city.City;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
public class RealEstateTransaction {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	private Long id;

	/**
	 * DVF mutation identifier (e.g. "2024-1234567"). One mutation = one real sale;
	 * the source CSV publishes one row per "lot" of that mutation, all sharing the
	 * same mutation_id and the same total price. Stats aggregations group on this
	 * column to count one mutation once.
	 */
	@Column(name = "mutation_id")
	private String mutationId;

	private LocalDate mutationDate;

	private String mutationNature;

	@Column(name = "property_value")
	private BigDecimal propertyValue;

	private String streetNumber;

	private String postalCode;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "city_insee_code")
	private City city;

	private String section;

	private String planNumber;

	private Integer lotCount;

	@Enumerated(STRING)
	private PropertyType propertyType;

	private Double builtSurface;

	private Integer roomCount;

	private Double landSurface;

	private String streetType;
}
