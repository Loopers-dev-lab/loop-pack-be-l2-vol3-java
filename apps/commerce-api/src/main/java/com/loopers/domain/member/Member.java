package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import lombok.Getter;

@Getter
public class Member {
    private final Long id;
    private final MemberId memberId;
    private Password password;
    private final Name name;
    private final Email email;
    private final BirthDate birthDate;

    private Member(Long id, MemberId memberId, Password password, Name name, Email email, BirthDate birthDate) {
        this.id = id;
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public static Member create(MemberId memberId, Password password, Name name, Email email, BirthDate birthDate) {
        return new Member(null, memberId, password, name, email, birthDate);
    }

    public static Member of(Long id, MemberId memberId, Password password, Name name, Email email, BirthDate birthDate) {
        return new Member(id, memberId, password, name, email, birthDate);
    }

    public void updatePassword(String currentPassword, String newPassword, PasswordEncoder encoder) {
        this.password = this.password.change(currentPassword, newPassword, this.birthDate, encoder);
    }
}
