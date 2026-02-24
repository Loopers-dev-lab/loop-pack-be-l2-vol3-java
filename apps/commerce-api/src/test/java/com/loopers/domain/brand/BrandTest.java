package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandTest {

    @Nested
    @DisplayName("Brand 생성")
    class Create {

        @DisplayName("유효한 정보로 Brand를 생성할 수 있다")
        @Test
        void create_withValidInfo_succeeds() {
            Brand brand = new Brand("나이키", "스포츠 브랜드");

            assertThat(brand.getName()).isEqualTo("나이키");
            assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        }
    }

    @Nested
    @DisplayName("Brand 수정")
    class Update {

        @DisplayName("브랜드 이름을 변경할 수 있다")
        @Test
        void changeName_withNewName_updatesName() {
            Brand brand = new Brand("나이키", "스포츠 브랜드");

            brand.changeName("아디다스");

            assertThat(brand.getName()).isEqualTo("아디다스");
        }

        @DisplayName("브랜드 설명을 변경할 수 있다")
        @Test
        void changeDescription_withNewDescription_updatesDescription() {
            Brand brand = new Brand("나이키", "스포츠 브랜드");

            brand.changeDescription("글로벌 스포츠 브랜드");

            assertThat(brand.getDescription()).isEqualTo("글로벌 스포츠 브랜드");
        }
    }
}
