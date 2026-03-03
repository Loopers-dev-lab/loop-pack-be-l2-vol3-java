package com.loopers.interfaces.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.interfaces.api.brand.BrandDto;
import com.loopers.interfaces.api.product.ProductDto;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class AdminApiControllerTest {

    private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

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
    @DisplayName("Admin LDAP 인증")
    class AdminLdap {

        @Test
        @DisplayName("LDAP 헤더 없이 어드민 엔드포인트 호출 시 401")
        void returnsUnauthorizedWithoutLdapHeader() throws Exception {
            mockMvc.perform(get("/api-admin/v1/brands"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 LDAP 값으로 호출 시 403")
        void returnsForbiddenWithInvalidLdapHeader() throws Exception {
            mockMvc.perform(get("/api-admin/v1/brands")
                            .header(ADMIN_LDAP_HEADER, "wrong.admin"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Admin Brand API")
    class AdminBrandApi {

        @Test
        @DisplayName("브랜드 목록 조회 성공")
        void listBrandsSuccess() throws Exception {
            brandRepository.save(new Brand(new BrandName("브랜드A"), "desc", "img"));
            brandRepository.save(new Brand(new BrandName("브랜드B"), "desc", "img"));

            mockMvc.perform(get("/api-admin/v1/brands")
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("브랜드 생성 후 상세 조회 성공")
        void createAndGetBrandSuccess() throws Exception {
            BrandDto.CreateBrandRequest request = BrandDto.CreateBrandRequest.builder()
                    .name("브랜드C")
                    .description("설명")
                    .imageUrl("https://example.com/image.png")
                    .build();

            String body = mockMvc.perform(post("/api-admin/v1/brands")
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode json = objectMapper.readTree(body);
            UUID brandId = UUID.fromString(json.path("data").path("id").asText());

            mockMvc.perform(get("/api-admin/v1/brands/{brandId}", brandId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(brandId.toString()));
        }
    }

    @Nested
    @DisplayName("Admin Product API")
    class AdminProductApi {

        @Test
        @DisplayName("상품 생성/목록/상세/수정/삭제 성공")
        void productCrudSuccess() throws Exception {
            UUID categoryId = categoryRepository.save(new Category("카테고리A")).id();
            UUID brandId = brandRepository.save(new Brand(new BrandName("브랜드A"), "desc", "img")).id();

            ProductDto.CreateProductRequest createRequest = new ProductDto.CreateProductRequest(
                    "상품A",
                    10000,
                    30,
                    "설명",
                    categoryId,
                    brandId
            );

            String createBody = mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            UUID productId = UUID.fromString(objectMapper.readTree(createBody).path("data").path("id").asText());

            mockMvc.perform(get("/api-admin/v1/products")
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));

            mockMvc.perform(get("/api-admin/v1/products/{productId}", productId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(productId.toString()));

            ProductDto.UpdateProductRequest updateRequest = new ProductDto.UpdateProductRequest(
                    "상품A-수정",
                    12000,
                    25,
                    "설명-수정",
                    categoryId,
                    null
            );

            mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.name").value("상품A-수정"));

            mockMvc.perform(delete("/api-admin/v1/products/{productId}", productId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

            mockMvc.perform(get("/api-admin/v1/products/{productId}", productId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.deletedAt").isNotEmpty());
        }

        @Test
        @DisplayName("상품 수정 시 brandId를 보내면 400")
        void updateProductFailsWhenBrandIdProvided() throws Exception {
            UUID categoryId = categoryRepository.save(new Category("카테고리A")).id();
            UUID brandId = brandRepository.save(new Brand(new BrandName("브랜드A"), "desc", "img")).id();

            ProductDto.CreateProductRequest createRequest = new ProductDto.CreateProductRequest(
                    "상품A",
                    10000,
                    30,
                    "설명",
                    categoryId,
                    brandId
            );

            String createBody = mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            UUID productId = UUID.fromString(objectMapper.readTree(createBody).path("data").path("id").asText());

            ProductDto.UpdateProductRequest updateRequest = new ProductDto.UpdateProductRequest(
                    "상품A-수정",
                    12000,
                    25,
                    "설명-수정",
                    categoryId,
                    brandId
            );

            mockMvc.perform(put("/api-admin/v1/products/{productId}", productId)
                            .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isBadRequest());
        }
    }
}
