package com.loopers.application.admin;

import com.loopers.application.admin.brand.AdminBrandAppService;
import com.loopers.application.admin.brand.AdminBrandFacade;
import com.loopers.application.admin.product.AdminProductAppService;
import com.loopers.domain.brand.Brand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AdminBrandFacade 단위 테스트")
class AdminBrandFacadeTest {

    private AdminBrandFacade adminBrandFacade;
    private AdminBrandAppService adminBrandAppService;
    private AdminProductAppService adminProductAppService;

    @BeforeEach
    void setUp() {
        adminBrandAppService = mock(AdminBrandAppService.class);
        adminProductAppService = mock(AdminProductAppService.class);
        adminBrandFacade = new AdminBrandFacade(adminBrandAppService, adminProductAppService);
    }

    @Nested
    @DisplayName("브랜드 생성")
    class CreateTest {

        @Test
        @DisplayName("브랜드를 생성하면 AdminBrandAppService에 위임한다")
        void create() {
            // given
            Brand brand = mock(Brand.class);
            given(brand.getName()).willReturn("테스트 브랜드");
            given(adminBrandAppService.create("테스트 브랜드")).willReturn(brand);

            // when
            Brand result = adminBrandFacade.create("테스트 브랜드");

            // then
            assertThat(result.getName()).isEqualTo("테스트 브랜드");
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class UpdateTest {

        @Test
        @DisplayName("브랜드를 수정하면 AdminBrandAppService에 위임한다")
        void update() {
            // given
            Brand updatedBrand = mock(Brand.class);
            given(updatedBrand.getName()).willReturn("수정된 브랜드");
            given(adminBrandAppService.update(1L, "수정된 브랜드")).willReturn(updatedBrand);

            // when
            Brand result = adminBrandFacade.update(1L, "수정된 브랜드");

            // then
            assertThat(result.getName()).isEqualTo("수정된 브랜드");
        }
    }

    @Nested
    @DisplayName("브랜드 삭제")
    class DeleteTest {

        @Test
        @DisplayName("브랜드를 삭제하면 해당 브랜드의 상품도 함께 삭제한다")
        void delete_deletesProductsToo() {
            // when
            adminBrandFacade.delete(1L);

            // then
            verify(adminProductAppService).deleteByBrandId(1L);
            verify(adminBrandAppService).delete(1L);
        }
    }

    @Nested
    @DisplayName("브랜드 조회")
    class GetTest {

        @Test
        @DisplayName("ID로 브랜드를 조회한다")
        void getById() {
            // given
            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(1L);
            given(adminBrandAppService.getById(1L)).willReturn(brand);

            // when
            Brand result = adminBrandFacade.getById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("전체 브랜드를 조회한다")
        void getAll() {
            // given
            Brand brandA = mock(Brand.class);
            Brand brandB = mock(Brand.class);
            given(adminBrandAppService.getAll()).willReturn(List.of(brandA, brandB));

            // when
            List<Brand> result = adminBrandFacade.getAll();

            // then
            assertThat(result).hasSize(2);
        }
    }
}
