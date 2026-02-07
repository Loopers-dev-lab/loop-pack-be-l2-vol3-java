package com.loopers.interfaces.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.SignUpCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.config.WebMvcConfig;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.user.ChangePasswordRequest;
import com.loopers.interfaces.user.UsersController;
import com.loopers.interfaces.user.UsersSignUpRequestDto;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsersController.class)
@Import({WebMvcConfig.class, CredentialsHeadersArgumentResolver.class, AuthUserArgumentResolver.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserFacade userFacade;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 API 호출 테스트")
    void success_signup() throws Exception {
        UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
            LocalDate.of(1991, 12, 3),
            "김용권",
            "yk@google.com"
        );

        UserInfo userInfo = new UserInfo(1L, "kim", "김용권", "yk@google.com", LocalDate.of(1991, 12, 3));
        given(userFacade.signUp(any(SignUpCommand.class))).willReturn(userInfo);

        String json = objectMapper.writeValueAsString(requestBody);

        mockMvc.perform(post("/users")
                .contentType(APPLICATION_JSON)
                .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1!")
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @DisplayName("회원가입 API 실패 테스트")
    @Nested
    class UserSignupFailureTest {

        @Test
        @DisplayName("이메일 형식이 잘못되면 400 Bad Request를 반환한다")
        void fail_when_email_format_invalid() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_email_null() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_null() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_empty() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_too_short() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_too_long() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_contains_number() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_name_contains_special_character() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_birthDate_null() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_birthDate_future() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_password_too_short() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_password_too_long() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_password_contains_korean() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_password_contains_space() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        void fail_when_loginId_header_missing() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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
        @DisplayName("로그인 ID에 특수문자가 포함되면 400 Bad Request를 반환한다")
        void fail_when_loginId_contains_special_character() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/users")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim!")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "Password1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-Loopers-LoginPw 헤더가 없으면 400 Bad Request를 반환한다")
        void fail_when_loginPw_header_missing() throws Exception {
            UsersSignUpRequestDto requestBody = new UsersSignUpRequestDto(
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

    @DisplayName("비밀번호 변경 API")
    @Nested
    class ChangePasswordTest {

        private UserModel mockUser() {
            return UserModel.create("kim", "encodedOldPass", LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");
        }

        @Test
        @DisplayName("비밀번호 변경에 성공한다")
        void success() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "NewPass1!");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());
            doNothing().when(userFacade).changePassword(any(), any(ChangePasswordRequest.class));

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("기존 비밀번호가 null이면 400 Bad Request를 반환한다")
        void fail_when_currentPassword_is_null() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest(null, "NewPass1!");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호가 null이면 400 Bad Request를 반환한다")
        void fail_when_newPassword_is_null() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", null);
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호가 7자 이하면 400 Bad Request를 반환한다")
        void fail_when_newPassword_too_short() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "Pass1!");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호가 17자 이상이면 400 Bad Request를 반환한다")
        void fail_when_newPassword_too_long() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "Password123456789!");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호에 한글이 포함되면 400 Bad Request를 반환한다")
        void fail_when_newPassword_contains_korean() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "NewPass1가");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("새 비밀번호에 공백이 포함되면 400 Bad Request를 반환한다")
        void fail_when_newPassword_contains_space() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "New Pass1!");
            String json = objectMapper.writeValueAsString(request);

            given(userService.authenticate("kim", "OldPass1!")).willReturn(mockUser());

            mockMvc.perform(patch("/users/me/password")
                    .contentType(APPLICATION_JSON)
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_ID, "kim")
                    .header(LoopersHeaders.X_LOOPERS_LOGIN_PW, "OldPass1!")
                    .content(json))
                .andExpect(status().isBadRequest());
        }
    }
}
