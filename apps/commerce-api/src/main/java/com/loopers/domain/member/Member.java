package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {
    
    private String loginId;
    private String encryptedPassword;
    private String name;
    private String birthDate;
    private String email;
    
    protected Member() {}
    
    public Member(String loginId, String rawPassword, String name, 
                  String birthDate, String email) {
        validateLoginId(loginId);
        validatePassword(rawPassword);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);
        
        this.loginId = loginId;
        this.encryptedPassword = rawPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }
    
    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인ID는 필수입니다");
        }
    }
    
    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다");
        }
    }
    
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다");
        }
    }
    
    private void validateBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다");
        }
    }
    
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다");
        }
    }
    
    public String getLoginId() {
        return loginId;
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
}
