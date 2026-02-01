package com.loopers.domain.member;


import com.loopers.domain.BaseEntity;
import com.loopers.infrastructure.jpa.converter.EmailConverter;
import com.loopers.infrastructure.jpa.converter.MemberIdConverter;
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

    protected MemberModel() {}
    public MemberModel(String memberId, String password) {
        this.memberId = new MemberId(memberId);
        this.password = password;
    }

    public MemberModel(String memberId, String password, String email) {
        this(memberId, password);
        this.email = new Email(email);
    }
}
