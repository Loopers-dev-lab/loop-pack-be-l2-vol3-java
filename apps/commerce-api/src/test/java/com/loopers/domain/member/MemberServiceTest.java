package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberServiceTest {

    private MemberService memberService;
    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원가입에 성공하면 저장된 회원을 반환한다.")
    @Test
    void signup_success() {
        // given
        MemberService.SignupCommand command = new MemberService.SignupCommand(
                "user1",
                "Password1!",
                "홍길동",
                "test@test.com",
                "1997-01-01"
        );

        given(memberRepository.existsByMemberIdValue(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Member member = memberService.signup(command);

        // then
        assertThat(member).isNotNull();
        assertThat(member.getMemberId().getValue()).isEqualTo("user1");
        verify(memberRepository).save(any(Member.class));
    }

    @DisplayName("이미 존재하는 ID로 회원가입하면 예외가 발생한다.")
    @Test
    void signup_fail_duplicate_id() {
        // given
        MemberService.SignupCommand command = new MemberService.SignupCommand(
                "existing1",
                "Password1!",
                "홍길동",
                "test@test.com",
                "1997-01-01"
        );

        given(memberRepository.existsByMemberIdValue(anyString())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signup(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("회원가입 시 비밀번호는 암호화되어 저장된다.")
    @Test
    void signup_password_encoded() {
        // given
        String rawPassword = "Password1!";
        String encodedPassword = "$2a$10$encodedPasswordValue";

        MemberService.SignupCommand command = new MemberService.SignupCommand(
                "user1",
                rawPassword,
                "홍길동",
                "test@test.com",
                "1997-01-01"
        );

        given(memberRepository.existsByMemberIdValue(anyString())).willReturn(false);
        given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Member member = memberService.signup(command);

        // then
        assertThat(member.getPassword().getValue()).isEqualTo(encodedPassword);
        verify(passwordEncoder).encode(rawPassword);
    }
}
