package com.loopers.application.member;

import com.loopers.application.member.command.AuthenticateCommand;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAuthenticationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberAuthenticationService memberAuthenticationService;

    private MemberId memberId;
    private Member member;

    @BeforeEach
    void setUp() {
        memberId = new MemberId("testmember");
        member = new Member(
                memberId,
                Password.ofEncoded("$2a$10$dummyEncodedPasswordForTest"),
                new Name("홍길동"),
                new Email("test@example.com"),
                new BirthDate(LocalDate.of(1999, 1, 15)),
                new Phone("010-1234-5678")
        );
    }

    @Nested
    @DisplayName("인증")
    class AuthenticateTest {

        @Test
        @DisplayName("성공")
        void authenticateSuccess() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .memberId(memberId)
                    .rawPassword("1Q2w3e4r!")
                    .build();
            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("1Q2w3e4r!", member.password().value())).thenReturn(true);

            Member result = memberAuthenticationService.authenticate(command);

            assertThat(result.id()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void authenticateFailMemberNotFound() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .memberId(memberId)
                    .rawPassword("1Q2w3e4r!")
                    .build();
            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberAuthenticationService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치")
        void authenticateFailWrongPassword() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .memberId(memberId)
                    .rawPassword("wrongPassword")
                    .build();
            when(memberRepository.findByMemberId(memberId)).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("wrongPassword", member.password().value())).thenReturn(false);

            assertThatThrownBy(() -> memberAuthenticationService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}
