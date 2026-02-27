package com.loopers.domain.brand;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BrandModel 도메인 모델 테스트")
class BrandModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            BrandModel brand = BrandModel.create("루퍼스", "브랜드 설명", "서울시 강남구");

            assertThat(brand.getBrandName()).isEqualTo("루퍼스");
            assertThat(brand.getDescription()).isEqualTo("브랜드 설명");
            assertThat(brand.getAddress()).isEqualTo("서울시 강남구");
        }

        @Test
        @DisplayName("brandName이 null이면 CoreException 발생")
        void create_WithNullBrandName_ShouldThrowCoreException() {
            assertThatThrownBy(() -> BrandModel.create(null, "설명", "주소"))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("brandName이 빈 문자열이면 CoreException 발생")
        void create_WithBlankBrandName_ShouldThrowCoreException() {
            assertThatThrownBy(() -> BrandModel.create("  ", "설명", "주소"))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("생성 시 displayStatus 기본값은 ACTIVE이다")
        void create_DefaultDisplayStatus_ShouldBeACTIVE() {
            BrandModel brand = createTestBrand();
            assertThat(brand.getDisplayStatus()).isEqualTo(DisplayStatus.ACTIVE);
        }

        @Test
        @DisplayName("생성 시 del_yn 기본값은 'N'이다")
        void create_DefaultDelYn_ShouldBeN() {
            BrandModel brand = createTestBrand();
            assertThat(brand.getDelYn()).isEqualTo("N");
        }

        @Test
        @DisplayName("BaseStringIdEntity를 상속한다")
        void create_ShouldExtendBaseStringIdEntity() {
            BrandModel brand = createTestBrand();
            assertThat(brand).isInstanceOf(BaseStringIdEntity.class);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransitionTests {

        @Test
        @DisplayName("hide 호출 시 displayStatus가 HIDDEN으로 변경된다")
        void hide_ShouldSetDisplayStatusToHIDDEN() {
            BrandModel brand = createTestBrand();
            brand.hide();
            assertThat(brand.getDisplayStatus()).isEqualTo(DisplayStatus.HIDDEN);
        }

        @Test
        @DisplayName("activate 호출 시 displayStatus가 ACTIVE로 변경된다")
        void activate_ShouldSetDisplayStatusToACTIVE() {
            BrandModel brand = createTestBrand();
            brand.hide();
            brand.activate();
            assertThat(brand.getDisplayStatus()).isEqualTo(DisplayStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("소프트 삭제")
    class SoftDeleteTests {

        @Test
        @DisplayName("softDelete 호출 시 del_yn='Y', deletedAt 설정")
        void softDelete_ShouldSetDelYnYAndDeletedAt() {
            BrandModel brand = createTestBrand();
            brand.softDelete();

            assertThat(brand.getDelYn()).isEqualTo("Y");
            assertThat(brand.getDeletedAt()).isNotNull();
            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("이미 삭제된 상태에서 softDelete는 멱등하다")
        void softDelete_ShouldBeIdempotent() {
            BrandModel brand = createTestBrand();
            brand.softDelete();
            var firstDeletedAt = brand.getDeletedAt();

            brand.softDelete();
            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }

        @Test
        @DisplayName("restore 호출 시 del_yn='N', deletedAt=null")
        void restore_ShouldSetDelYnNAndClearDeletedAt() {
            BrandModel brand = createTestBrand();
            brand.softDelete();
            brand.restore();

            assertThat(brand.getDelYn()).isEqualTo("N");
            assertThat(brand.getDeletedAt()).isNull();
            assertThat(brand.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("고객 노출 여부")
    class VisibilityTests {

        @Test
        @DisplayName("ACTIVE이고 삭제되지 않은 경우 true를 반환한다")
        void isVisibleForCustomer_WhenActiveAndNotDeleted_ShouldReturnTrue() {
            BrandModel brand = createTestBrand();
            assertThat(brand.isVisibleForCustomer()).isTrue();
        }

        @Test
        @DisplayName("HIDDEN인 경우 false를 반환한다")
        void isVisibleForCustomer_WhenHidden_ShouldReturnFalse() {
            BrandModel brand = createTestBrand();
            brand.hide();
            assertThat(brand.isVisibleForCustomer()).isFalse();
        }

        @Test
        @DisplayName("삭제된 경우 false를 반환한다")
        void isVisibleForCustomer_WhenDeleted_ShouldReturnFalse() {
            BrandModel brand = createTestBrand();
            brand.softDelete();
            assertThat(brand.isVisibleForCustomer()).isFalse();
        }
    }

    @Nested
    @DisplayName("정보 수정")
    class UpdateInfoTests {

        @Test
        @DisplayName("유효한 이름으로 수정 성공")
        void updateInfo_WithValidName_ShouldUpdate() {
            BrandModel brand = createTestBrand();
            brand.updateInfo("새브랜드", "새설명", "새주소");

            assertThat(brand.getBrandName()).isEqualTo("새브랜드");
            assertThat(brand.getDescription()).isEqualTo("새설명");
            assertThat(brand.getAddress()).isEqualTo("새주소");
        }

        @Test
        @DisplayName("빈 이름으로 수정 시 CoreException 발생")
        void updateInfo_WithBlankName_ShouldThrowCoreException() {
            BrandModel brand = createTestBrand();
            assertThatThrownBy(() -> brand.updateInfo("  ", "설명", "주소"))
                    .isInstanceOf(CoreException.class);
        }
    }

    // === Helper ===

    private BrandModel createTestBrand() {
        return BrandModel.create("루퍼스", "브랜드 설명", "서울시 강남구");
    }
}
