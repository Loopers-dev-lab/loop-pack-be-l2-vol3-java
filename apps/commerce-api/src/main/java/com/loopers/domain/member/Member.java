package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "member_id", nullable = false, unique = true))
    private MemberId memberId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password", nullable = false))
    private Password password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private Name name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false))
    private Email email;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "birth_date", nullable = false))
    private BirthDate birthDate;

    public Member(MemberId memberId, Password password, Name name, Email email, BirthDate birthDate) {
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public void updatePassword(String currentPassword, String newPassword, PasswordEncoder encoder) {
        if (!this.password.matches(currentPassword, encoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        Password newPw = Password.of(newPassword, this.birthDate);
        String encodedNewPassword = encoder.encode(newPassword);

        if (this.password.matches(newPassword, encoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }

        this.password = Password.ofEncoded(encodedNewPassword);
    }

    public String getMaskedName() {
        return this.name.masked();
    }
}
