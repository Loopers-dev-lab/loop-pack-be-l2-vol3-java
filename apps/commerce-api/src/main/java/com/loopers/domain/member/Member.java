package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
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

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "point", nullable = false))
    private Money point;

    private Member(MemberId memberId, Password password, Name name, Email email, BirthDate birthDate, Money point) {
        this.memberId = memberId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.point = point;
    }

    public static Member create(MemberId memberId, Password password, Name name, Email email, BirthDate birthDate) {
        return new Member(memberId, password, name, email, birthDate, Money.zero());
    }

    public void deductPoint(Money amount) {
        if (!this.point.isGreaterThanOrEqual(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.");
        }
        this.point = this.point.subtract(amount);
    }

    public void addPoint(Money amount) {
        this.point = this.point.add(amount);
    }

    public void updatePassword(String currentPassword, String newPassword, PasswordEncoder encoder) {
        this.password = this.password.change(currentPassword, newPassword, this.birthDate, encoder);
    }
}
