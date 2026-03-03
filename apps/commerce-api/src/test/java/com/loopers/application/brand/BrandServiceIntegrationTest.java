package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

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
            Brand result = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @Test
        void 이미_존재하는_브랜드명으로_등록하면_예외() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            assertThatThrownBy(() -> brandService.register(BrandCommand.Create.of("나이키", "다른 설명")))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드와_동일한_이름으로_등록하면_예외() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brand.delete();
            brandRepository.save(brand);

            assertThatThrownBy(() -> brandService.register(BrandCommand.Create.of("나이키", "새 설명")))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }
    }

    @Nested
    class 브랜드_수정 {

        @Test
        void 유효한_정보로_수정하면_성공한다() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            Brand result = brandService.updateInfo(brand.getId(), BrandCommand.UpdateInfo.of("아디다스", "독일 스포츠 브랜드"));

            assertThat(result.getName()).isEqualTo("아디다스");
            assertThat(result.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @Test
        void 미존재_브랜드면_예외() {
            assertThatThrownBy(() -> brandService.updateInfo(999L, BrandCommand.UpdateInfo.of("나이키", null)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드를_수정하면_예외() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brandService.delete(brand.getId());

            assertThatThrownBy(() -> brandService.updateInfo(brand.getId(), BrandCommand.UpdateInfo.of("아디다스", null)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }

        @Test
        void 다른_브랜드와_이름이_중복이면_예외() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));

            assertThatThrownBy(() -> brandService.updateInfo(adidas.getId(), BrandCommand.UpdateInfo.of("나이키", null)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 삭제된_브랜드와_이름이_중복이면_예외() {
            Brand nike = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            nike.delete();
            brandRepository.save(nike);

            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));

            assertThatThrownBy(() -> brandService.updateInfo(adidas.getId(), BrandCommand.UpdateInfo.of("나이키", null)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT))
                    .hasMessageContaining("이미 등록된 브랜드입니다");
        }

        @Test
        void 자기_자신_이름으로_수정하면_정상_처리된다() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            Brand result = brandService.updateInfo(brand.getId(), BrandCommand.UpdateInfo.of("나이키", "변경된 설명"));

            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isEqualTo("변경된 설명");
        }

    }

    @Nested
    class 브랜드_삭제 {

        @Test
        void 활성_브랜드를_삭제하면_삭제_상태로_변경된다() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            brandService.delete(brand.getId());

            Brand result = brandService.getBrand(brand.getId());
            assertThat(result.isDeleted()).isTrue();
        }

        @Test
        void 이미_삭제된_브랜드를_삭제해도_성공한다() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brandService.delete(brand.getId());

            assertThatCode(() -> brandService.delete(brand.getId()))
                    .doesNotThrowAnyException();
        }

        @Test
        void 미존재_브랜드면_예외() {
            assertThatThrownBy(() -> brandService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
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
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brand.delete();
            brandRepository.save(brand);

            Brand result = brandService.getBrand(brand.getId());

            assertThat(result.getId()).isEqualTo(brand.getId());
            assertThat(result.isDeleted()).isTrue();
        }
    }

    @Nested
    class 브랜드_목록_조회 {

        @Test
        void 조건_없이_조회하면_전체_브랜드가_최신순으로_반환된다() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));
            brandService.register(BrandCommand.Create.of("뉴발란스", "미국 스포츠 브랜드"));

            Page<Brand> result = brandService.findBrands(null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("뉴발란스");
            assertThat(result.getContent().get(1).getName()).isEqualTo("아디다스");
            assertThat(result.getContent().get(2).getName()).isEqualTo("나이키");
        }

        @Test
        void name_키워드로_검색하면_부분_일치하는_브랜드만_반환된다() {
            brandService.register(BrandCommand.Create.of("나이키 에어", "에어 시리즈"));
            brandService.register(BrandCommand.Create.of("나이키 조던", "조던 시리즈"));
            brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));

            Page<Brand> result = brandService.findBrands("나이키", null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Brand::getName)
                    .containsExactly("나이키 조던", "나이키 에어");
        }

        @Test
        void deleted_false면_활성_브랜드만_반환된다() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));
            adidas.delete();
            brandRepository.save(adidas);

            Page<Brand> result = brandService.findBrands(null, false, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키");
        }

        @Test
        void deleted_true면_삭제된_브랜드만_반환된다() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));
            adidas.delete();
            brandRepository.save(adidas);

            Page<Brand> result = brandService.findBrands(null, true, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("아디다스");
        }

        @Test
        void 복합_조건_name과_deleted_적용_시_모두_반영된다() {
            brandService.register(BrandCommand.Create.of("나이키 에어", "에어 시리즈"));
            Brand deletedNike = brandService.register(BrandCommand.Create.of("나이키 조던", "조던 시리즈"));
            deletedNike.delete();
            brandRepository.save(deletedNike);
            brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));

            Page<Brand> result = brandService.findBrands("나이키", false, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키 에어");
        }

        @Test
        void 결과가_없으면_빈_페이지를_반환한다() {
            Page<Brand> result = brandService.findBrands("존재하지않는", null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    class 브랜드_일괄_조회 {

        @Test
        void ID_목록에_해당하는_브랜드들이_반환된다() {
            Brand nike = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));
            brandService.register(BrandCommand.Create.of("뉴발란스", "미국 스포츠 브랜드"));

            List<Brand> result = brandService.getBrands(List.of(nike.getId(), adidas.getId()));

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Brand::getName)
                    .containsExactlyInAnyOrder("나이키", "아디다스");
        }

        @Test
        void 빈_목록을_전달하면_빈_결과를_반환한다() {
            List<Brand> result = brandService.getBrands(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void 존재하지_않는_ID가_포함되면_존재하는_것만_반환된다() {
            Brand nike = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            List<Brand> result = brandService.getBrands(List.of(nike.getId(), 999L));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("나이키");
        }
    }

    @Nested
    class 활성_브랜드_조회 {

        @Test
        void 활성_브랜드를_조회하면_성공한다() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));

            Brand result = brandService.getActiveBrand(brand.getId());

            assertThat(result.getId()).isEqualTo(brand.getId());
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @Test
        void 삭제된_브랜드를_조회하면_예외() {
            Brand brand = brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            brand.delete();
            brandRepository.save(brand);

            assertThatThrownBy(() -> brandService.getActiveBrand(brand.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }

        @Test
        void 미존재_브랜드를_조회하면_예외() {
            assertThatThrownBy(() -> brandService.getActiveBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 브랜드입니다");
        }
    }

    @Nested
    class 활성_브랜드_목록_조회 {

        @Test
        void 활성_브랜드만_이름_오름차순으로_반환된다() {
            brandService.register(BrandCommand.Create.of("다나이키", "스포츠 브랜드"));
            brandService.register(BrandCommand.Create.of("가아디다스", "독일 스포츠 브랜드"));
            brandService.register(BrandCommand.Create.of("나뉴발란스", "미국 스포츠 브랜드"));

            Page<Brand> result = brandService.findActiveBrands(null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("가아디다스");
            assertThat(result.getContent().get(1).getName()).isEqualTo("나뉴발란스");
            assertThat(result.getContent().get(2).getName()).isEqualTo("다나이키");
        }

        @Test
        void 삭제된_브랜드는_제외된다() {
            brandService.register(BrandCommand.Create.of("나이키", "스포츠 브랜드"));
            Brand adidas = brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));
            adidas.delete();
            brandRepository.save(adidas);

            Page<Brand> result = brandService.findActiveBrands(null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키");
        }

        @Test
        void name_키워드로_검색하면_활성_브랜드_중_부분_일치하는_것만_반환된다() {
            brandService.register(BrandCommand.Create.of("나이키 에어", "에어 시리즈"));
            brandService.register(BrandCommand.Create.of("나이키 조던", "조던 시리즈"));
            brandService.register(BrandCommand.Create.of("아디다스", "독일 스포츠 브랜드"));

            Page<Brand> result = brandService.findActiveBrands("나이키", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Brand::getName)
                    .containsExactly("나이키 에어", "나이키 조던");
        }

        @Test
        void 결과가_없으면_빈_페이지를_반환한다() {
            Page<Brand> result = brandService.findActiveBrands("존재하지않는", PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}
