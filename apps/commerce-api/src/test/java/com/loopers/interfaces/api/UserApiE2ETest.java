package com.loopers.interfaces.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsersController.class)
class UserApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 API 호출 테스트")
    void userSignupApiTest() throws Exception {
        UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
            LocalDate.of(1991, 12, 3),
            "김용권",
            "yk@google.com"
        );

        String json = objectMapper.writeValueAsString(requestBody);

        mockMvc.perform(post("/users")
                .contentType(APPLICATION_JSON)
                .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                .content(json))
            .andExpect(status().isOk());
    }

    @DisplayName("회원가입 API 실패 테스트")
    @Nested
    class UserSignupFailureTest {

        @Test
        @DisplayName("이메일 형식이 잘못되면 400 Bad Request를 반환한다")
        void userSignupApiEmailInvalidTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "invalid-email"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일이 null이면 400 Bad Request를 반환한다")
        void userSignupApiEmailNullTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                null
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름이 null이면 400 Bad Request를 반환한다")
        void userSignupApiNameNullTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                null,
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름이 빈 문자열이면 400 Bad Request를 반환한다")
        void userSignupApiNameEmptyTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름이 1자이면 400 Bad Request를 반환한다")
        void userSignupApiNameTooShortTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름이 11자 이상이면 400 Bad Request를 반환한다")
        void userSignupApiNameTooLongTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "가나다라마바사아자차카",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름에 숫자가 포함되면 400 Bad Request를 반환한다")
        void userSignupApiNameContainsNumberTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권1",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름에 특수문자가 포함되면 400 Bad Request를 반환한다")
        void userSignupApiNameContainsSpecialCharacterTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권!",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("생년월일이 null이면 400 Bad Request를 반환한다")
        void userSignupApiBirthDateNullTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                null,
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("생년월일이 미래 날짜이면 400 Bad Request를 반환한다")
        void userSignupApiBirthDateFutureTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.now().plusDays(1),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호가 7자 이하면 400 Bad Request를 반환한다")
        void userSignupApiPwdTooShortTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Abc12!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호가 17자 이상이면 400 Bad Request를 반환한다")
        void userSignupApiPwdTooLongTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Abcd123!@#efgh456")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호에 한글이 포함되면 400 Bad Request를 반환한다")
        void userSignupApiPwdContainsKoreanTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1가")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호에 공백이 포함되면 400 Bad Request를 반환한다")
        void userSignupApiPwdContainsSpaceTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password 1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-Loopers-LoginId 헤더가 없으면 400 Bad Request를 반환한다")
        void userSignupApiMissingLoginIdHeaderTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );
            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-Loopers-LoginPw 헤더가 없으면 400 Bad Request를 반환한다")
        void userSignupApiMissingLoginPwHeaderTest() throws Exception {
            UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );
            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 JSON 형식이면 400 Bad Request를 반환한다")
        void userSignupApiJsonInvalidTest() throws Exception {
            String invalidJson = "{ invalid json }";

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1")
                    .content(invalidJson))
                .andExpect(status().isBadRequest());
        }
    }
}
