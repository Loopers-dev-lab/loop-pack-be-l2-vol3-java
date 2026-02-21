package com.loopers.domain.common.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AddressTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 우편번호가_null이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Address(null, "서울시 강남구", "4층"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 우편번호가_빈값이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Address("  ", "서울시 강남구", "4층"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 기본주소가_null이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Address("06234", null, "4층"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 기본주소가_빈값이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Address("06234", "  ", "4층"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 유효한_정보면_정상_생성된다() {
            // act
            Address address = new Address("06234", "서울시 강남구 테헤란로 123", "4층 401호");

            // assert
            assertThat(address)
                    .extracting(Address::getZipCode, Address::getAddressLine1, Address::getAddressLine2)
                    .containsExactly("06234", "서울시 강남구 테헤란로 123", "4층 401호");
        }

        @Test
        void 상세주소가_null이어도_정상_생성된다() {
            // act
            Address address = new Address("06234", "서울시 강남구 테헤란로 123", null);

            // assert
            assertThat(address.getAddressLine2()).isNull();
        }
    }

    @DisplayName("동등성을 비교할 때,")
    @Nested
    class 동등성 {

        @Test
        void 같은_주소면_동등하다() {
            // arrange
            Address a = new Address("06234", "서울시 강남구", "4층");
            Address b = new Address("06234", "서울시 강남구", "4층");

            // act & assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        void 다른_주소면_동등하지_않다() {
            // arrange
            Address a = new Address("06234", "서울시 강남구", "4층");
            Address b = new Address("12345", "부산시 해운대구", "3층");

            // act & assert
            assertThat(a).isNotEqualTo(b);
        }
    }
}
