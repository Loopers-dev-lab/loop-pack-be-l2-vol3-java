package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "member")
@Getter
public class MemberModel extends BaseEntity {

	private String loginId;
	private String password;
	private String name;
	private LocalDate birthDate;
	private String email;

	protected MemberModel() {}

	public MemberModel(String loginId, String password, String name, LocalDate birthDate, String email) {
		if (loginId == null || loginId.isBlank()) {
			throw new CoreException(ErrorType.BAD_REQUEST, "loginId는 비어있을 수 없습니다.");
		}
		if (!loginId.matches("^[a-zA-Z0-9]+$")) {
			throw new CoreException(ErrorType.BAD_REQUEST, "loginId는 영문과 숫자만 허용됩니다.");
		}
		if (name == null || name.isBlank()) {
			throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
		}
		if (birthDate == null) {
			throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
		}
		validatePassword(password, birthDate);
		if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
			throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
		}

		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.birthDate = birthDate;
		this.email = email;
	}

	public String getMaskedName() {
		return name.substring(0, name.length() - 1) + "*";
	}

	public void changePassword(String newPassword) {
		validatePassword(newPassword, this.birthDate);
		this.password = newPassword;
	}

	public void applyEncodedPassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	private static void validatePassword(String password, LocalDate birthDate) {
		if (password == null || password.length() < 8 || password.length() > 16) {
			throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
		}
		if (!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$")) {
			throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
		}
		if (password.contains(birthDate.format(DateTimeFormatter.BASIC_ISO_DATE))) {
			throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
		}
	}
}
