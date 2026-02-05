package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.application.service.dto.MyMemberInfoResponse;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.infrastructure.member.MemberRepository;
import com.loopers.utils.PasswordEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    public void 회원가입_성공() throws Exception {
        // given
        String inputId = "integrationId123";
        MemberRegisterRequest request = MemberRegisterRequest.builder()
                .loginId(inputId)
                .password("Pass!1234")
                .name("공명선")
                .birthdate(LocalDate.of(2001, 2, 9))
                .email("test@loopers.com")
                .build();

        // when
        memberService.register(request);

        // then
        assertThat(memberRepository.existsByLoginId(inputId)).isTrue();
    }

    @Test
    void 회원가입_시_중복_아이디_사용_불가() {
        // given
        String duplicateId = "existingId";
        memberRepository.save(Member.builder()
                .loginId(duplicateId)
                .password("encodedPassword")
                .name("기존유저")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("old@test.com")
                .build());

        MemberRegisterRequest request = MemberRegisterRequest.builder()
                .loginId(duplicateId)
                .password("NewPass!123")
                .name("신규유저")
                .birthdate(LocalDate.of(2000, 1, 1))
                .email("new@test.com")
                .build();

        // when & then
        assertThatThrownBy(() -> memberService.register(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조회 성공: 올바른 ID와 비밀번호를 입력하면 마스킹된 정보를 반환한다")
    void getMyInfo_Success() {
        // given
        String loginId = "tester123";
        String password = "password123!";
        memberRepository.save(Member.builder()
                .loginId(loginId)
                .password(PasswordEncryptor.encode(password)) // 실제로는 암호화된 값이 들어갈 지점
                .name("공명선")
                .birthDate(LocalDate.of(2001, 2, 9))
                .email("test@loopers.com")
                .build());

        // when
        MyMemberInfoResponse response = memberService.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);
        assertThat(response.name()).isEqualTo("공명*"); // 마스킹 검증
        assertThat(response.email()).isEqualTo("test@loopers.com");
    }

    @Test
    @DisplayName("조회 실패: 아이디는 맞지만 비밀번호가 틀리면 예외를 던진다")
    void getMyInfo_Fail_InvalidPassword() {
        // given
        String loginId = "tester123";
        memberRepository.save(Member.builder()
                .loginId(loginId)
                .password("correctPassword")
                .name("공명선")
                .birthDate(LocalDate.of(2001, 2, 9))
                .email("test@loopers.com")
                .build());

        // when & then
        assertThatThrownBy(() -> memberService.getMyInfo(loginId, "wrongPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

    @Test
    @DisplayName("조회 실패: 존재하지 않는 아이디로 조회하면 예외를 던진다")
    void getMyInfo_Fail_NotFoundId() {
        // given
        String unknownId = "nobody";
        String anyPassword = "anyPassword";

        // when & then
        assertThatThrownBy(() -> memberService.getMyInfo(unknownId, anyPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.ExistsMember.CANNOT_LOGIN.message());
    }

}
