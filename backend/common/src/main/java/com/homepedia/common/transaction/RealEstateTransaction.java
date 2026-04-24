package com.homepedia.common.transaction;

import com.homepedia.common.city.City;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "transactions", indexes = {@Index(name = "idx_transaction_city", columnList = "city_insee_code"),
		@Index(name = "idx_transaction_date", columnList = "mutation_date"),
		@Index(name = "idx_transaction_type", columnList = "property_type")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
public class RealEstateTransaction {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate mutationDate;

	@Column(nullable = false, length = 50)
	private String mutationNature;

	@Column(precision = 15, scale = 2)
	private BigDecimal propertyValue;

	@Column(length = 10)
	private String streetNumber;

	@Column(length = 10)
	private String postalCode;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "city_insee_code")
	private City city;

	@Column(length = 10)
	private String section;

	@Column(length = 10)
	private String planNumber;

	private Integer lotCount;

	@Enumerated(STRING)
	@Column(length = 30)
	private PropertyType propertyType;

	private Double builtSurface;

	private Integer roomCount;

	private Double landSurface;

	private String streetType;
}
