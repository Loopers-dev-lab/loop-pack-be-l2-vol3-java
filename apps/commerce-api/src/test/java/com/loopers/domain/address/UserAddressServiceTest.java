package com.loopers.domain.address;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserAddressServiceTest {

    private UserAddressRepository userAddressRepository;
    private UserAddressService userAddressService;

    @BeforeEach
    void setUp() {
        userAddressRepository = Mockito.mock(UserAddressRepository.class);
        userAddressService = new UserAddressService(userAddressRepository);
    }

    @DisplayName("배송지를 등록할 때,")
    @Nested
    class 등록 {

        @Test
        void 첫_번째_주소면_기본주소로_설정된다() {
            // arrange
            when(userAddressRepository.countByUserIdAndDeletedAtIsNull(1L)).thenReturn(0L);
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            UserAddress result = userAddressService.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // assert
            assertThat(result.isDefault()).isTrue();
        }

        @Test
        void 기존_주소가_있으면_기본주소가_아닌_상태로_생성된다() {
            // arrange
            when(userAddressRepository.countByUserIdAndDeletedAtIsNull(1L)).thenReturn(1L);
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            UserAddress result = userAddressService.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // assert
            assertThat(result.isDefault()).isFalse();
        }

        @Test
        void 등록_시_save가_호출된다() {
            // arrange
            when(userAddressRepository.countByUserIdAndDeletedAtIsNull(1L)).thenReturn(0L);
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            userAddressService.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

            // assert
            verify(userAddressRepository).save(any(UserAddress.class));
        }
    }

    @DisplayName("배송지를 수정할 때,")
    @Nested
    class 수정 {

        @Test
        void 존재하지_않는_주소면_예외가_발생한다() {
            // arrange
            when(userAddressRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userAddressService.update(1L, 1L, "김철수", "010-9876-5432", "54321", "부산시", null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.ADDRESS_NOT_FOUND);
        }

        @Test
        void 본인의_주소가_아니면_예외가_발생한다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act & assert
            assertThatThrownBy(() -> userAddressService.update(1L, 999L, "김철수", "010-9876-5432", "54321", "부산시", null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_수정된다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act
            userAddressService.update(1L, 1L, "김철수", "010-9876-5432", "54321", "부산시", null);

            // assert
            assertThat(address.getReceiverName()).isEqualTo("김철수");
        }
    }

    @DisplayName("배송지를 삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 존재하지_않는_주소면_예외가_발생한다() {
            // arrange
            when(userAddressRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userAddressService.delete(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.ADDRESS_NOT_FOUND);
        }

        @Test
        void 본인의_주소가_아니면_예외가_발생한다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act & assert
            assertThatThrownBy(() -> userAddressService.delete(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.NOT_OWNER);
        }

        @Test
        void 기본주소_삭제_시_다른_주소가_기본주소로_전환된다() {
            // arrange
            UserAddress defaultAddress = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            defaultAddress.setAsDefault();
            UserAddress otherAddress = UserAddress.create(1L, "김철수", "010-9876-5432", "54321", "부산시", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(defaultAddress));
            when(userAddressRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNot(1L, 1L))
                    .thenReturn(Optional.of(otherAddress));

            // act
            userAddressService.delete(1L, 1L);

            // assert
            assertThat(otherAddress.isDefault()).isTrue();
        }

        @Test
        void 기본주소가_아닌_주소_삭제_시_다른_주소에_영향_없다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act
            userAddressService.delete(1L, 1L);

            // assert
            assertThat(address.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("배송지를 단건 조회할 때,")
    @Nested
    class 단건조회 {

        @Test
        void 존재하지_않는_주소면_예외가_발생한다() {
            // arrange
            when(userAddressRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userAddressService.getAddress(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.ADDRESS_NOT_FOUND);
        }

        @Test
        void 본인의_주소가_아니면_예외가_발생한다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act & assert
            assertThatThrownBy(() -> userAddressService.getAddress(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(UserAddressErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_주소를_반환한다() {
            // arrange
            UserAddress address = UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
            when(userAddressRepository.findById(1L)).thenReturn(Optional.of(address));

            // act
            UserAddress result = userAddressService.getAddress(1L, 1L);

            // assert
            assertThat(result.getReceiverName()).isEqualTo("홍길동");
        }
    }

    @DisplayName("배송지 목록을 조회할 때,")
    @Nested
    class 목록조회 {

        @Test
        void 사용자의_배송지_목록이_반환된다() {
            // arrange
            List<UserAddress> addresses = List.of(
                    UserAddress.create(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                    UserAddress.create(1L, "김철수", "010-9876-5432", "54321", "부산시", null)
            );
            when(userAddressRepository.findAllByUserIdAndDeletedAtIsNull(1L)).thenReturn(addresses);

            // act
            List<UserAddress> result = userAddressService.getAddresses(1L);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 배송지가_없으면_빈_리스트가_반환된다() {
            // arrange
            when(userAddressRepository.findAllByUserIdAndDeletedAtIsNull(1L)).thenReturn(List.of());

            // act
            List<UserAddress> result = userAddressService.getAddresses(1L);

            // assert
            assertThat(result).isEmpty();
        }
    }
}
