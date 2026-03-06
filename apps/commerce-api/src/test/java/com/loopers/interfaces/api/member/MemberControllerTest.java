package com.loopers.interfaces.api.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/members - 회원가입")
    class RegisterTest {

        @Test
        @DisplayName("유효한 요청이면 201 Created를 반환한다")
        void returnsCreated_whenValidRequest() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("로그인 ID가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenLoginIdIsBlank() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("로그인 ID가 4자 미만이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenLoginIdTooShort() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "abc",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("로그인 ID에 특수문자가 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenLoginIdHasSpecialChars() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "test@member",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호가 8자 미만이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenPasswordTooShort() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Pass1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("생년월일 형식이 잘못되면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenBirthDateInvalidFormat() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "1990-01-01",
                    "test@example.com",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일 형식이 잘못되면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenEmailInvalidFormat() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "invalid-email",
                    "010-1234-5678"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("전화번호 형식이 잘못되면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenPhoneInvalidFormat() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-123-4567"
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("전화번호가 누락되면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenPhoneIsBlank() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    ""
            );

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미 존재하는 아이디로 가입하면 409 Conflict를 반환한다")
        void returnsConflict_whenDuplicateMemberId() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "testmember1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // act & assert
            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/duplicate - 로그인 ID 중복 검사")
    class DuplicateCheckTest {

        @Test
        @DisplayName("사용 가능한 아이디면 available=true를 반환한다")
        void returnsAvailable_whenLoginIdIsAvailable() throws Exception {
            // act & assert
            mockMvc.perform(get("/api/v1/members/duplicate")
                            .param("loginId", "newmember123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.available").value(true))
                    .andExpect(jsonPath("$.data.loginId").value("newmember123"));
        }

        @Test
        @DisplayName("이미 사용 중인 아이디면 available=false를 반환한다")
        void returnsUnavailable_whenLoginIdIsAlreadyUsed() throws Exception {
            // arrange
            MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(
                    "existingmember",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com",
                    "010-1234-5678"
            );
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // act & assert
            mockMvc.perform(get("/api/v1/members/duplicate")
                            .param("loginId", "existingmember"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.available").value(false))
                    .andExpect(jsonPath("$.data.loginId").value("existingmember"));
        }

        @Test
        @DisplayName("loginId 파라미터가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenLoginIdParamIsMissing() throws Exception {
            // act & assert
            mockMvc.perform(get("/api/v1/members/duplicate"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/me - 내 정보 조회")
    class GetMeTest {

        @Test
        @DisplayName("인증된 사용자면 200 OK와 사용자 정보를 반환한다")
        void returnsOk_whenAuthenticated() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            // act & assert
            mockMvc.perform(get("/api/v1/members/me")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.loginId").value("testmember1"))
                    .andExpect(jsonPath("$.data.name").value("홍길*"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"));
        }

        @Test
        @DisplayName("인증 정보가 없으면 401 Unauthorized를 반환한다")
        void returnsUnauthorized_whenNoCredentials() throws Exception {
            // act & assert
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 비밀번호로 조회하면 401 Unauthorized를 반환한다")
        void returnsUnauthorized_whenWrongPassword() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            // act & assert
            mockMvc.perform(get("/api/v1/members/me")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "WrongPass1!"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/members/me/password - 비밀번호 변경")
    class ChangePasswordTest {

        @Test
        @DisplayName("유효한 요청이면 200 OK를 반환한다")
        void returnsOk_whenValidRequest() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            MemberDto.ChangePasswordRequest request = new MemberDto.ChangePasswordRequest(
                    "NewPassword1!"
            );

            // act & assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "Password1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("새 비밀번호가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenNewPasswordIsBlank() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            MemberDto.ChangePasswordRequest request = new MemberDto.ChangePasswordRequest(
                    ""
            );

            // act & assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "Password1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호가 8자 미만이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenNewPasswordTooShort() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            MemberDto.ChangePasswordRequest request = new MemberDto.ChangePasswordRequest(
                    "Short1!"
            );

            // act & assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "Password1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenSamePassword() throws Exception {
            // arrange
            registerMember("testmember1", "Password1!", "홍길동", "19900101", "test@example.com", "010-1234-5678");

            MemberDto.ChangePasswordRequest request = new MemberDto.ChangePasswordRequest(
                    "Password1!"
            );

            // act & assert
            mockMvc.perform(patch("/api/v1/members/me/password")
                            .header("X-Loopers-LoginId", "testmember1")
                            .header("X-Loopers-LoginPw", "Password1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    private void registerMember(String loginId, String password, String name, String birthDate, String email, String phone) throws Exception {
        MemberDto.RegisterRequest request = new MemberDto.RegisterRequest(loginId, password, name, birthDate, email, phone);
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
