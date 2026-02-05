package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @Test
        void 성공하면_200_OK() throws Exception {
            // given
            UserDto.SignupRequest request = new UserDto.SignupRequest(
                    "looper123",
                    "password123",
                    "루퍼스",
                    LocalDate.of(1996, 11, 22),
                    "test@loopers.im"
            );

            when(userService.signup(request.loginId(), request.password(), request.name(), request.birthDate(), request.email()))
                    .thenReturn(LoginId.from("looper123"));

            String content = objectMapper.writeValueAsString(request);

            // when-then
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginId").value("looper123"));
       }
    }

}
