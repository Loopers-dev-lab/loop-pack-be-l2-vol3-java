package com.loopers.application.member;

import com.loopers.domain.member.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberFacadeTest {

    @Mock
    private MemberSignupService memberSignupService;

    @Mock
    private MemberAuthService memberAuthService;

    @Mock
    private MemberPasswordService memberPasswordService;

    private MemberFacade memberFacade;

    @BeforeEach
    void setUp() {
        memberFacade = new MemberFacade(memberSignupService, memberAuthService, memberPasswordService);
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @DisplayName("SignupService에 위임하고 MemberInfo를 반환한다")
        @Test
        void delegatesToSignupService() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "encoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));
            when(memberSignupService.signup("kwonmo", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com")).thenReturn(member);

            // when
            MemberInfo result = memberFacade.signup("kwonmo", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // then
            assertAll(
                () -> assertThat(result.loginId()).isEqualTo("kwonmo"),
                () -> assertThat(result.name()).isEqualTo("양권모"),
                () -> assertThat(result.email()).isEqualTo("kwonmo@example.com")
            );
        }
    }

    @DisplayName("내 정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("인증 후 마스킹된 이름으로 반환한다")
        @Test
        void returnsWithMaskedName() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "encoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));
            when(memberAuthService.authenticate("kwonmo", "Test1234!")).thenReturn(member);

            // when
            MemberInfo result = memberFacade.getMyInfo("kwonmo", "Test1234!");

            // then
            assertAll(
                () -> assertThat(result.loginId()).isEqualTo("kwonmo"),
                () -> assertThat(result.name()).isEqualTo("양권*"),
                () -> assertThat(result.email()).isEqualTo("kwonmo@example.com")
            );
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("인증 후 PasswordService에 위임한다")
        @Test
        void delegatesToPasswordService() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "encoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));
            when(memberAuthService.authenticate("kwonmo", "Test1234!")).thenReturn(member);

            // when
            memberFacade.changePassword("kwonmo", "Test1234!", "Current1!", "NewPass5678!");

            // then
            verify(memberPasswordService).changePassword(member, "Current1!", "NewPass5678!");
        }
    }
}
