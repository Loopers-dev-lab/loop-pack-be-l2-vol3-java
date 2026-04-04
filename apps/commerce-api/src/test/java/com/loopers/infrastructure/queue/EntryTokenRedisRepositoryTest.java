package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("EntryTokenRedisRepository 통합 테스트")
class EntryTokenRedisRepositoryTest {

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("issue()")
    class IssueTest {

        @Test
        @DisplayName("토큰 발급 후 조회 가능")
        void issue_thenFindable() {
            // given
            Long memberId = 1L;
            String token = "test-token-uuid";

            // when
            entryTokenRepository.issue(memberId, token, 300);

            // then
            Optional<String> found = entryTokenRepository.findToken(memberId);
            assertThat(found).isPresent().hasValue(token);
        }

        @Test
        @DisplayName("동일 memberId로 재발급 시 기존 토큰 덮어씀")
        void issue_overwritesPreviousToken() {
            // given
            Long memberId = 1L;
            entryTokenRepository.issue(memberId, "old-token", 300);

            // when
            entryTokenRepository.issue(memberId, "new-token", 300);

            // then
            Optional<String> found = entryTokenRepository.findToken(memberId);
            assertThat(found).hasValue("new-token");
        }
    }

    @Nested
    @DisplayName("findToken()")
    class FindTokenTest {

        @Test
        @DisplayName("미발급 memberId는 empty 반환")
        void findToken_notIssued_returnsEmpty() {
            // when
            Optional<String> result = entryTokenRepository.findToken(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("consume()")
    class ConsumeTest {

        @Test
        @DisplayName("토큰 소비 후 조회 시 empty 반환")
        void consume_thenNotFindable() {
            // given
            Long memberId = 1L;
            entryTokenRepository.issue(memberId, "test-token", 300);

            // when
            entryTokenRepository.consume(memberId);

            // then
            assertThat(entryTokenRepository.findToken(memberId)).isEmpty();
        }

        @Test
        @DisplayName("미발급 memberId consume 시 에러 없음")
        void consume_notIssued_noError() {
            // when & then — 예외 없이 정상 처리
            entryTokenRepository.consume(999L);
        }
    }

    @Nested
    @DisplayName("TTL")
    class TtlTest {

        @Test
        @DisplayName("TTL 만료 후 토큰 자동 삭제")
        void ttlExpiry_tokenAutoDeleted() throws InterruptedException {
            // given
            Long memberId = 1L;
            entryTokenRepository.issue(memberId, "expiring-token", 1);

            // when
            assertThat(entryTokenRepository.findToken(memberId)).isPresent();
            Thread.sleep(1500);

            // then
            assertThat(entryTokenRepository.findToken(memberId)).isEmpty();
        }
    }
}
