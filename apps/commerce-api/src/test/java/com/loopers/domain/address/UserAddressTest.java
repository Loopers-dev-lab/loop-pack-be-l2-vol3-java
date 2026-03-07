package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserAddressTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_모든_필드가_저장된다() {
            // act
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층 401호");

            // assert
            assertThat(address)
                    .extracting(UserAddress::getUserId, UserAddress::getReceiverName,
                            UserAddress::getPhone, UserAddress::getZipCode,
                            UserAddress::getAddressLine1, UserAddress::getAddressLine2,
                            UserAddress::isDefault)
                    .containsExactly(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층 401호", false);
        }
    }

    @DisplayName("수정할 때,")
    @Nested
    class 수정 {

        @Test
        void receiverName_phone_zipCode_addressLine이_변경된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층 401호");

            // act
            address.changeInfo("김철수", "010-9876-5432", "54321", "부산시 해운대구", "3층 302호");

            // assert
            assertThat(address)
                    .extracting(UserAddress::getReceiverName, UserAddress::getPhone,
                            UserAddress::getZipCode, UserAddress::getAddressLine1, UserAddress::getAddressLine2)
                    .containsExactly("김철수", "010-9876-5432", "54321", "부산시 해운대구", "3층 302호");
        }
    }

    @DisplayName("기본주소를 설정할 때,")
    @Nested
    class 기본주소설정 {

        @Test
        void setAsDefault로_isDefault가_true가_된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // act
            address.setAsDefault();

            // assert
            assertThat(address.isDefault()).isTrue();
        }

        @Test
        void unsetDefault로_isDefault가_false가_된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            address.setAsDefault();

            // act
            address.unsetDefault();

            // assert
            assertThat(address.isDefault()).isFalse();
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class 소유권확인 {

        @Test
        void 본인의_주소가_아니면_예외가_발생한다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // act & assert
            assertThatThrownBy(() -> address.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.NOT_OWNER);
        }

        @Test
        void 본인의_주소이면_예외가_발생하지_않는다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // act & assert
            assertThatCode(() -> address.validateOwnership(1L)).doesNotThrowAnyException();
        }
    }

    @DisplayName("삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 삭제되지_않은_주소는_정상적으로_삭제된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // act
            address.remove();

            // assert
            assertThat(address.getDeletedAt()).isNotNull();
        }
    }
}
