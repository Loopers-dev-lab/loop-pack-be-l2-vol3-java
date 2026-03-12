package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
            UUID categoryId = createCategory("푸드");
            UUID brandId = createBrand("퍼피박스");

            ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                    "강아지 간식",
                    3500,
                    100,
                    "오리 고구마",
                    categoryId,
                    brandId
            );

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").isString())
                    .andExpect(jsonPath("$.data.name").value("강아지 간식"));
        }

        @Test
        @DisplayName("카테고리 ID가 없으면 400을 반환한다")
        void createFailWhenCategoryIdMissing() throws Exception {
            UUID brandId = createBrand("퍼피박스");

            ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                    "강아지 장난감",
                    3000,
                    50,
                    "오리",
                    null,
                    brandId
            );

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("브랜드 ID가 없으면 400을 반환한다")
        void createFailWhenBrandIdMissing() throws Exception {
            UUID categoryId = createCategory("푸드");

            ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                    "강아지 목줄",
                    2500,
                    20,
                    "편안한 소재",
                    categoryId,
                    null
            );

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetDetail {

        @Test
        @DisplayName("존재하는 상품이면 200과 상품 정보를 반환한다")
        void getDetailSuccess() throws Exception {
            UUID categoryId = createCategory("푸드");
            UUID brandId = createBrand("퍼피박스");
            UUID productId = createProduct("사료A", 12000, 30, "설명", categoryId, brandId);

            mockMvc.perform(get("/api/v1/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(productId.toString()))
                    .andExpect(jsonPath("$.data.name").value("사료A"));
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 404를 반환한다")
        void getDetailNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetList {

        @Test
        @DisplayName("브랜드 필터로 목록을 조회한다")
        void listByBrandFilter() throws Exception {
            UUID categoryId = createCategory("푸드");
            UUID brandIdForList = createBrand("퍼피박스");
            UUID otherBrandId = createBrand("포메피아");

            createProduct("사료A", 10000, 10, "설명", categoryId, brandIdForList);
            createProduct("사료B", 11000, 10, "설명", categoryId, otherBrandId);

            mockMvc.perform(get("/api/v1/products")
                            .param("brandId", brandIdForList.toString())
                            .param("sort", "latest")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.items[0].brandId").value(brandIdForList.toString()))
                    .andExpect(jsonPath("$.data.items[0].categoryId").value(categoryId.toString()));
        }
    }

    private UUID createProduct(String name, int price, int stock, String description, UUID categoryId, UUID brandId) throws Exception {
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

        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private UUID createBrand(String name) {
        return brandRepository.save(new Brand(new BrandName(name), "", "")).id();
    }
}
