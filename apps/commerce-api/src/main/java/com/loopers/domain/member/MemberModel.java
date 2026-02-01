package com.loopers.domain.member;


import com.loopers.domain.BaseEntity;
import com.loopers.infrastructure.jpa.converter.BirthDateConverter;
import com.loopers.infrastructure.jpa.converter.EmailConverter;
import com.loopers.infrastructure.jpa.converter.MemberIdConverter;
import com.loopers.infrastructure.jpa.converter.NameConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Getter
    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;

    @Getter
    @Column(nullable = false)
    private String password;

    @Getter
    @Convert(converter = EmailConverter.class)
    @Column(length = 100)
    private Email email;

    @Getter
    @Convert(converter = BirthDateConverter.class)
    @Column(length = 10)
    private BirthDate birthDate;

    @Getter
    @Convert(converter = NameConverter.class)
    @Column(length = 50)
    private Name name;

    protected MemberModel() {}
    public MemberModel(String memberId, String password) {
        this.memberId = new MemberId(memberId);
        this.password = password;
    }

    public MemberModel(String memberId, String password, String email) {
        this(memberId, password);
        this.email = new Email(email);
    }

    public MemberModel(String memberId, String password, String email, String birthDate) {
        this(memberId, password, email);
        this.birthDate = BirthDate.fromString(birthDate);
    }

    public MemberModel(String memberId, String password, String email, String birthDate, String name) {
        this(memberId, password, email, birthDate);
        this.name = new Name(name);
    }
}
