package com.loopers.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.utils.RedisCleanUp;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EntryTokenRedisStoreTest {

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("토큰 저장 후 TTL이 만료되면, 토큰이 무효화된다")
    void token_expires_after_ttl() throws InterruptedException {
        Long userId = 1L;
        EntryToken token = EntryToken.issue(userId);
        entryTokenRepository.save(token);

        // test 프로파일에서 TTL = 1초
        Thread.sleep(2000);

        Optional<EntryToken> result = entryTokenRepository.findByUserId(userId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("토큰 저장 후 TTL 이내에 조회하면, 토큰이 반환된다")
    void token_exists_within_ttl() {
        Long userId = 2L;
        EntryToken token = EntryToken.issue(userId);
        entryTokenRepository.save(token);

        Optional<EntryToken> result = entryTokenRepository.findByUserId(userId);
        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo(token.getToken());
    }
}
