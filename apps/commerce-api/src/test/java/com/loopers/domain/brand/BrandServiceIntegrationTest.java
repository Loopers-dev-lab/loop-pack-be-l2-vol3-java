package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드 등록")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 등록하면 브랜드가 생성된다")
        @Test
        void createsBrandSuccessfully() {
            // given & when
            BrandModel result = brandService.register("나이키", "스포츠 브랜드");

            // then
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.name().value()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("중복 브랜드명이면 CONFLICT 예외가 발생한다")
        @Test
        void throwsOnDuplicateName() {
            // given
            brandService.register("나이키", "스포츠 브랜드");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.register("나이키", "다른 설명"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드 조회")
    @Nested
    class GetBrand {

        @DisplayName("존재하고 미삭제 상태면 브랜드를 반환한다")
        @Test
        void returnsBrand() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");

            // when
            BrandModel result = brandService.getBrand(saved.getId());

            // then
            assertThat(result.name().value()).isEqualTo("나이키");
        }

        @DisplayName("삭제된 브랜드면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsWhenDeleted() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");
            brandService.delete(saved.getId());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.getBrand(saved.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @DisplayName("이름과 설명을 변경할 수 있다")
        @Test
        void updatesSuccessfully() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");

            // when
            BrandModel result = brandService.update(saved.getId(), "뉴발란스", "라이프스타일 브랜드");

            // then
            assertAll(
                () -> assertThat(result.name().value()).isEqualTo("뉴발란스"),
                () -> assertThat(result.description()).isEqualTo("라이프스타일 브랜드")
            );
        }

        @DisplayName("동일명 유지 시 중복 체크를 통과한다")
        @Test
        void skipsDuplicateCheckWhenSameName() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");

            // when
            BrandModel result = brandService.update(saved.getId(), "나이키", "설명만 변경");

            // then
            assertAll(
                () -> assertThat(result.name().value()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("설명만 변경")
            );
        }

        @DisplayName("다른 이름으로 변경 시 중복이면 CONFLICT 예외가 발생한다")
        @Test
        void throwsOnDuplicateNameChange() {
            // given
            brandService.register("나이키", "스포츠 브랜드");
            BrandModel target = brandService.register("아디다스", "다른 브랜드");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.update(target.getId(), "나이키", "변경 시도"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class Delete {

        @DisplayName("soft delete 후 customer 조회에서 제외된다")
        @Test
        void excludedFromCustomerQueryAfterDelete() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");

            // when
            brandService.delete(saved.getId());

            // then
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.getBrand(saved.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("soft delete 후 admin 조회에서는 포함된다")
        @Test
        void includedInAdminQueryAfterDelete() {
            // given
            BrandModel saved = brandService.register("나이키", "스포츠 브랜드");

            // when
            brandService.delete(saved.getId());

            // then
            BrandModel result = brandService.getBrandForAdmin(saved.getId());
            assertThat(result.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("페이징된 결과를 반환한다")
        @Test
        void returnsPagedResult() {
            // given
            brandService.register("나이키", "스포츠");
            brandService.register("아디다스", "스포츠");
            brandService.register("뉴발란스", "라이프스타일");

            // when
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(3),
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getTotalPages()).isEqualTo(2)
            );
        }
    }
}
