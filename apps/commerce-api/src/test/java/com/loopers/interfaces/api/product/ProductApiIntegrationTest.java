package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductUpdateRequest;
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
class ProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_URL = "/api-admin/v1/products";
    private static final String PUBLIC_URL = "/api/v1/products";
    private static final String BRAND_ADMIN_URL = "/api-admin/v1/brands";
    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("상품 생성 API")
    class CreateProductApi {

        @Test
        @DisplayName("상품 생성 성공")
        void createProduct_success() throws Exception {
            createBrand("나이키", "스포츠");

            var request = new ProductCreateRequest(1L, "운동화", 50000, null, 100, "좋은 운동화");

            mockMvc.perform(post(ADMIN_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 상품 생성시 실패")
        void createProduct_fail_brandNotFound() throws Exception {
            var request = new ProductCreateRequest(999L, "운동화", 50000, null, 100, "좋은 운동화");

            mockMvc.perform(post(ADMIN_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("상품 수정 API")
    class UpdateProductApi {

        @Test
        @DisplayName("상품 수정 성공")
        void updateProduct_success() throws Exception {
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);

            var updateRequest = new ProductUpdateRequest("슬리퍼", 30000, null, 200, "변경된 설명");

            mockMvc.perform(put(ADMIN_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk());

            // 변경 확인
            mockMvc.perform(get(PUBLIC_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("슬리퍼"))
                    .andExpect(jsonPath("$.price").value(30000));
        }
    }

    @Nested
    @DisplayName("상품 삭제 API")
    class DeleteProductApi {

        @Test
        @DisplayName("상품 삭제 성공")
        void deleteProduct_success() throws Exception {
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);

            mockMvc.perform(delete(ADMIN_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk());

            // 삭제 확인
            mockMvc.perform(get(PUBLIC_URL + "/1"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("상품 조회 API")
    class QueryProductApi {

        @Test
        @DisplayName("상품 상세 조회 성공")
        void getProduct_success() throws Exception {
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);

            mockMvc.perform(get(PUBLIC_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("운동화"))
                    .andExpect(jsonPath("$.price").value(50000))
                    .andExpect(jsonPath("$.brandName").value("나이키"));
        }

        @Test
        @DisplayName("상품 목록 조회 성공 (페이징)")
        void getProducts_success() throws Exception {
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);
            createProduct(1L, "슬리퍼", 30000, 200);

            mockMvc.perform(get(PUBLIC_URL)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        @DisplayName("브랜드 필터링 조회")
        void getProducts_withBrandFilter() throws Exception {
            createBrand("나이키", "스포츠");
            createBrand("아디다스", "독일");
            createProduct(1L, "나이키 운동화", 50000, 100);
            createProduct(2L, "아디다스 운동화", 60000, 50);

            mockMvc.perform(get(PUBLIC_URL)
                            .param("brandId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("나이키"));
        }

        @Test
        @DisplayName("관리자 상품 목록 조회")
        void getProducts_admin() throws Exception {
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);

            mockMvc.perform(get(ADMIN_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    private void createBrand(String name, String description) throws Exception {
        var request = new BrandCreateRequest(name, description);
        mockMvc.perform(post(BRAND_ADMIN_URL)
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createProduct(Long brandId, String name, int price, int stock) throws Exception {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        mockMvc.perform(post(ADMIN_URL)
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
