package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("Brand를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenInfoIsValid() {
            // arrange & act
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");

            // assert
            assertThat(brand.getName()).isEqualTo("Nike");
            assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
            assertThat(brand.getLogoUrl()).isEqualTo("https://example.com/nike.png");
            assertThat(brand.getCreatedAt()).isNotNull();
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("ID는 null로 생성된다.")
        @Test
        void createsWithNullId() {
            // act
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");

            // assert
            assertThat(brand.getId()).isNull();
        }
    }

    @DisplayName("Brand를 복원할 때,")
    @Nested
    class Reconstitute {

        @DisplayName("모든 필드가 복원된다.")
        @Test
        void reconstitutesAllFields() {
            // arrange
            Long id = 1L;
            String name = "Nike";
            String description = "스포츠 브랜드";
            String logoUrl = "https://example.com/nike.png";
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(1);
            ZonedDateTime updatedAt = ZonedDateTime.now();
            ZonedDateTime deletedAt = null;

            // act
            Brand brand = Brand.reconstitute(id, name, description, logoUrl, createdAt, updatedAt, deletedAt);

            // assert
            assertThat(brand.getId()).isEqualTo(id);
            assertThat(brand.getName()).isEqualTo(name);
            assertThat(brand.getDescription()).isEqualTo(description);
            assertThat(brand.getLogoUrl()).isEqualTo(logoUrl);
            assertThat(brand.getCreatedAt()).isEqualTo(createdAt);
            assertThat(brand.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(brand.getDeletedAt()).isNull();
        }

        @DisplayName("삭제된 브랜드도 복원된다.")
        @Test
        void reconstitutesDeletedBrand() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime deletedAt = now;

            // act
            Brand brand = Brand.reconstitute(1L, "Nike", "설명", "url", now, now, deletedAt);

            // assert
            assertThat(brand.isDeleted()).isTrue();
            assertThat(brand.getDeletedAt()).isEqualTo(deletedAt);
        }
    }

    @DisplayName("Brand를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("활성 브랜드는 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenBrandIsActive() {
            // arrange
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");

            // act
            brand.update("Adidas", "독일 스포츠 브랜드", "https://example.com/adidas.png");

            // assert
            assertThat(brand.getName()).isEqualTo("Adidas");
            assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
            assertThat(brand.getLogoUrl()).isEqualTo("https://example.com/adidas.png");
        }

        @DisplayName("삭제된 브랜드는 수정할 수 없다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");
            brand.delete();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("Adidas", "설명", "url");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_DELETED);
        }
    }

    @DisplayName("Brand를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("활성 브랜드는 삭제된다.")
        @Test
        void deletesBrand_whenBrandIsActive() {
            // arrange
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");

            // act
            brand.delete();

            // assert
            assertThat(brand.isDeleted()).isTrue();
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 브랜드를 삭제해도 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenBrandIsAlreadyDeleted() {
            // arrange
            Brand brand = Brand.create("Nike", "스포츠 브랜드", "https://example.com/nike.png");
            brand.delete();
            ZonedDateTime firstDeletedAt = brand.getDeletedAt();

            // act
            assertDoesNotThrow(() -> brand.delete());

            // assert
            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}