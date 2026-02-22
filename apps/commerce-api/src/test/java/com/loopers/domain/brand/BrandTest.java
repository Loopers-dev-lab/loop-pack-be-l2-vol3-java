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
        }

        @Test
        @DisplayName("ID를 포함하여 브랜드 객체를 복원할 수 있다")
        void createBrandWithId() {
            Brand brand = Brand.of(1L, "아디다스");

            assertThat(brand.getId()).isEqualTo(1L);
            assertThat(brand.getName()).isEqualTo("아디다스");
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
}
