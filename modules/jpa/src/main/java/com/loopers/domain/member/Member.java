package com.loopers.domain.member;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;

    private String password;

    private String name;

    private LocalDate birthDate;

    private String email;

    public static Member register(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        return Member.builder()
                .loginId(loginId)
                .password(password)
                .name(name)
                .birthDate(birthDate)
                .email(email)
                .build();
    }

}
