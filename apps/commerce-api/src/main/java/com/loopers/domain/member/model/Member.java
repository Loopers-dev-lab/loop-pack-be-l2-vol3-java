package com.loopers.domain.member.model;

import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.domain.member.vo.*;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class Member {

    private Long id;
    private LoginId loginId;
    private Password password;
    private MemberName name;
    private BirthDate birthDate;
    private Email email;

    private Member(LoginId loginId, Password password, MemberName name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static Member signUp(MemberCommand.SignUp command, PasswordEncryptor encryptor) {
        BirthDate birthDate = new BirthDate(command.birthDate());
        return new Member(
            new LoginId(command.loginId()),
            Password.create(command.rawPassword(), birthDate.toFormattedString(), encryptor),
            new MemberName(command.name()),
            birthDate,
            new Email(command.email())
        );
    }

    public static Member reconstruct(Long id, String loginId, String password, String name, LocalDate birthDate, String email) {
        Member member = new Member(
            new LoginId(loginId),
            new Password(password),
            new MemberName(name),
            new BirthDate(birthDate),
            new Email(email)
        );
        member.id = id;
        return member;
    }

    public void changePassword(Password newPassword) {
        this.password = newPassword;
    }

}
