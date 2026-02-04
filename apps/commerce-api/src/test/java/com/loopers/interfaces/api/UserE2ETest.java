package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc // MockMvc를 주입받기 위한 설정
class UserE2ETest {

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

    @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다")
    @Test
    void registerUser_success() throws Exception {
        // given
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("name", "테스터");
        requestMap.put("birthDate", "2000-01-01");
        requestMap.put("email", "test@example.com");
        requestMap.put("gender", "MALE");

        // when & then
        mockMvc.perform(post("/api/users/register")
                        .header("X-Loopers-LoginId", "tester01")
                        .header("X-Loopers-LoginPw", "Password123!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.loginId").value("tester01"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.gender").value("MALE"));
    }

    @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다")
    @Test
    void registerUser_fail_whenGenderIsNull() throws Exception {
        // given
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("name", "테스터");
        requestMap.put("birthDate", "2000-01-01");
        requestMap.put("email", "test2@example.com");
        // gender 필드 누락

        // when & then
        mockMvc.perform(post("/api/users/register")
                        .header("X-Loopers-LoginId", "tester02")
                        .header("X-Loopers-LoginPw", "Password123!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andDo(print())
                .andExpect(status().isBadRequest()); // 400 에러 확인
    }

    @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다")
    @Test
    void getUserInfo_success() throws Exception {
        // given - 먼저 회원가입
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("name", "정보조회");
        registerRequest.put("birthDate", "2000-01-01");
        registerRequest.put("email", "info@example.com");
        registerRequest.put("gender", "MALE");

        String registerResponse = mockMvc.perform(post("/api/users/register")
                        .header("X-Loopers-LoginId", "infotest")
                        .header("X-Loopers-LoginPw", "Password123!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 응답에서 userId 추출
        Long userId = objectMapper.readTree(registerResponse).get("data").get("id").asLong();

        // when & then - 내 정보 조회
        mockMvc.perform(get("/api/users/" + userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.loginId").value("infotest"))
                .andExpect(jsonPath("$.data.name").value("정보조회"))
                .andExpect(jsonPath("$.data.email").value("info@example.com"))
                .andExpect(jsonPath("$.data.gender").value("MALE"));
    }

    @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다")
    @Test
    void getUserInfo_notFound() throws Exception {
        // when & then - 존재하지 않는 ID로 조회
        mockMvc.perform(get("/api/users/999999"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}
