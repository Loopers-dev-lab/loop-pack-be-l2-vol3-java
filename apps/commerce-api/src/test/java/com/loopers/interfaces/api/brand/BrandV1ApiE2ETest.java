package com.loopers.interfaces.api.brand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Brand API V1 E2E 테스트")
class BrandV1ApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BrandJpaRepository brandJpaRepository;

    @Nested
    @DisplayName("GET /api/v1/brands - 브랜드 목록 조회")
    class ListBrandsTests {

        @Test
        @DisplayName("ACTIVE 브랜드 2개 생성 시 목록에 2개 반환")
        void GET_brands_ShouldReturn200WithList() throws Exception {
            brandJpaRepository.save(BrandModel.create("브랜드A", "설명A", "주소A"));
            brandJpaRepository.save(BrandModel.create("브랜드B", "설명B", "주소B"));

            mockMvc.perform(get("/api/v1/brands"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("키워드 검색으로 필터링된 결과 반환")
        void GET_brands_WithKeyword_ShouldReturnFilteredResults() throws Exception {
            brandJpaRepository.save(BrandModel.create("테스트A", "설명A", "주소A"));
            brandJpaRepository.save(BrandModel.create("테스트B", "설명B", "주소B"));
            brandJpaRepository.save(BrandModel.create("다른브랜드", "설명C", "주소C"));

            mockMvc.perform(get("/api/v1/brands").param("q", "테스트"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/brands/{brandId} - 브랜드 상세 조회")
    class GetBrandDetailTests {

        @Test
        @DisplayName("존재하는 브랜드 ID로 200 반환")
        void GET_brandDetail_ShouldReturn200() throws Exception {
            BrandModel brand = brandJpaRepository.save(BrandModel.create("테스트브랜드", "설명", "주소"));

            mockMvc.perform(get("/api/v1/brands/{brandId}", brand.getBrandId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.brandName").value("테스트브랜드"));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 ID로 404 반환")
        void GET_brandDetail_NotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(get("/api/v1/brands/{brandId}", "nonexistent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("HIDDEN 브랜드 조회 시 404 반환")
        void GET_brandDetail_HiddenBrand_ShouldReturn404() throws Exception {
            BrandModel brand = BrandModel.create("숨김브랜드", "설명", "주소");
            brand.hide();
            brandJpaRepository.save(brand);

            mockMvc.perform(get("/api/v1/brands/{brandId}", brand.getBrandId()))
                    .andExpect(status().isNotFound());
        }
    }
}
