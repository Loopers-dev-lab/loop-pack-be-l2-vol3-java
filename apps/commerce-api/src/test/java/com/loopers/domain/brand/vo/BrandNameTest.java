package com.loopers.domain.brand.vo;

import com.loopers.domain.brand.exception.BrandValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;

public class BrandNameTest {

    @Nested
    @DisplayName("BrandName 생성 검증")
    class BrandNameValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 브랜드 이름")
        @ValueSource(strings = {"퍼피박스", "도그월드", "A", "Brand123", "abc"})
        void validBrandName(String name) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new BrandName(name));
        }

        @ParameterizedTest
        @DisplayName("브랜드 이름 null 또는 빈 문자열")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        void nullOrEmptyBrandName(String name) {
            // when & then
            assertThatThrownBy(() -> new BrandName(name))
                    .isInstanceOf(BrandValidationException.class);
        }

        @Test
        @DisplayName("브랜드 이름 길이 제한 - 50자 초과")
        void brandNameExceeds50Chars() {
            // given
            String name = "a".repeat(51);

            // when & then
            assertThatThrownBy(() -> new BrandName(name))
                    .isInstanceOf(BrandValidationException.class);
        }

        @Test
        @DisplayName("브랜드 이름 길이 경계값 - 50자 성공")
        void brandNameExactly50Chars() {
            // given
            String name = "a".repeat(50);

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new BrandName(name));
        }

        @Test
        @DisplayName("브랜드 이름 특수문자 포함 - 허용")
        void brandNameWithSpecialCharsAllowed() {
            // given - 하이픈, 언더스코어,ドット 허용
            String name = "Dog-Care_Shop.ko";

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new BrandName(name));
        }
    }

    @Nested
    @DisplayName("BrandName 불변성")
    class BrandNameImmutabilityTest {

        @Test
        @DisplayName("BrandName은 불변 객체")
        void brandNameIsImmutable() {
            // given
            BrandName name = new BrandName("퍼피박스");

            // when & then - value는 변경 불가
            assertThat(name.value()).isEqualTo("퍼피박스");
            assertThat(Modifier.isFinal(BrandName.class.getModifiers())).isTrue();
        }
    }
}
