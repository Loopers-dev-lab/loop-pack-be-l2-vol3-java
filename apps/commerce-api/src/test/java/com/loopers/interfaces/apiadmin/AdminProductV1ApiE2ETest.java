package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Product API V1 E2E 테스트")
class AdminProductV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BrandJpaRepository brandJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductStockJpaRepository productStockJpaRepository;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandJpaRepository.save(BrandModel.create("테스트브랜드", "설명", "서울"));
    }

    @Nested
    @DisplayName("POST /api-admin/v1/products - 상품 생성")
    class CreateProductTests {

        @Test
        @DisplayName("정상 생성 시 200 반환")
        void POST_createProduct_ShouldReturn200() throws Exception {
            var request = AdminProductV1Dto.CreateProductRequest.builder()
                    .productName("테스트상품").brandId(brand.getBrandId())
                    .price(BigDecimal.valueOf(10000)).description("설명").initialStock(100).build();

            mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productId").exists())
                    .andExpect(jsonPath("$.data.productName").value("테스트상품"));
        }

        @Test
        @DisplayName("미존재 브랜드로 404 반환")
        void POST_createProduct_NonExistingBrand_ShouldReturn404() throws Exception {
            var request = AdminProductV1Dto.CreateProductRequest.builder()
                    .productName("테스트상품").brandId(999L)
                    .price(BigDecimal.valueOf(10000)).initialStock(100).build();

            mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/products/{productId} - 상품 수정")
    class UpdateProductTests {

        @Test
        @DisplayName("수정 후 변경된 필드 확인")
        void PUT_updateProduct_ShouldReturn200() throws Exception {
            ProductModel product = productJpaRepository.save(
                    ProductModel.create("원래상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                            "설명", null, null, null, null, null, null));
            productStockJpaRepository.save(ProductStockModel.create(product.getProductId(), 100));

            var request = AdminProductV1Dto.UpdateProductRequest.builder()
                    .productName("수정상품").price(BigDecimal.valueOf(20000)).description("수정설명").build();

            mockMvc.perform(put("/api-admin/v1/products/" + product.getProductId())
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productName").value("수정상품"));
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/products/{productId} - 상품 삭제")
    class DeleteProductTests {

        @Test
        @DisplayName("softDelete 수행")
        void DELETE_deleteProduct_ShouldReturn200() throws Exception {
            ProductModel product = productJpaRepository.save(
                    ProductModel.create("삭제상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                            null, null, null, null, null, null, null));
            productStockJpaRepository.save(ProductStockModel.create(product.getProductId(), 50));

            mockMvc.perform(delete("/api-admin/v1/products/" + product.getProductId())
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/products - 상품 목록")
    class ListProductTests {

        @Test
        @DisplayName("includeDeleted=true 시 삭제 상품도 포함")
        void GET_products_WithIncludeDeleted_ShouldReturn200() throws Exception {
            ProductModel active = productJpaRepository.save(
                    ProductModel.create("활성상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                            null, null, null, null, null, null, null));
            productStockJpaRepository.save(ProductStockModel.create(active.getProductId(), 100));

            ProductModel deleted = ProductModel.create("삭제상품", brand.getBrandId(), BigDecimal.valueOf(20000),
                    null, null, null, null, null, null, null);
            deleted.softDelete();
            deleted = productJpaRepository.save(deleted);
            productStockJpaRepository.save(ProductStockModel.create(deleted.getProductId(), 50));

            mockMvc.perform(get("/api-admin/v1/products")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .param("includeDeleted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/products/{productId}/revisions - 변경 이력")
    class RevisionTests {

        @Test
        @DisplayName("변경 이력 목록 조회")
        void GET_revisions_ShouldReturn200() throws Exception {
            var createReq = AdminProductV1Dto.CreateProductRequest.builder()
                    .productName("이력상품").brandId(brand.getBrandId())
                    .price(BigDecimal.valueOf(10000)).initialStock(50).build();

            var result = mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String productId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("productId").asText();

            mockMvc.perform(get("/api-admin/v1/products/" + productId + "/revisions")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("변경 이력 상세 조회")
        void GET_revisionDetail_ShouldReturn200() throws Exception {
            var createReq = AdminProductV1Dto.CreateProductRequest.builder()
                    .productName("이력상품").brandId(brand.getBrandId())
                    .price(BigDecimal.valueOf(10000)).initialStock(50).build();

            var result = mockMvc.perform(post("/api-admin/v1/products")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String productId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("productId").asText();

            mockMvc.perform(get("/api-admin/v1/products/" + productId + "/revisions/0")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("CREATE"));
        }
    }
}
