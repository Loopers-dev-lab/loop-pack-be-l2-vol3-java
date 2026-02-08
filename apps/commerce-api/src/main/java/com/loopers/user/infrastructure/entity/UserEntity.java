package com.loopers.user.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {

	@Column(name = "login_id", nullable = false, unique = true, length = 20)
	private String loginId;

	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(name = "birthday", nullable = false)
	private LocalDate birthday;

	@Column(name = "email", nullable = false, length = 254)
	private String email;


	private UserEntity(Long id, String loginId, String password, String name, LocalDate birthday, String email) {
		super(id);
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthday = birthday;
		this.email = email;
	}


	public static UserEntity of(Long id, String loginId, String password, String name, LocalDate birthday, String email) {
		return new UserEntity(
			id,
			loginId,
			password,
			name,
			birthday,
			email
		);
	}

	public static UserEntity of(String loginId, String password, String name, LocalDate birthday, String email) {
		return of(null, loginId, password, name, birthday, email);
	}

}
