package com.loopers.infrastructure.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
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

    protected UserEntity() {}

    public UserEntity(String userId, String password, String name, String email, LocalDate birthDate) {
        this.userId = userId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public static UserEntity from(User user) {
        return new UserEntity(
                user.id().value(),
                user.password().value(),
                user.name().value(),
                user.email().value(),
                user.birthDate().value()
        );
    }

    public User toDomain() {
        return new User(
                new UserId(userId),
                Password.ofEncoded(password),
                new Name(name),
                new Email(email),
                new BirthDate(birthDate)
        );
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

}
