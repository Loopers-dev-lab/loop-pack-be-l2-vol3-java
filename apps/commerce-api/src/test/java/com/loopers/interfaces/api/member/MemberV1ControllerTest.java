package com.loopers.interfaces.api.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.MemberService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberV1Controller.class)
class MemberV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemberService memberService;

    @MockBean
    private MemberRepository memberRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @DisplayName("유효한 요청으로 회원가입하면 201 Created 응답을 받는다")
    @Test
    void signUp_withValidRequest_returnsCreated() throws Exception {
        // arrange
        MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
            "testuser1",
            "Password1!",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        MemberModel savedMember = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(memberService.register(anyString(), anyString(), anyString(), any(LocalDate.class), anyString()))
            .thenReturn(savedMember);

        // act & assert
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.loginId").value("testuser1"))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @DisplayName("로그인 ID에 영문/숫자 외 문자가 포함되면 400 Bad Request 응답을 받는다")
    @Test
    void signUp_withInvalidLoginIdFormat_returnsBadRequest() throws Exception {
        // arrange
        MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
            "테스트유저", // 한글 포함
            "Password1!",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        // act & assert
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @DisplayName("인증 헤더 없이 내 정보 조회하면 401 Unauthorized 응답을 받는다")
    @Test
    void getMyInfo_withoutAuthHeaders_returnsUnauthorized() throws Exception {
        // act & assert
        mockMvc.perform(get("/api/v1/members/me"))
            .andExpect(status().isUnauthorized());
    }

    @DisplayName("잘못된 비밀번호로 내 정보 조회하면 401 Unauthorized 응답을 받는다")
    @Test
    void getMyInfo_withWrongPassword_returnsUnauthorized() throws Exception {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(memberRepository.findByLoginId("testuser1")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // act & assert
        mockMvc.perform(get("/api/v1/members/me")
                .header("X-Loopers-LoginId", "testuser1")
                .header("X-Loopers-LoginPw", "wrongPassword"))
            .andExpect(status().isUnauthorized());
    }

    @DisplayName("올바른 인증 헤더로 내 정보 조회하면 200 OK와 마스킹된 이름을 받는다")
    @Test
    void getMyInfo_withValidAuth_returnsOkWithMaskedName() throws Exception {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        when(memberRepository.findByLoginId("testuser1")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);

        // act & assert
        mockMvc.perform(get("/api/v1/members/me")
                .header("X-Loopers-LoginId", "testuser1")
                .header("X-Loopers-LoginPw", "Password1!"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loginId").value("testuser1"))
            .andExpect(jsonPath("$.data.name").value("홍길*"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @DisplayName("올바른 인증 헤더로 비밀번호 변경하면 200 OK 응답을 받는다")
    @Test
    void changePassword_withValidAuth_returnsOk() throws Exception {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
            "OldPassword1!",
            "NewPassword1!"
        );

        when(memberRepository.findByLoginId("testuser1")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("OldPassword1!", "encodedPassword")).thenReturn(true);
        doNothing().when(memberService).changePassword(any(MemberModel.class), anyString(), anyString());

        // act & assert
        mockMvc.perform(patch("/api/v1/members/me/password")
                .header("X-Loopers-LoginId", "testuser1")
                .header("X-Loopers-LoginPw", "OldPassword1!")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
