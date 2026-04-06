package com.loopers.application.queue;

import com.loopers.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("TokenService 테스트")
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        Set<String> tokenKeys = redisTemplate.keys(queueProperties.tokenPrefix() + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("토큰 발급")
    class IssueToken {

        @Test
        @DisplayName("유저에게 토큰을 발급한다")
        void 토큰_발급_성공() {
            // Arrange
            Long userId = 1L;

            // Act
            String token = tokenService.issueToken(userId);

            // Assert
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();

            String storedToken = redisTemplate.opsForValue().get(queueProperties.tokenPrefix() + ":" + userId);
            assertThat(storedToken).isEqualTo(token);
        }

        @Test
        @DisplayName("발급된 토큰에 TTL이 설정된다")
        void 토큰_TTL_설정() {
            // Arrange
            Long userId = 1L;

            // Act
            tokenService.issueToken(userId);

            // Assert
            String key = queueProperties.tokenPrefix() + ":" + userId;
            Long ttl = redisTemplate.getExpire(key);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(queueProperties.tokenTtlSeconds());
        }

        @Test
        @DisplayName("같은 유저에게 토큰을 다시 발급하면 기존 토큰이 교체된다")
        void 토큰_재발급() {
            // Arrange
            Long userId = 1L;
            String firstToken = tokenService.issueToken(userId);

            // Act
            String secondToken = tokenService.issueToken(userId);

            // Assert
            assertThat(secondToken).isNotEqualTo(firstToken);

            String storedToken = redisTemplate.opsForValue().get(queueProperties.tokenPrefix() + ":" + userId);
            assertThat(storedToken).isEqualTo(secondToken);
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰은 검증에 성공한다")
        void 유효한_토큰_검증_성공() {
            // Arrange
            Long userId = 1L;
            String token = tokenService.issueToken(userId);

            // Act
            boolean result = tokenService.validateToken(userId, token);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("잘못된 토큰은 검증에 실패한다")
        void 잘못된_토큰_검증_실패() {
            // Arrange
            Long userId = 1L;
            tokenService.issueToken(userId);

            // Act
            boolean result = tokenService.validateToken(userId, "wrong-token");

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("토큰이 없는 유저는 검증에 실패한다")
        void 토큰_없는_유저_검증_실패() {
            // Arrange
            Long userId = 999L;

            // Act
            boolean result = tokenService.validateToken(userId, "any-token");

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 토큰은 검증에 실패한다")
        void null_토큰_검증_실패() {
            // Arrange
            Long userId = 1L;
            tokenService.issueToken(userId);

            // Act
            boolean result = tokenService.validateToken(userId, null);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 삭제")
    class DeleteToken {

        @Test
        @DisplayName("토큰을 삭제하면 검증에 실패한다")
        void 토큰_삭제_후_검증_실패() {
            // Arrange
            Long userId = 1L;
            String token = tokenService.issueToken(userId);

            // Act
            tokenService.deleteToken(userId);

            // Assert
            boolean result = tokenService.validateToken(userId, token);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 존재 여부")
    class HasToken {

        @Test
        @DisplayName("토큰이 있으면 true를 반환한다")
        void 토큰_있음() {
            // Arrange
            Long userId = 1L;
            tokenService.issueToken(userId);

            // Act
            boolean result = tokenService.hasToken(userId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("토큰이 없으면 false를 반환한다")
        void 토큰_없음() {
            // Arrange
            Long userId = 999L;

            // Act
            boolean result = tokenService.hasToken(userId);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 조회")
    class GetToken {

        @Test
        @DisplayName("토큰이 있으면 Optional로 반환한다")
        void 토큰_조회_성공() {
            // Arrange
            Long userId = 1L;
            String issuedToken = tokenService.issueToken(userId);

            // Act
            Optional<String> result = tokenService.getToken(userId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(issuedToken);
        }

        @Test
        @DisplayName("토큰이 없으면 빈 Optional을 반환한다")
        void 토큰_조회_없음() {
            // Arrange
            Long userId = 999L;

            // Act
            Optional<String> result = tokenService.getToken(userId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("토큰 만료")
    class TokenExpiration {

        @Test
        @DisplayName("TTL이 지나면 토큰이 자동으로 삭제되어 검증에 실패한다")
        void 토큰_TTL_만료_후_검증_실패() {
            // Arrange
            Long userId = 1L;
            String token = tokenService.issueToken(userId);
            String key = queueProperties.tokenPrefix() + ":" + userId;

            // 토큰의 TTL을 1초로 강제 설정
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);

            // Act & Assert
            await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean result = tokenService.validateToken(userId, token);
                    assertThat(result).isFalse();
                });
        }

        @Test
        @DisplayName("TTL이 지나면 hasToken이 false를 반환한다")
        void 토큰_TTL_만료_후_hasToken_false() {
            // Arrange
            Long userId = 1L;
            tokenService.issueToken(userId);
            String key = queueProperties.tokenPrefix() + ":" + userId;

            // 토큰의 TTL을 1초로 강제 설정
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);

            // Act & Assert
            await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    boolean result = tokenService.hasToken(userId);
                    assertThat(result).isFalse();
                });
        }
    }
}
