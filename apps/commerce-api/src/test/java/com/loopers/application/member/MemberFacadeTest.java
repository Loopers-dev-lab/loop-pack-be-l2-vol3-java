package com.loopers.application.member;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MemberFacadeTest {

    private final MemberFacade memberFacade;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberFacadeTest(
        MemberFacade memberFacade,
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.memberFacade = memberFacade;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 요청할 때,")
    @Nested
    class SignUp {

        @DisplayName("정상적인 정보로 가입하면, MemberInfo를 반환한다.")
        @Test
        void signUp_withValidInfo_returnsMemberInfo() {
            // arrange
            String loginId = "testuser1";
            String password = "Test1234!";
            String name = "홍길동";
            LocalDate birthday = LocalDate.of(1995, 3, 15);
            String email = "test@example.com";

            // act
            MemberInfo info = memberFacade.signUp(loginId, password, name, birthday, email);

            // assert
            assertAll(
                () -> assertThat(info.id()).isNotNull(),
                () -> assertThat(info.loginId()).isEqualTo(loginId),
                () -> assertThat(info.name()).isEqualTo(name),
                () -> assertThat(info.birthday()).isEqualTo(birthday),
                () -> assertThat(info.email()).isEqualTo(email)
            );
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetMyInfo {

        @DisplayName("올바른 자격 증명으로 조회하면, MemberInfo를 반환한다.")
        @Test
        void getMyInfo_withValidCredentials_returnsMemberInfo() {
            // arrange
            String rawPassword = "Test1234!";
            Member member = new Member("testuser1", rawPassword, "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            member.encryptPassword(passwordEncoder.encode(rawPassword));
            memberRepository.save(member);

            // act
            MemberInfo info = memberFacade.getMyInfo("testuser1", rawPassword);

            // assert
            assertAll(
                () -> assertThat(info.loginId()).isEqualTo("testuser1"),
                () -> assertThat(info.name()).isEqualTo("홍길동"),
                () -> assertThat(info.birthday()).isEqualTo(LocalDate.of(1995, 3, 15)),
                () -> assertThat(info.email()).isEqualTo("test@example.com")
            );
        }
    }
}