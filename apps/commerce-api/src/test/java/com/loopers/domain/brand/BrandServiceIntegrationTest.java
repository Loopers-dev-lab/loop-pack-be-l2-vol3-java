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
        void 설명_없이_등록하면_브랜드가_생성된다() {
            Brand result = brandService.register("나이키", null);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isNull();
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
}
