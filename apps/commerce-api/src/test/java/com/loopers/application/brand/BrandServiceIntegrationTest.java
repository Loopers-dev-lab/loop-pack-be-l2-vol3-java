package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
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

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class createBrand {

        @DisplayName("유효한 정보를 입력하면, 브랜드가 DB에 저장된다.")
        @Test
        void savesBrandToDatabase_whenValidInputProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand = brandRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(savedBrand.getId()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand.getName()).isEqualTo(name),
                    () -> assertThat(savedBrand.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("이미 존재하는 브랜드 이름을 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateBrandNameProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            brandService.createBrand(name, logoUrl, description);

            // act & assert
            assertThatThrownBy(() -> brandService.createBrand(name, logoUrl, description))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXIST_BRAND_NAME));
        }

        @DisplayName("삭제된 브랜드 이름으로 다시 생성할 수 있다.")
        @Test
        void allowsCreatingBrandWithDeletedName() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            var brandResult = brandService.createBrand(name, logoUrl, description);
            var savedBrand = brandRepository.findById(brandResult.id()).orElseThrow();
            savedBrand.delete();
            brandRepository.save(savedBrand);

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand2 = brandRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(savedBrand2.getId()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand2.getName()).isEqualTo(name),
                    () -> assertThat(savedBrand2.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand2.getDescription()).isEqualTo(description)
            );
        }
    }
}