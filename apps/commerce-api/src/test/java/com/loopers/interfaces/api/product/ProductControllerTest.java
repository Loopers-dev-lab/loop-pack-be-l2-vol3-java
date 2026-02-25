package com.loopers.interfaces.api.product;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {

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
    @DisplayName("POST /api/v1/products")
    class Create {

        @Test
        @DisplayName("유효한 요청이면 201 Created를 반환한다")
        void createSuccess() throws Exception {
            ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                    "강아지 간식",
                    3500,
                    100,
                    "오리 고구마",
                    1L,
                    10L
            );

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.name").value("강아지 간식"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetDetail {

        @Test
        @DisplayName("존재하는 상품이면 200과 상품 정보를 반환한다")
        void getDetailSuccess() throws Exception {
            Long productId = createProduct("사료A", 12000, 30, "설명", 1L, 10L);

            mockMvc.perform(get("/api/v1/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(productId))
                    .andExpect(jsonPath("$.data.name").value("사료A"));
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 404를 반환한다")
        void getDetailNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", 99999L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetList {

        @Test
        @DisplayName("브랜드 필터로 목록을 조회한다")
        void listByBrandFilter() throws Exception {
            createProduct("사료A", 10000, 10, "설명", 1L, 10L);
            createProduct("사료B", 11000, 10, "설명", 1L, 20L);

            mockMvc.perform(get("/api/v1/products")
                            .param("brandId", "10")
                            .param("sort", "latest")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.items[0].brandId").value(10));
        }
    }

    private Long createProduct(String name, int price, int stock, String description, Long categoryId, Long brandId) throws Exception {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                stock,
                description,
                categoryId,
                brandId
        );

        String body = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).path("data").path("id").asLong();
    }
}
