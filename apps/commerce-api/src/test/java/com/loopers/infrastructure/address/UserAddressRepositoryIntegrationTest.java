package com.loopers.infrastructure.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserAddressRepositoryIntegrationTest {

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_배송지를_저장하면_ID가_생성된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층");

            // act
            UserAddress saved = userAddressRepository.save(address);

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_배송지의_필드가_올바르게_저장된다() {
            // arrange
            UserAddress address = UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층");

            // act
            UserAddress saved = userAddressRepository.save(address);

            // assert
            assertThat(saved)
                    .extracting(UserAddress::getUserId, UserAddress::getReceiverName,
                            UserAddress::getPhone, UserAddress::getZipCode,
                            UserAddress::getAddressLine1, UserAddress::getAddressLine2)
                    .containsExactly(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "4층");
        }
    }

    @Nested
    @DisplayName("findById 메서드는")
    class FindById {

        @Test
        void 존재하는_배송지를_반환한다() {
            // arrange
            UserAddress saved = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null));

            // act
            Optional<UserAddress> result = userAddressRepository.findById(saved.getId());

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 소프트_삭제된_배송지는_조회되지_않는다() {
            // arrange
            UserAddress saved = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", null));
            saved.remove();
            userAddressRepository.save(saved);

            // act
            Optional<UserAddress> result = userAddressRepository.findById(saved.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByUserIdAndDeletedAtIsNull 메서드는")
    class CountByUserId {

        @Test
        void 활성_배송지_수를_반환한다() {
            // arrange
            userAddressRepository.save(UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            userAddressRepository.save(UserAddress.register(1L, "김철수", "010-9876-5432", "54321", "부산시", null));

            // act
            long count = userAddressRepository.countByUserIdAndDeletedAtIsNull(1L);

            // assert
            assertThat(count).isEqualTo(2);
        }

        @Test
        void 삭제된_배송지는_카운트에서_제외된다() {
            // arrange
            UserAddress addr1 = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            userAddressRepository.save(UserAddress.register(1L, "김철수", "010-9876-5432", "54321", "부산시", null));
            addr1.remove();
            userAddressRepository.save(addr1);

            // act
            long count = userAddressRepository.countByUserIdAndDeletedAtIsNull(1L);

            // assert
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findAllByUserIdAndDeletedAtIsNull 메서드는")
    class FindAllByUserId {

        @Test
        void 사용자의_활성_배송지_목록을_반환한다() {
            // arrange
            userAddressRepository.save(UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            userAddressRepository.save(UserAddress.register(1L, "김철수", "010-9876-5432", "54321", "부산시", null));

            // act
            List<UserAddress> result = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(1L);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 삭제된_배송지는_제외된다() {
            // arrange
            UserAddress addr1 = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            userAddressRepository.save(UserAddress.register(1L, "김철수", "010-9876-5432", "54321", "부산시", null));
            addr1.remove();
            userAddressRepository.save(addr1);

            // act
            List<UserAddress> result = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(1L);

            // assert
            assertThat(result).hasSize(1);
        }

        @Test
        void 다른_사용자의_배송지는_포함되지_않는다() {
            // arrange
            userAddressRepository.save(UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            userAddressRepository.save(UserAddress.register(2L, "김철수", "010-9876-5432", "54321", "부산시", null));

            // act
            List<UserAddress> result = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(1L);

            // assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findFirstByUserIdAndDeletedAtIsNullAndIdNot 메서드는")
    class FindFirstExcluding {

        @Test
        void 지정_ID를_제외한_첫_번째_배송지를_반환한다() {
            // arrange
            UserAddress addr1 = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));
            UserAddress addr2 = userAddressRepository.save(
                    UserAddress.register(1L, "김철수", "010-9876-5432", "54321", "부산시", null));

            // act
            Optional<UserAddress> result = userAddressRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNot(
                    1L, addr1.getId());

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(addr2.getId());
        }

        @Test
        void 다른_배송지가_없으면_empty를_반환한다() {
            // arrange
            UserAddress addr1 = userAddressRepository.save(
                    UserAddress.register(1L, "홍길동", "010-1234-5678", "12345", "서울시", null));

            // act
            Optional<UserAddress> result = userAddressRepository.findFirstByUserIdAndDeletedAtIsNullAndIdNot(
                    1L, addr1.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }
}
