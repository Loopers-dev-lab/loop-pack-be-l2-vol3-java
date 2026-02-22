package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Brand 도메인 테스트")
class BrandTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 이름으로 브랜드를 생성할 수 있다")
        void createBrand() {
            Brand brand = Brand.create("나이키");

            assertThat(brand.getId()).isNull();
            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("ID를 포함하여 브랜드 객체를 복원할 수 있다")
        void createBrandWithId() {
            Brand brand = Brand.of(1L, "아디다스", false);

            assertThat(brand.getId()).isEqualTo(1L);
            assertThat(brand.getName()).isEqualTo("아디다스");
            assertThat(brand.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("이름이 빈 값이면 예외가 발생한다")
        void createWithEmptyNameThrowsException() {
            assertThatThrownBy(() -> Brand.create(""))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("브랜드 이름은 필수");
        }

        @Test
        @DisplayName("이름이 null이면 예외가 발생한다")
        void createWithNullNameThrowsException() {
            assertThatThrownBy(() -> Brand.create(null))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("이름이 공백만 있으면 예외가 발생한다")
        void createWithBlankNameThrowsException() {
            assertThatThrownBy(() -> Brand.create("   "))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("수정 테스트")
    class UpdateTest {

        @Test
        @DisplayName("브랜드 이름을 수정할 수 있다")
        void updateBrand() {
            Brand brand = Brand.create("원래 이름");

            brand.update("새 이름");

            assertThat(brand.getName()).isEqualTo("새 이름");
        }

        @Test
        @DisplayName("수정 시 이름이 빈 값이면 예외가 발생한다")
        void updateWithEmptyNameThrowsException() {
            Brand brand = Brand.create("원래 이름");

            assertThatThrownBy(() -> brand.update(""))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("삭제 테스트")
    class DeleteTest {

        @Test
        @DisplayName("브랜드를 삭제할 수 있다")
        void deleteBrand() {
            Brand brand = Brand.create("나이키");

            brand.delete();

            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("삭제된 브랜드를 복원할 수 있다")
        void restoreBrand() {
            Brand brand = Brand.create("나이키");
            brand.delete();

            brand.restore();

            assertThat(brand.isDeleted()).isFalse();
        }
    }
}
