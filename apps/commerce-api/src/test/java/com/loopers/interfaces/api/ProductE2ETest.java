package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
@AutoConfigureMockMvc
class ProductE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("ADIDAS"), new BrandDescription("아디다스"))
        );
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/products → GET /api/v1/products: 상품 생성 후 목록/상세 조회에 성공한다")
    @Test
    void product_create_and_get_success() throws Exception {
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("brandId", brandId);
        createBody.put("name", "슈퍼스타");
        createBody.put("description", "오리지널 라인");
        createBody.put("price", 129000);
        createBody.put("stock", 20);

        String created = mockMvc.perform(post("/api-admin/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBody)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.name").value("슈퍼스타"))
            .andExpect(jsonPath("$.data.brandId").value(brandId))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long productId = objectMapper.readTree(created).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/products")
                .param("brandId", String.valueOf(brandId))
                .param("sort", "likes_desc")
                .param("page", "0")
                .param("size", "20"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].id").value(productId));

        mockMvc.perform(get("/api/v1/products/" + productId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(productId))
            .andExpect(jsonPath("$.data.brandName").value("ADIDAS"));
    }
}
