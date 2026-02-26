package com.loopers.interfaces.api.brand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.brand.dto.BrandUpdateRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class BrandApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_BASE_URL = "/api-admin/v1/brands";
    private static final String PUBLIC_BASE_URL = "/api/v1/brands";
    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("브랜드 생성 API")
    class CreateBrandApi {

        @Test
        @DisplayName("브랜드 생성 성공")
        void createBrand_success() throws Exception {
            var request = new BrandCreateRequest("나이키", "스포츠 브랜드");

            mockMvc.perform(post(ADMIN_BASE_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("중복 이름으로 생성 시 실패")
        void createBrand_fail_duplicate() throws Exception {
            var request = new BrandCreateRequest("나이키", "스포츠 브랜드");

            mockMvc.perform(post(ADMIN_BASE_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            mockMvc.perform(post(ADMIN_BASE_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("관리자 인증 없이 생성 시 실패")
        void createBrand_fail_unauthorized() throws Exception {
            var request = new BrandCreateRequest("나이키", "스포츠 브랜드");

            mockMvc.perform(post(ADMIN_BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("브랜드 수정 API")
    class UpdateBrandApi {

        @Test
        @DisplayName("브랜드 수정 성공")
        void updateBrand_success() throws Exception {
            createBrand("나이키", "스포츠 브랜드");

            var updateRequest = new BrandUpdateRequest("아디다스", "변경된 설명");

            mockMvc.perform(put(ADMIN_BASE_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk());

            // 변경 확인
            mockMvc.perform(get(ADMIN_BASE_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("아디다스"))
                    .andExpect(jsonPath("$.description").value("변경된 설명"));
        }
    }

    @Nested
    @DisplayName("브랜드 삭제 API")
    class DeleteBrandApi {

        @Test
        @DisplayName("브랜드 삭제 성공")
        void deleteBrand_success() throws Exception {
            createBrand("나이키", "스포츠 브랜드");

            mockMvc.perform(delete(ADMIN_BASE_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk());

            // 삭제 확인
            mockMvc.perform(get(ADMIN_BASE_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("브랜드 조회 API")
    class QueryBrandApi {

        @Test
        @DisplayName("브랜드 단건 조회 성공")
        void getBrand_success() throws Exception {
            createBrand("나이키", "스포츠 브랜드");

            mockMvc.perform(get(PUBLIC_BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("나이키"))
                    .andExpect(jsonPath("$.description").value("스포츠 브랜드"));
        }

        @Test
        @DisplayName("브랜드 목록 조회 성공")
        void getBrands_success() throws Exception {
            createBrand("나이키", "스포츠 브랜드");
            createBrand("아디다스", "독일 스포츠 브랜드");

            mockMvc.perform(get(ADMIN_BASE_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 조회 시 실패")
        void getBrand_fail_notFound() throws Exception {
            mockMvc.perform(get(PUBLIC_BASE_URL + "/999"))
                    .andExpect(status().isBadRequest());
        }
    }

    private void createBrand(String name, String description) throws Exception {
        var request = new BrandCreateRequest(name, description);
        mockMvc.perform(post(ADMIN_BASE_URL)
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
