package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp; // 기존에 있던 DB 정리 유틸 활용
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
        requestMap.put("loginId", "tester01");
        requestMap.put("password", "Password123!");
        requestMap.put("name", "테스터");
        requestMap.put("birthDate", "2000-01-01");
        requestMap.put("email", "test@example.com");
        requestMap.put("gender", "MALE"); // 요구사항에 있는 성별 추가

        // when & then
        mockMvc.perform(post("/api/users/register") // 실제 컨트롤러 URL
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andDo(print()) // 테스트 실행 로그 출력
                .andExpect(status().isOk()) // 200 OK 확인 (생성 시 201 Created일 수도 있음)
                .andExpect(jsonPath("$.loginId").value("tester01"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다")
    @Test
    void registerUser_fail_whenGenderIsNull() throws Exception {
        // given
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("loginId", "tester02");
        requestMap.put("password", "Password123!");
        requestMap.put("name", "테스터");
        requestMap.put("birthDate", "2000-01-01");
        requestMap.put("email", "test2@example.com");
        // gender 필드 누락

        // when & then
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andDo(print())
                .andExpect(status().isBadRequest()); // 400 에러 확인
    }
}
