package com.loopers.interfaces.api.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원 API V1 E2E 테스트
 *
 * E2E (End-to-End) 테스트:
 * - HTTP 요청부터 응답까지 전체 흐름 테스트
 * - 실제 Spring 컨텍스트 로드
 * - 모든 레이어 통합 검증
 *
 * @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * @AutoConfigureMockMvc: MockMvc 자동 설정
 * @Transactional: 각 테스트 후 롤백
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("회원 API V1 E2E 테스트")
class MemberV1ApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        // 각 테스트 전 데이터 정리
        // @Transactional로 자동 롤백되지만, 명시적 정리도 가능
    }

    // ========================================
    // 1. 회원가입 API 테스트
    // ========================================

    @Test
    @DisplayName("POST /api/v1/members - 유효한 입력으로 회원가입 성공")
    void register_WithValidInput_ShouldReturn200() throws Exception {
        // Given: 유효한 회원가입 요청
        MemberV1Dto.RegisterRequest request = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        // When & Then: POST 요청 및 검증
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("testuser123"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/members - 로그인 ID 중복 시 409 반환")
    void register_WithDuplicateLoginId_ShouldReturn409() throws Exception {
        // Given: 이미 가입된 회원
        MemberV1Dto.RegisterRequest firstRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("duplicate123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test1@example.com")
                .build();

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)));

        // When: 동일한 로그인 ID로 재가입 시도
        MemberV1Dto.RegisterRequest duplicateRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("duplicate123")
                .loginPw("Test1234!@#")
                .name("김철수")
                .birthDate(LocalDate.of(1991, 2, 2))
                .email("test2@example.com")
                .build();

        // Then: 409 Conflict
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("중복")));
    }

    @Test
    @DisplayName("POST /api/v1/members - 비밀번호 8자 미만 시 400 반환")
    void register_WithShortPassword_ShouldReturn400() throws Exception {
        // Given: 짧은 비밀번호
        MemberV1Dto.RegisterRequest request = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Short1!")  // 7자
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        // When & Then: 400 Bad Request
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("8~16자")));
    }

    @Test
    @DisplayName("POST /api/v1/members - 필수 필드 누락 시 400 반환")
    void register_WithMissingFields_ShouldReturn400() throws Exception {
        // Given: 이름 누락
        MemberV1Dto.RegisterRequest request = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                // name 누락
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        // When & Then: 400 Bad Request
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========================================
    // 2. 내 정보 조회 API 테스트
    // ========================================

    @Test
    @DisplayName("GET /api/v1/members/me - 인증 성공 시 마스킹된 정보 반환")
    void getMyInfo_WithValidAuth_ShouldReturn200() throws Exception {
        // Given: 회원 가입
        MemberV1Dto.RegisterRequest registerRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then: 내 정보 조회
        mockMvc.perform(get("/api/v1/members/me")
                        .header("X-Loopers-LoginId", "testuser123")
                        .header("X-Loopers-LoginPw", "Test1234!@#"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("testuser123"))
                .andExpect(jsonPath("$.name").value("홍길*"))  // 마스킹됨
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/members/me - 인증 헤더 누락 시 401 반환")
    void getMyInfo_WithoutAuthHeader_ShouldReturn401() throws Exception {
        // When & Then: 헤더 없이 요청
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/members/me - 잘못된 비밀번호로 401 반환")
    void getMyInfo_WithWrongPassword_ShouldReturn401() throws Exception {
        // Given: 회원 가입
        MemberV1Dto.RegisterRequest registerRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then: 잘못된 비밀번호로 조회
        mockMvc.perform(get("/api/v1/members/me")
                        .header("X-Loopers-LoginId", "testuser123")
                        .header("X-Loopers-LoginPw", "WrongPass123!"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================
    // 3. 비밀번호 변경 API 테스트
    // ========================================

    @Test
    @DisplayName("PATCH /api/v1/members/me/password - 유효한 입력으로 변경 성공")
    void changePassword_WithValidInput_ShouldReturn200() throws Exception {
        // Given: 회원 가입
        MemberV1Dto.RegisterRequest registerRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When: 비밀번호 변경
        MemberV1Dto.ChangePasswordRequest changeRequest = MemberV1Dto.ChangePasswordRequest.builder()
                .currentPassword("Test1234!@#")
                .newPassword("NewPass5678$")
                .build();

        // Then: 200 OK
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header("X-Loopers-LoginId", "testuser123")
                        .header("X-Loopers-LoginPw", "Test1234!@#")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk());

        // 검증: 새 비밀번호로 로그인 가능
        mockMvc.perform(get("/api/v1/members/me")
                        .header("X-Loopers-LoginId", "testuser123")
                        .header("X-Loopers-LoginPw", "NewPass5678$"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/members/me/password - 인증 헤더 누락 시 401 반환")
    void changePassword_WithoutAuthHeader_ShouldReturn401() throws Exception {
        // When & Then: 헤더 없이 요청
        MemberV1Dto.ChangePasswordRequest request = MemberV1Dto.ChangePasswordRequest.builder()
                .currentPassword("Test1234!@#")
                .newPassword("NewPass5678$")
                .build();

        mockMvc.perform(patch("/api/v1/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/members/me/password - 새 비밀번호가 기존과 동일 시 400 반환")
    void changePassword_WithSamePassword_ShouldReturn400() throws Exception {
        // Given: 회원 가입
        MemberV1Dto.RegisterRequest registerRequest = MemberV1Dto.RegisterRequest.builder()
                .loginId("testuser123")
                .loginPw("Test1234!@#")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("test@example.com")
                .build();

        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When: 동일한 비밀번호로 변경 시도
        MemberV1Dto.ChangePasswordRequest request = MemberV1Dto.ChangePasswordRequest.builder()
                .currentPassword("Test1234!@#")
                .newPassword("Test1234!@#")  // 동일
                .build();

        // Then: 400 Bad Request
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header("X-Loopers-LoginId", "testuser123")
                        .header("X-Loopers-LoginPw", "Test1234!@#")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("달라야")));
    }
}
