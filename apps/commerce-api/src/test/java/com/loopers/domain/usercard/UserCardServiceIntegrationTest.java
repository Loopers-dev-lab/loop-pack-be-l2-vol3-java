package com.loopers.domain.usercard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.payment.CardType;
import com.loopers.infrastructure.usercard.UserCardJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserCardServiceIntegrationTest {

    @Autowired
    private UserCardService userCardService;

    @Autowired
    private UserCardJpaRepository userCardJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("saveCard")
    @Nested
    class SaveCard {

        @DisplayName("기본 카드가 없을 때 저장하면, 기본 카드로 설정된다.")
        @Test
        void savesAsDefault_whenNoDefaultCardExists() {
            // arrange
            Long userId = 1L;

            // act
            UserCard saved = userCardService.saveCard(userId, CardType.SAMSUNG, "1234-5678-9012-3456", false);

            // assert
            assertAll(
                () -> assertThat(saved.isDefault()).isTrue(),
                () -> assertThat(userCardJpaRepository.findByUserIdAndIsDefaultTrue(userId)).isPresent()
            );
        }

        @DisplayName("기본 카드가 있고 updateDefaultCard = true이면, 새 카드가 기본 카드가 되고 기존 카드는 해제된다.")
        @Test
        void updatesDefault_whenUpdateDefaultCardIsTrue() {
            // arrange
            Long userId = 1L;
            UserCard existingDefault = userCardService.saveCard(userId, CardType.SAMSUNG, "1111-1111-1111-1111", false);

            // act
            UserCard newCard = userCardService.saveCard(userId, CardType.HYUNDAI, "2222-2222-2222-2222", true);

            // assert
            assertAll(
                () -> assertThat(newCard.isDefault()).isTrue(),
                () -> assertThat(userCardJpaRepository.findById(existingDefault.getId()))
                    .isPresent()
                    .get()
                    .satisfies(card -> assertThat(card.isDefault()).isFalse())
            );
        }

        @DisplayName("기본 카드가 있고 updateDefaultCard = false이면, 새 카드는 기본 카드가 아니고 기존 기본 카드는 유지된다.")
        @Test
        void doesNotChangeDefault_whenUpdateDefaultCardIsFalse() {
            // arrange
            Long userId = 1L;
            UserCard existingDefault = userCardService.saveCard(userId, CardType.SAMSUNG, "1111-1111-1111-1111", false);

            // act
            UserCard newCard = userCardService.saveCard(userId, CardType.HYUNDAI, "2222-2222-2222-2222", false);

            // assert
            assertAll(
                () -> assertThat(newCard.isDefault()).isFalse(),
                () -> assertThat(userCardJpaRepository.findById(existingDefault.getId()))
                    .isPresent()
                    .get()
                    .satisfies(card -> assertThat(card.isDefault()).isTrue())
            );
        }
    }
}