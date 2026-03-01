package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrandTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllValuesAreValid() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var brand = Brand.create(name, logoUrl, description);

            // assert
            assertAll(
                    () -> assertThat(brand.getName()).isEqualTo(name),
                    () -> assertThat(brand.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("모든 값이 유효하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenAllValuesAreValid() {
            // arrange
            var brand = Brand.create("brand name", "logo url", "description");
            var newName = "new brand name";
            var newLogoUrl = "new logo url";
            var newDescription = "new description";

            // act
            brand.update(newName, newLogoUrl, newDescription);

            // assert
            assertAll(
                    () -> assertThat(brand.getName()).isEqualTo(newName),
                    () -> assertThat(brand.getLogoUrl()).isEqualTo(newLogoUrl),
                    () -> assertThat(brand.getDescription()).isEqualTo(newDescription)
            );
        }
    }
}