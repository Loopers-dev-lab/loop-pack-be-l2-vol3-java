package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import lombok.Getter;

@Getter
public class MemberModel {

    private Long id;
    private LoginId loginId;
    private Password password;
    private MemberName name;
    private BirthDate birthDate;
    private Email email;

    private MemberModel(LoginId loginId, Password password, MemberName name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static MemberModel signUp(LoginId loginId, Password password, MemberName name, BirthDate birthDate, Email email) {
        return new MemberModel(loginId, password, name, birthDate, email);
    }

    public static MemberModel reconstruct(Long id, String loginId, String password, String name, java.time.LocalDate birthDate, String email) {
        MemberModel model = new MemberModel(
            new LoginId(loginId),
            new Password(password),
            new MemberName(name),
            new BirthDate(birthDate),
            new Email(email)
        );
        model.id = id;
        return model;
    }

    public void changePassword(Password newPassword) {
        this.password = newPassword;
    }
}
