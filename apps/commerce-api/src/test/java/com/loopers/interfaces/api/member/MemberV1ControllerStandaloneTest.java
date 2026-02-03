package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.domain.member.FakeMemberRepository;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiControllerAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberV1ControllerStandaloneTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FakeMemberRepository fakeRepository = new FakeMemberRepository();
        MemberService memberService = new MemberService(fakeRepository);
        MemberFacade memberFacade = new MemberFacade(memberService, new BCryptPasswordEncoder());
        MemberV1Controller controller = new MemberV1Controller(memberFacade);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiControllerAdvice())
            .build();
    }

    private void registerMember() throws Exception {
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "loginId": "testuser",
                        "password": "password1!",
                        "name": "홍길동",
                        "birthday": "19900101",
                        "email": "test@example.com"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @DisplayName("회원가입")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 성공하고 회원 정보를 반환한다.")
        @Test
        void returns200_withValidInfo() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "loginId": "testuser",
                            "password": "password1!",
                            "name": "홍길동",
                            "birthday": "19900101",
                            "email": "test@example.com"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("testuser"))
                .andExpect(jsonPath("$.data.name").value("홍길동"));

            // 암호화 확인: 가입한 비밀번호로 인증 가능
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isOk());
        }

        @DisplayName("중복된 loginId로 가입하면, 409를 반환한다.")
        @Test
        void returns409_whenDuplicateLoginId() throws Exception {
            // Arrange
            registerMember();

            // Act & Assert
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "loginId": "testuser",
                            "password": "password2!",
                            "name": "김철수",
                            "birthday": "19950505",
                            "email": "new@example.com"
                        }
                        """))
                .andExpect(status().isConflict());
        }

        @DisplayName("비밀번호에 생일이 포함되면, 400을 반환한다.")
        @Test
        void returns400_whenPasswordContainsBirthday() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "loginId": "testuser",
                            "password": "pw19900101!",
                            "name": "홍길동",
                            "birthday": "19900101",
                            "email": "test@example.com"
                        }
                        """))
                .andExpect(status().isBadRequest());
        }
    }

    @DisplayName("내정보 조회 - 헤더 인증")
    @Nested
    class GetMyInfo {

        @DisplayName("올바른 헤더로 요청하면, 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenHeadersValid() throws Exception {
            // Arrange
            registerMember();

            // Act & Assert
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("testuser"))
                .andExpect(jsonPath("$.data.name").value("홍길*"))
                .andExpect(jsonPath("$.data.birthday").value("19900101"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }

        @DisplayName("비밀번호 헤더가 틀리면, 400을 반환한다.")
        @Test
        void returns400_whenPasswordHeaderWrong() throws Exception {
            // Arrange
            registerMember();

            // Act & Assert
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "wrongpass1!"))
                .andExpect(status().isBadRequest());
        }

        @DisplayName("존재하지 않는 회원 헤더로 요청하면, 404를 반환한다.")
        @Test
        void returns404_whenMemberNotFound() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "nouser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isNotFound());
        }
    }

    @DisplayName("비밀번호 변경 - 헤더 인증")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 헤더와 유효한 새 비밀번호로 요청하면, 변경에 성공한다.")
        @Test
        void returns200_whenHeadersValidAndNewPasswordValid() throws Exception {
            // Arrange
            registerMember();

            // Act
            mockMvc.perform(patch("/api/v1/members/me/password")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"newPassword": "newpass12!!"}
                        """))
                .andExpect(status().isOk());

            // Assert — 새 비밀번호로 인증 가능
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "newpass12!!"))
                .andExpect(status().isOk());

            // Assert — 기존 비밀번호로 인증 불가
            mockMvc.perform(get("/api/v1/members/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isBadRequest());
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 400을 반환한다.")
        @Test
        void returns400_whenNewPasswordSameAsCurrent() throws Exception {
            // Arrange
            registerMember();

            // Act & Assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"newPassword": "password1!"}
                        """))
                .andExpect(status().isBadRequest());
        }

        @DisplayName("현재 비밀번호 헤더가 틀리면, 400을 반환한다.")
        @Test
        void returns400_whenCurrentPasswordHeaderWrong() throws Exception {
            // Arrange
            registerMember();

            // Act & Assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "wrongpass1!")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"newPassword": "newpass12!!"}
                        """))
                .andExpect(status().isBadRequest());
        }
    }
}
