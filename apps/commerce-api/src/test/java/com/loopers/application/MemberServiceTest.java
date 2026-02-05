package com.loopers.application;

import com.loopers.application.service.MemberService;
import com.loopers.application.service.dto.MemberRegisterRequest;
import com.loopers.domain.member.MemberExceptionMessage;
import com.loopers.infrastructure.member.MemberRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Test
    public void 회원가입_시_아이디_중복_불가() throws Exception {
        //given
        String inputId = "apape123";
        MemberRegisterRequest request = new MemberRegisterRequest(
                inputId, "password123!", "공명선", LocalDate.of(2001,2,9), "gms72901217@gmail.com"
        );

        //when
        when(memberRepository.existsByLoginId(inputId)).thenReturn(true);

        //then
        assertThatThrownBy(() -> memberService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MemberExceptionMessage.LoginId.DUPLICATE_ID_EXISTS.message());
    }

}
