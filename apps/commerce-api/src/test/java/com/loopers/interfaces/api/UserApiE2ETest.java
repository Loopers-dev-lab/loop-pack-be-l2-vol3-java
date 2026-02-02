package com.loopers.interfaces.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsersController.class)
class UserApiE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 API 호출 테스트")
    void userSignupApiTest() throws Exception {
        UserSignUpRequestDto requestBody = new UserSignUpRequestDto(
            "kim",
            "pw111",
            LocalDate.of(1991, 12, 3),
            "김용권",
            "yk@google.com"
        );

        String json = objectMapper.writeValueAsString(requestBody);

        mockMvc.perform(post("/users")
                .contentType(APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());
    }
}
