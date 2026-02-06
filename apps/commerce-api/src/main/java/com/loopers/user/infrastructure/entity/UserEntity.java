package com.loopers.user.infrastructure.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.user.domain.model.User;
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

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "birthday", nullable = false)
	private LocalDate birthday;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	private UserEntity(String loginId, String password, String name, LocalDate birthday, String email) {
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthday = birthday;
		this.email = email;
	}

	public static UserEntity from(User user) {
		return new UserEntity(
			user.getLoginId(),
			user.getPassword().value(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}

	public void updatePassword(String password) {
		this.password = password;
	}

	public User toDomain() {
		return User.reconstruct(
			this.getId(),
			this.loginId,
			this.password,
			this.name,
			this.birthday,
			this.email
		);
	}
}
