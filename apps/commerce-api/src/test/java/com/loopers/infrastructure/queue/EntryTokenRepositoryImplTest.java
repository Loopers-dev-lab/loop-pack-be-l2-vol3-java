package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenConsumeResult;
import com.loopers.domain.queue.EntryTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntryTokenRepositoryImplTest {

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("entry-token:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("queue-status:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("lock:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    @DisplayName("토큰을 저장할 때, ")
    @Nested
    class Save {

        @DisplayName("저장 후 조회할 수 있다.")
        @Test
        void savesAndFinds_token() {
            EntryToken token = new EntryToken(1L, "test-token", 1000L);

            entryTokenRepository.save(token, 300);

            Optional<EntryToken> found = entryTokenRepository.findByUserId(1L);
            assertThat(found).isPresent();
            assertThat(found.get().token()).isEqualTo("test-token");
            assertThat(found.get().activateAt()).isEqualTo(1000L);
        }

        @DisplayName("활성 토큰 카운트가 증가한다.")
        @Test
        void incrementsActiveTokenCount() {
            entryTokenRepository.save(new EntryToken(1L, "t1", 1000L), 300);
            entryTokenRepository.save(new EntryToken(2L, "t2", 1000L), 300);

            assertThat(entryTokenRepository.countActiveTokens()).isEqualTo(2);
        }

        @DisplayName("TTL이 만료되면, 활성 토큰 카운트에서 자동으로 제외된다.")
        @Test
        void excludesExpiredTokens_fromActiveCount() throws InterruptedException {
            entryTokenRepository.save(new EntryToken(1L, "t1", 1000L), 1);
            entryTokenRepository.save(new EntryToken(2L, "t2", 1000L), 1);

            Thread.sleep(2000);

            assertThat(entryTokenRepository.countActiveTokens()).isZero();
        }
    }

    @DisplayName("토큰을 GETDEL할 때, ")
    @Nested
    class FindAndDelete {

        @DisplayName("토큰이 있으면, 반환하고 삭제한다.")
        @Test
        void returnsAndDeletes_whenTokenExists() {
            entryTokenRepository.save(new EntryToken(1L, "test-token", 1000L), 300);

            Optional<EntryToken> result = entryTokenRepository.findAndDeleteByUserId(1L);

            assertThat(result).isPresent();
            assertThat(result.get().token()).isEqualTo("test-token");
            assertThat(entryTokenRepository.findByUserId(1L)).isEmpty();
            assertThat(entryTokenRepository.countActiveTokens()).isZero();
        }

        @DisplayName("토큰 삭제 시 queue-status도 함께 삭제된다.")
        @Test
        void deletesStatus_whenTokenDeleted() {
            entryTokenRepository.save(new EntryToken(1L, "test-token", 1000L), 300);
            entryTokenRepository.saveStatus(1L, "TOKEN_ISSUED", 420);

            entryTokenRepository.findAndDeleteByUserId(1L);

            assertThat(entryTokenRepository.getStatus(1L)).isEmpty();
        }

        @DisplayName("토큰이 없으면, empty를 반환한다.")
        @Test
        void returnsEmpty_whenNoToken() {
            Optional<EntryToken> result = entryTokenRepository.findAndDeleteByUserId(999L);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("토큰을 복원할 때, ")
    @Nested
    class Restore {

        @DisplayName("삭제된 토큰을 다시 저장한다.")
        @Test
        void restoresDeletedToken() {
            EntryToken token = new EntryToken(1L, "test-token", 1000L);
            entryTokenRepository.save(token, 300);
            entryTokenRepository.findAndDeleteByUserId(1L);

            entryTokenRepository.restore(token, 300, 600);

            assertThat(entryTokenRepository.findByUserId(1L)).isPresent();
            assertThat(entryTokenRepository.countActiveTokens()).isEqualTo(1);
        }
    }

    @DisplayName("상태를 저장하고 조회할 때, ")
    @Nested
    class Status {

        @DisplayName("저장한 상태를 조회할 수 있다.")
        @Test
        void savesAndGetsStatus() {
            entryTokenRepository.saveStatus(1L, "TOKEN_ISSUED", 420);

            Optional<String> status = entryTokenRepository.getStatus(1L);

            assertThat(status).isPresent();
            assertThat(status.get()).isEqualTo("TOKEN_ISSUED");
        }

        @DisplayName("상태가 없으면, empty를 반환한다.")
        @Test
        void returnsEmpty_whenNoStatus() {
            assertThat(entryTokenRepository.getStatus(999L)).isEmpty();
        }
    }

    @DisplayName("토큰을 조건부 소비할 때, ")
    @Nested
    class ConsumeIfActivated {

        @DisplayName("activateAt에 도달했으면, 토큰을 삭제하고 consumed=true를 반환한다.")
        @Test
        void consumesToken_whenActivated() {
            long pastActivateAt = System.currentTimeMillis() - 1000;
            entryTokenRepository.save(new EntryToken(1L, "test-token", pastActivateAt), 300);
            entryTokenRepository.saveStatus(1L, "TOKEN_ISSUED", 420);

            Optional<EntryTokenConsumeResult> result = entryTokenRepository.consumeIfActivated(1L, System.currentTimeMillis());

            assertThat(result).isPresent();
            assertThat(result.get().consumed()).isTrue();
            assertThat(result.get().token().token()).isEqualTo("test-token");
            assertThat(entryTokenRepository.findByUserId(1L)).isEmpty();
            assertThat(entryTokenRepository.getStatus(1L)).isEmpty();
        }

        @DisplayName("activateAt에 도달하지 않았으면, 토큰을 유지하고 consumed=false를 반환한다.")
        @Test
        void doesNotConsumeToken_whenNotActivated() {
            long futureActivateAt = System.currentTimeMillis() + 60_000;
            entryTokenRepository.save(new EntryToken(1L, "test-token", futureActivateAt), 300);
            entryTokenRepository.saveStatus(1L, "TOKEN_ISSUED", 420);

            Optional<EntryTokenConsumeResult> result = entryTokenRepository.consumeIfActivated(1L, System.currentTimeMillis());

            assertThat(result).isPresent();
            assertThat(result.get().consumed()).isFalse();
            assertThat(result.get().token().token()).isEqualTo("test-token");
            assertThat(entryTokenRepository.findByUserId(1L)).isPresent();
            assertThat(entryTokenRepository.getStatus(1L)).isPresent();
        }

        @DisplayName("토큰이 없으면, empty를 반환한다.")
        @Test
        void returnsEmpty_whenNoToken() {
            Optional<EntryTokenConsumeResult> result = entryTokenRepository.consumeIfActivated(999L, System.currentTimeMillis());

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("분산 락을 획득할 때, ")
    @Nested
    class AcquireLock {

        @DisplayName("락이 없으면, 획득 성공하고 lockValue를 반환한다.")
        @Test
        void acquiresLock_whenNotLocked() {
            Optional<String> result = entryTokenRepository.acquireLock("lock:test", 5000);

            assertThat(result).isPresent();
            assertThat(result.get()).isNotBlank();
        }

        @DisplayName("이미 락이 있으면, 획득 실패한다.")
        @Test
        void failsToAcquire_whenAlreadyLocked() {
            entryTokenRepository.acquireLock("lock:test", 5000);

            Optional<String> result = entryTokenRepository.acquireLock("lock:test", 5000);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("분산 락을 해제할 때, ")
    @Nested
    class ReleaseLock {

        @DisplayName("소유자가 일치하면, 락을 해제한다.")
        @Test
        void releasesLock_whenOwnerMatches() {
            Optional<String> lockValue = entryTokenRepository.acquireLock("lock:test", 5000);
            assertThat(lockValue).isPresent();

            entryTokenRepository.releaseLock("lock:test", lockValue.get());

            Optional<String> reacquired = entryTokenRepository.acquireLock("lock:test", 5000);
            assertThat(reacquired).isPresent();
        }

        @DisplayName("소유자가 일치하지 않으면, 락을 해제하지 않는다.")
        @Test
        void doesNotRelease_whenOwnerDoesNotMatch() {
            entryTokenRepository.acquireLock("lock:test", 5000);

            entryTokenRepository.releaseLock("lock:test", "wrong-value");

            Optional<String> result = entryTokenRepository.acquireLock("lock:test", 5000);
            assertThat(result).isEmpty();
        }
    }
}
