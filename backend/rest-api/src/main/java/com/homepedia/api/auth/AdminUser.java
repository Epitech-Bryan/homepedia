package com.homepedia.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "admins")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class AdminUser {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 64)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 72)
	private String passwordHash;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
