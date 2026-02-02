package com.loopers.application.member;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MemberFacadeTest {

    @Autowired
    private MemberFacade memberFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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
                () -> assertThat(info.email()).isEqualTo(email)
            );
        }
    }
}