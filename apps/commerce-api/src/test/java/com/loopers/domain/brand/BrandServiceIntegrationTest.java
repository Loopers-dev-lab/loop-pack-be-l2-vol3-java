package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 브랜드_등록 {

        @Test
        void 유효한_정보로_등록하면_브랜드가_생성된다() {
            Brand result = brandService.register("나이키", "스포츠 브랜드");

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @Test
        void 이미_존재하는_브랜드명으로_등록하면_예외() {
            brandService.register("나이키", "스포츠 브랜드");

            assertThatThrownBy(() -> brandService.register("나이키", "다른 설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드와_동일한_이름으로_등록하면_예외() {
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            brand.delete();
            brandRepository.save(brand);

            assertThatThrownBy(() -> brandService.register("나이키", "새 설명"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }
    }

    @Nested
    class 브랜드_수정 {

        @Test
        void 유효한_정보로_수정하면_성공한다() {
            Brand brand = brandService.register("나이키", "스포츠 브랜드");

            Brand activeBrand = brandService.getBrand(brand.getId());
            brandService.update(activeBrand, "아디다스", "독일 스포츠 브랜드");

            assertThat(activeBrand.getName()).isEqualTo("아디다스");
            assertThat(activeBrand.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @Test
        void 다른_브랜드와_이름이_중복이면_예외() {
            brandService.register("나이키", "스포츠 브랜드");
            Brand adidas = brandService.register("아디다스", "독일 스포츠 브랜드");

            Brand activeBrand = brandService.getBrand(adidas.getId());

            assertThatThrownBy(() -> brandService.update(activeBrand, "나이키", null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드와_이름이_중복이면_예외() {
            Brand nike = brandService.register("나이키", "스포츠 브랜드");
            nike.delete();
            brandRepository.save(nike);

            Brand adidas = brandService.register("아디다스", "독일 스포츠 브랜드");

            Brand activeBrand = brandService.getBrand(adidas.getId());

            assertThatThrownBy(() -> brandService.update(activeBrand, "나이키", null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 자기_자신_이름으로_수정하면_정상_처리된다() {
            Brand brand = brandService.register("나이키", "스포츠 브랜드");

            Brand activeBrand = brandService.getBrand(brand.getId());
            brandService.update(activeBrand, "나이키", "변경된 설명");

            assertThat(activeBrand.getName()).isEqualTo("나이키");
            assertThat(activeBrand.getDescription()).isEqualTo("변경된 설명");
        }

    }

    @Nested
    class 브랜드_삭제 {

        @Test
        void 활성_브랜드를_삭제하면_삭제_상태로_변경된다() {
            Brand brand = brandService.register("나이키", "스포츠 브랜드");

            Brand activeBrand = brandService.getBrand(brand.getId());
            brandService.delete(activeBrand);

            assertThat(activeBrand.isDeleted()).isTrue();
        }
    }

    @Nested
    class 브랜드_조회 {

        @Test
        void 미존재_브랜드면_예외() {
            assertThatThrownBy(() -> brandService.getBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드도_조회된다() {
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            brand.delete();
            brandRepository.save(brand);

            Brand result = brandService.getBrand(brand.getId());

            assertThat(result.getId()).isEqualTo(brand.getId());
            assertThat(result.isDeleted()).isTrue();
        }
    }
}
