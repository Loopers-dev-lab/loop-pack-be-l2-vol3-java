package com.loopers.infrastructure.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.Phone;
import com.loopers.domain.user.vo.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Getter
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone")
    private String phone;

    protected UserEntity() {
    }

    public UserEntity(String userId, String password, String name, String email, LocalDate birthDate, String phone) {
        this.userId = userId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.phone = phone;
    }

    public static UserEntity from(User user) {
        return new UserEntity(
                user.id().value(),
                user.password().value(),
                user.name().value(),
                user.email().value(),
                user.birthDate().value(),
                user.phone() != null ? user.phone().value() : null
        );
    }

    public User toDomain() {
        return new User(
                new UserId(userId),
                Password.ofEncoded(password),
                new Name(name),
                new Email(email),
                new BirthDate(birthDate),
                phone != null ? new Phone(phone) : null
        );
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateFrom(User user) {
        this.password = user.password().value();
        this.name = user.name().value();
        this.email = user.email().value();
        this.birthDate = user.birthDate().value();
        this.phone = user.phone() != null ? user.phone().value() : null;
    }
}
