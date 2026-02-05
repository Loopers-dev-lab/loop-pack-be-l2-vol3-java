package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private String loginId;
    private String password ;
    private String name;
    private String birthDate;
    private String email;

    protected MemberModel() {}

    public MemberModel(String loginId, String password, String name, String birthDate, String email) {

      // 모든 항목은 비어 있을 수 없다
      if (loginId == null || loginId.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.");
      }
      if (password == null || password.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
      }
      if (name == null || name.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
      }
      if (birthDate == null || birthDate.isBlank()) {
          throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
      }
      if (email == null || email.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
      }

        // 가입된 아이디로는 가입이 불가능하다 -> 디비에서 검증. 서비스에서 하기
        // 가입하는 로그인 ID는 영문과 숫자만 허용한다
        validateLoginId(loginId);

        // 비밀번호 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.
        // 비밀번호 생년월일은 비밀번호 내에 포함될 수 없습니다.
        // 비밀번호 규칙 검증
        validatePassword(password, birthDate);

      this.loginId = loginId;
      this.password = password;
      this.name = name;
      this.birthDate = birthDate;
      this.email = email;
    }

    public MemberModel(String loginId) {
        // 가입하는 로그인 ID는 영문과 숫자만 허용한다
        validateLoginId(loginId);
        this.loginId = loginId;
    }

    public String getLoginId() {
      return loginId;
    }

    public String getPassword() {
      return password;
    }

    public String getName() {
      return name;
    }

    public String getBirthDate() {
      return birthDate;
    }

    public String getEmail() {
      return email;
    }

    private void validateLoginId(String loginId) {
        // 로그인 ID 는 영문과 숫자만 허용
        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "로그인 아이디는 영문자와 숫자만 사용할 수 있습니다."
            );
        }
    }

    private void validatePassword(String password, String birthDate) {
        // 1. 8~16자 길이 체크
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자여야 합니다.");
        }

        // 2. 영문 대소문자, 숫자, 특수문자만 허용 (한글, 공백 등 불가)
        if (!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }

        // 3. 생년월일이 비밀번호에 포함되면 안됨
        if (password.contains(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    // 암호화된 비밀번호를 엔티티에 넣어주기
    public void encryptPassword(String encryptedPassword) {
      this.password = encryptedPassword;
    }

    // 이름 마지막 글자에 마스킹 추가
    public String maskLastChar(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 비어 있을 수 없습니다.");
        }

        if (name.length() == 1) {
            return "*";
        }

        return name.substring(0, name.length() - 1) + "*";
    }

    // 마스킹된 이름 가져오기
    public String getMaskedName(){
        return maskLastChar(this.name);
    }


}
