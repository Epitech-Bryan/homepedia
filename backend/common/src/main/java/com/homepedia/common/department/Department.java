package com.homepedia.common.department;

import com.homepedia.common.region.Region;
import jakarta.persistence.*;
import lombok.*;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "departments")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class Department {

	@Id
	@Column(length = 3)
	private String code;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "region_code", nullable = false)
	private Region region;

	private Long population;

	private Double area;
}
