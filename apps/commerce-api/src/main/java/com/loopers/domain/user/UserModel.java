package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true, length = 10)
    private String userId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private String birthDate;

    @Column(name = "encrypted_password", nullable = false)
    private String encryptedPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "points", nullable = false)
    private Long points;

    protected UserModel() {}

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

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public Gender getGender() {
        return gender;
    }

    public Long getPoints() {
        return points;
    }
}
