package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.domain.member.Member;
import com.loopers.infrastructure.member.MemberRepository;
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

}
