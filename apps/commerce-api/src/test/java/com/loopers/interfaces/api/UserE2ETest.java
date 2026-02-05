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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                .andExpect(jsonPath("$.data.name").value("정보조*"))
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

    @DisplayName("비밀번호 변경이 성공할 경우, 200 OK 응답을 반환한다")
    @Test
    void changePassword_success() throws Exception {
        // given - 먼저 회원가입
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("name", "비번변경");
        registerRequest.put("birthDate", "1997-10-08");
        registerRequest.put("email", "change@example.com");
        registerRequest.put("gender", "FEMALE");

        String registerResponse = mockMvc.perform(post("/api/users/register")
                        .header("X-Loopers-LoginId", "changetest")
                        .header("X-Loopers-LoginPw", "password1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long userId = objectMapper.readTree(registerResponse).get("data").get("id").asLong();

        // when & then - 비밀번호 변경 (1997년생 -> 20대만 가능)
        Map<String, String> changePasswordRequest = new HashMap<>();
        changePasswordRequest.put("oldPassword", "password1");
        changePasswordRequest.put("newPassword", "newpass2!");

        mockMvc.perform(patch("/api/users/" + userId + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
    }

    @DisplayName("기존 비밀번호가 틀릴 경우, 400 Bad Request 응답을 반환한다")
    @Test
    void changePassword_fail_whenWrongOldPassword() throws Exception {
        // given - 먼저 회원가입
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("name", "비번실패");
        registerRequest.put("birthDate", "1997-10-08");
        registerRequest.put("email", "fail@example.com");
        registerRequest.put("gender", "MALE");

        String registerResponse = mockMvc.perform(post("/api/users/register")
                        .header("X-Loopers-LoginId", "failtest")
                        .header("X-Loopers-LoginPw", "password1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long userId = objectMapper.readTree(registerResponse).get("data").get("id").asLong();

        // when & then - 잘못된 기존 비밀번호로 변경 시도
        Map<String, String> changePasswordRequest = new HashMap<>();
        changePasswordRequest.put("oldPassword", "wrongPassword");
        changePasswordRequest.put("newPassword", "newpass2!");

        mockMvc.perform(patch("/api/users/" + userId + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("존재하지 않는 사용자의 비밀번호 변경 시도 시, 404 Not Found 응답을 반환한다")
    @Test
    void changePassword_fail_whenUserNotFound() throws Exception {
        // given
        Map<String, String> changePasswordRequest = new HashMap<>();
        changePasswordRequest.put("oldPassword", "oldpass1");
        changePasswordRequest.put("newPassword", "newpass2!");

        // when & then
        mockMvc.perform(patch("/api/users/999999/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}
