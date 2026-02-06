package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class UserModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true, length = 10)
    private String userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "birth_date", nullable = false, length = 10)
    private String birthDate;

    @Column(name = "encrypted_password", nullable = false, length = 255)
    private String encryptedPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "points", nullable = false)
    private Long points;

    private UserModel(
        String userId,
        String email,
        String birthDate,
        String encryptedPassword,
        Gender gender,
        Long points
    ) {
        this.userId = userId;
        this.email = email;
        this.birthDate = birthDate;
        this.encryptedPassword = encryptedPassword;
        this.gender = gender;
        this.points = points;
    }

    public static UserModel create(
        String userId,
        Email email,
        BirthDate birthDate,
        Password password,
        Gender gender
    ) {
        return new UserModel(
            userId,
            email.value(),
            birthDate.value(),
            password.encrypt(),
            gender,
            0L
        );
    }
}
