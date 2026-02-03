package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Entity
@Table(name = "users")
@Getter
public class UserModel extends BaseEntity {

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthday;
    private String email;

    protected UserModel(){}

    public UserModel(String loginId, String password, String name, String birthday, String email){
        if(loginId == null || loginId.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }

        if(password == null || password.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }

        if(name == null || name.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }

        if(email == null || email.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }

        if(birthday == null || birthday.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }

        // 이메일 형식 검증
        if(!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")){
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        // 비밀번호 자리수 검증
        if(password.length() < 8 || password.length() > 16){
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자리 이상, 16자리 이하이어야 합니다.");
        }

        // 비밀번호에 영문 대소문자, 숫자, 특수문자만 포함되어 있는지 검증
        if(!password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?]*$")){
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다.");
        }

        // 비밀번호에 생년월일이 포함되어 있는지 검증
        String birthdayWithoutDash = birthday.replace("-", "");
        if(password.contains(birthday) || password.contains(birthdayWithoutDash)){
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        try{
            this.birthday = LocalDate.parse(birthday);
        }catch(DateTimeParseException e){
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다.");
        }

        this.loginId = loginId;
        this.name = name;
        this.password = password;
        this.email = email;

    }
}
