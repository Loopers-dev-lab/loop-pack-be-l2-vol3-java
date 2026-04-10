package com.loopers.infrastructure.waitingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.waitingroom.EntryGate;
import com.loopers.domain.waitingroom.WaitingQueue;
import com.loopers.domain.waitingroom.WaitingRoomErrorCode;
import com.loopers.support.error.CoreException;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@DisplayName("WaitingRoom Redis 통합 테스트 (TTL 검증)")
@SpringBootTest
@TestPropertySource(properties = "queue.token.ttl-seconds=3")
class WaitingRoomRedisStoreIntegrationTest {

    @Autowired
    private WaitingQueue waitingQueue;

    @Autowired
    private EntryGate entryGate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("(4-2) 토큰 TTL 검증")
    @Nested
    class TokenTtl {

        @DisplayName("토큰 발급 후 TTL 이내에는 토큰이 존재한다.")
        @Test
        void token_existsBeforeTtl() {
            waitingQueue.enter(1L);
            entryGate.issueToken(1L);
            assertThat(entryGate.getToken(1L)).isNotNull();
        }

        @DisplayName("토큰 TTL(2초) 초과 시 토큰이 자동 삭제된다.")
        @Test
        void token_expiresAfterTtl() throws InterruptedException {
            waitingQueue.enter(1L);
            entryGate.issueToken(1L);
            assertThat(entryGate.getToken(1L)).isNotNull();

            Thread.sleep(4000);

            assertThat(entryGate.getToken(1L)).isNull();
        }
    }

    @DisplayName("(5a-3, 5b-2) 만료 토큰 검증 거부")
    @Nested
    class ExpiredTokenValidation {

        @DisplayName("만료된 토큰으로 검증하면 INVALID_TOKEN 예외가 발생한다.")
        @Test
        void validateToken_afterExpiry_throwsException() throws InterruptedException {
            waitingQueue.enter(1L);
            String token = entryGate.issueToken(1L);

            Thread.sleep(4000);

            assertThatThrownBy(() -> entryGate.validateToken(1L, token))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(WaitingRoomErrorCode.INVALID_TOKEN));
        }
    }

    @DisplayName("(5b-1) TTL 만료 시 자동 삭제 확인")
    @Nested
    class AutoDeletion {

        @DisplayName("TTL 만료 후 GET 결과가 null이다.")
        @Test
        void token_isNullAfterExpiry() throws InterruptedException {
            waitingQueue.enter(1L);
            entryGate.issueToken(1L);

            Thread.sleep(4000);

            assertAll(
                    () -> assertThat(entryGate.getToken(1L)).isNull(),
                    () -> assertThat(waitingQueue.getRank(1L)).isNull());
        }
    }
}
