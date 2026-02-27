package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Brand API V1 E2E 테스트")
class AdminBrandV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BrandJpaRepository brandJpaRepository;

    @Nested
    @DisplayName("POST /api-admin/v1/brands - 브랜드 생성")
    class CreateBrandTests {

        @Test
        @DisplayName("정상 생성 시 200 반환")
        void POST_createBrand_ShouldReturn200() throws Exception {
            var request = AdminBrandV1Dto.CreateBrandRequest.builder()
                    .brandName("테스트브랜드").description("설명").address("서울").build();

            mockMvc.perform(post("/api-admin/v1/brands")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.brandId").exists())
                    .andExpect(jsonPath("$.data.brandName").value("테스트브랜드"));
        }

        @Test
        @DisplayName("빈 이름으로 400 반환")
        void POST_createBrand_BlankName_ShouldReturn400() throws Exception {
            var request = AdminBrandV1Dto.CreateBrandRequest.builder()
                    .brandName("").description("설명").address("서울").build();

            mockMvc.perform(post("/api-admin/v1/brands")
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/brands/{brandId} - 브랜드 수정")
    class UpdateBrandTests {

        @Test
        @DisplayName("수정 후 변경된 필드 확인")
        void PUT_updateBrand_ShouldReturn200() throws Exception {
            BrandModel brand = brandJpaRepository.save(BrandModel.create("원래이름", "설명", "서울"));

            var request = AdminBrandV1Dto.UpdateBrandRequest.builder()
                    .brandName("수정이름").description("수정설명").address("부산").build();

            mockMvc.perform(put("/api-admin/v1/brands/" + brand.getBrandId())
                            .header(ADMIN_HEADER, ADMIN_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.brandName").value("수정이름"));
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/brands/{brandId} - 브랜드 삭제")
    class DeleteBrandTests {

        @Test
        @DisplayName("softDelete 수행")
        void DELETE_deleteBrand_ShouldReturn200() throws Exception {
            BrandModel brand = brandJpaRepository.save(BrandModel.create("삭제브랜드", "설명", "서울"));

            mockMvc.perform(delete("/api-admin/v1/brands/" + brand.getBrandId())
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/brands - 브랜드 목록")
    class ListBrandTests {

        @Test
        @DisplayName("HIDDEN/삭제 브랜드가 모두 포함된다")
        void GET_brands_ShouldIncludeHiddenAndDeleted() throws Exception {
            BrandModel active = BrandModel.create("활성", "설명", "서울");
            BrandModel hidden = BrandModel.create("숨김", "설명", "부산");
            hidden.hide();
            BrandModel deleted = BrandModel.create("삭제", "설명", "대전");
            deleted.softDelete();

            brandJpaRepository.save(active);
            brandJpaRepository.save(hidden);
            brandJpaRepository.save(deleted);

            mockMvc.perform(get("/api-admin/v1/brands")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3));
        }

        @Test
        @DisplayName("삭제된 브랜드를 관리자가 조회할 수 있다")
        void GET_deletedBrand_ShouldReturn200_AdminAccessible() throws Exception {
            BrandModel deleted = BrandModel.create("삭제브랜드", "설명", "서울");
            deleted.softDelete();
            brandJpaRepository.save(deleted);

            mockMvc.perform(get("/api-admin/v1/brands")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].delYn").value("Y"));
        }
    }
}
