package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 이름이 주어지면, 생성된다.")
        @Test
        void create_withValidName_shouldSucceed() {
            // given
            String name = "테스트 브랜드";

            // when
            BrandModel brand = BrandModel.create(name);

            // then
            assertThat(brand.getName()).isEqualTo(name);
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("이름이 null이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> BrandModel.create(null));
        }

        @DisplayName("이름이 빈 문자열이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withBlankName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> BrandModel.create(""));
        }

        @DisplayName("이름이 공백만 있으면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withWhitespaceOnlyName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> BrandModel.create("   "));
        }

        @DisplayName("이름 앞뒤 공백이 있으면, trim된 이름으로 저장된다.")
        @Test
        void create_withLeadingTrailingSpaces_shouldTrimName() {
            // given
            String input = "  브랜드  ";

            // when
            BrandModel brand = BrandModel.create(input);

            // then
            assertThat(brand.getName()).isEqualTo("브랜드");
        }

        @DisplayName("이름이 탭·개행만 있으면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withTabOrNewlineOnlyName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> BrandModel.create("\t"));
            assertThrows(IllegalArgumentException.class, () -> BrandModel.create("\n"));
        }

        @DisplayName("이름이 null이면, 예외 메시지에 'null' 관련 문구가 포함된다.")
        @Test
        void create_withNullName_shouldThrowWithMessage() {
            // when
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BrandModel.create(null));

            // then
            assertThat(thrown.getMessage()).contains("null");
        }

        @DisplayName("이름이 비어 있으면, 예외 메시지에 '비어' 관련 문구가 포함된다.")
        @Test
        void create_withBlankName_shouldThrowWithMessage() {
            // when
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> BrandModel.create(""));

            // then
            assertThat(thrown.getMessage()).contains("비어");
        }
    }

    @DisplayName("이름을 수정할 때, ")
    @Nested
    class UpdateName {

        @DisplayName("유효한 이름이 주어지면, 이름이 변경된다.")
        @Test
        void updateName_withValidName_shouldUpdate() {
            // given
            BrandModel brand = BrandModel.create("기존 이름");

            // when
            brand.updateName("새 이름");

            // then
            assertThat(brand.getName()).isEqualTo("새 이름");
        }

        @DisplayName("이름이 null이면, IllegalArgumentException이 발생한다.")
        @Test
        void updateName_withNullName_shouldThrow() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when & then
            assertThrows(IllegalArgumentException.class, () -> brand.updateName(null));
        }

        @DisplayName("이름이 빈 문자열이면, IllegalArgumentException이 발생한다.")
        @Test
        void updateName_withBlankName_shouldThrow() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when & then
            assertThrows(IllegalArgumentException.class, () -> brand.updateName(""));
        }

        @DisplayName("이름이 공백만 있으면, IllegalArgumentException이 발생한다.")
        @Test
        void updateName_withWhitespaceOnlyName_shouldThrow() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when & then
            assertThrows(IllegalArgumentException.class, () -> brand.updateName("   "));
        }

        @DisplayName("이름이 탭·개행만 있으면, IllegalArgumentException이 발생한다.")
        @Test
        void updateName_withTabNewlineOnlyName_shouldThrow() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when & then
            assertThrows(IllegalArgumentException.class, () -> brand.updateName("\t\n"));
        }

        @DisplayName("이름 앞뒤 공백이 있으면, trim된 이름으로 저장된다.")
        @Test
        void updateName_withLeadingTrailingSpaces_shouldTrimName() {
            // given
            BrandModel brand = BrandModel.create("기존");

            // when
            brand.updateName("  새이름  ");

            // then
            assertThat(brand.getName()).isEqualTo("새이름");
        }
    }

    @DisplayName("삭제 여부를 확인할 때, ")
    @Nested
    class IsDeleted {

        @DisplayName("생성 직후에는 삭제되지 않은 상태이다.")
        @Test
        void isDeleted_whenNotDeleted_shouldReturnFalse() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when & then
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("delete() 호출 후에는 삭제된 상태이다.")
        @Test
        void isDeleted_afterDelete_shouldReturnTrue() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when
            brand.delete();

            // then
            assertThat(brand.isDeleted()).isTrue();
        }

        @DisplayName("restore() 호출 후에는 삭제되지 않은 상태이다.")
        @Test
        void isDeleted_afterRestore_shouldReturnFalse() {
            // given
            BrandModel brand = BrandModel.create("브랜드");
            brand.delete();

            // when
            brand.restore();

            // then
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("delete()를 두 번 호출해도 삭제 상태는 동일하다. (멱등)")
        @Test
        void isDeleted_afterDeleteTwice_shouldRemainDeleted() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when
            brand.delete();
            brand.delete();

            // then
            assertThat(brand.isDeleted()).isTrue();
        }

        @DisplayName("restore()를 두 번 호출해도 미삭제 상태이다. (멱등)")
        @Test
        void isDeleted_afterRestoreTwiceWithoutDelete_shouldRemainNotDeleted() {
            // given
            BrandModel brand = BrandModel.create("브랜드");

            // when
            brand.restore();
            brand.restore();

            // then
            assertThat(brand.isDeleted()).isFalse();
        }
    }
}
