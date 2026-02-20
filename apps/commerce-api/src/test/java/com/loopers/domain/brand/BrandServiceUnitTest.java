package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceUnitTest {

    private BrandService brandService;
    private FakeBrandRepository fakeBrandRepository;

    @BeforeEach
    void setUp() {
        fakeBrandRepository = new FakeBrandRepository();
        brandService = new BrandService(fakeBrandRepository);
    }

    @DisplayName("브랜드를 등록할 때,")
    @Nested
    class Register {

        @DisplayName("정상 이름이면, 브랜드가 저장된다.")
        @Test
        void registersBrand_whenNameIsValid() {
            // arrange
            String name = "나이키";

            // act
            Brand result = brandService.register(name);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getName().value()).isEqualTo("나이키");
        }

        @DisplayName("동일한 이름의 브랜드가 이미 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsException_whenNameAlreadyExists() {
            // arrange
            brandService.register("나이키");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.register("나이키"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
