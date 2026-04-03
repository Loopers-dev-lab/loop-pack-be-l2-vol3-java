package com.loopers.application.order.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionStrategyComparisonTest {

    @Nested
    @DisplayName("A key TTL 전략")
    class KeyTtlStrategyTest extends StrategyContractTest {
        @Override
        AdmissionStrategyHarness harness() {
            return new KeyTtlHarness();
        }
    }

    @Nested
    @DisplayName("B hash field TTL 전략")
    class HashFieldTtlStrategyTest extends StrategyContractTest {
        @Override
        AdmissionStrategyHarness harness() {
            return new HashFieldTtlHarness();
        }
    }

    @Nested
    @DisplayName("C hybrid 전략")
    class HybridStrategyTest extends StrategyContractTest {
        @Override
        AdmissionStrategyHarness harness() {
            return new HybridHarness();
        }
    }

    @Test
    @DisplayName("기본 admission 전략은 hash-field-ttl이다")
    void defaultAdmissionStrategyIsHashFieldTtl() {
        OrderQueueProperties properties = new OrderQueueProperties(
                false, 1L, 1, 1L, true, 1000L, 10, 100, true, 5000L, 30000L, "hash-field-ttl", 1L, 5L
        );

        assertThat(properties.admissionStrategy()).isEqualTo("hash-field-ttl");
    }

    abstract static class StrategyContractTest {

        abstract AdmissionStrategyHarness harness();

        @Test
        @DisplayName("동일 사용자의 중복 claim을 막는다")
        void preventsDuplicateClaim() {
            AdmissionStrategyHarness harness = harness();

            assertThat(harness.tryClaim("user-1", 1_000L, 5_000L)).isTrue();
            assertThat(harness.tryClaim("user-1", 1_500L, 5_000L)).isFalse();
        }

        @Test
        @DisplayName("토큰 발급 후 유효 토큰을 조회할 수 있다")
        void issuesAndReadsToken() {
            AdmissionStrategyHarness harness = harness();

            assertThat(harness.issueToken("user-1", 1_000L, 30_000L)).isTrue();
            assertThat(harness.hasValidToken("user-1", 1_100L)).isTrue();
            assertThat(harness.countActiveTokens(1_100L)).isEqualTo(1L);
        }

        @Test
        @DisplayName("TTL이 지나면 claim과 token이 만료된다")
        void expiresClaimAndToken() {
            AdmissionStrategyHarness harness = harness();

            harness.tryClaim("user-1", 1_000L, 100L);
            harness.issueToken("user-1", 1_000L, 100L);

            assertThat(harness.hasActiveClaim("user-1", 1_050L)).isTrue();
            assertThat(harness.hasValidToken("user-1", 1_050L)).isTrue();

            assertThat(harness.hasActiveClaim("user-1", 1_101L)).isFalse();
            assertThat(harness.hasValidToken("user-1", 1_101L)).isFalse();
            assertThat(harness.countActiveTokens(1_101L)).isZero();
        }

        @Test
        @DisplayName("토큰 제거 시 active count도 함께 줄어든다")
        void removesTokenAndActiveCount() {
            AdmissionStrategyHarness harness = harness();

            harness.issueToken("user-1", 1_000L, 30_000L);
            harness.issueToken("user-2", 1_000L, 30_000L);

            assertThat(harness.countActiveTokens(1_100L)).isEqualTo(2L);
            harness.removeToken("user-1");
            assertThat(harness.hasValidToken("user-1", 1_100L)).isFalse();
            assertThat(harness.countActiveTokens(1_100L)).isEqualTo(1L);
        }
    }

    interface AdmissionStrategyHarness {
        boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis);

        boolean hasValidToken(String memberId, long nowMillis);

        boolean hasActiveClaim(String memberId, long nowMillis);

        boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis);

        long countActiveTokens(long nowMillis);

        void removeToken(String memberId);
    }

    static final class KeyTtlHarness implements AdmissionStrategyHarness {
        private final Map<String, Long> claims = new HashMap<>();
        private final Map<String, Long> tokens = new HashMap<>();

        @Override
        public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
            evictExpired(claims, nowMillis);
            if (claims.containsKey(memberId)) {
                return false;
            }
            claims.put(memberId, nowMillis + claimTtlMillis);
            return true;
        }

        @Override
        public boolean hasValidToken(String memberId, long nowMillis) {
            evictExpired(tokens, nowMillis);
            return tokens.containsKey(memberId);
        }

        @Override
        public boolean hasActiveClaim(String memberId, long nowMillis) {
            evictExpired(claims, nowMillis);
            return claims.containsKey(memberId);
        }

        @Override
        public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
            evictExpired(tokens, nowMillis);
            return tokens.putIfAbsent(memberId, nowMillis + tokenTtlMillis) == null;
        }

        @Override
        public long countActiveTokens(long nowMillis) {
            evictExpired(tokens, nowMillis);
            return tokens.size();
        }

        @Override
        public void removeToken(String memberId) {
            tokens.remove(memberId);
        }
    }

    static final class HashFieldTtlHarness implements AdmissionStrategyHarness {
        private final Map<String, Long> claimBucket = new HashMap<>();
        private final Map<String, Long> tokenBucket = new HashMap<>();

        @Override
        public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
            evictExpired(claimBucket, nowMillis);
            if (claimBucket.containsKey(memberId)) {
                return false;
            }
            claimBucket.put(memberId, nowMillis + claimTtlMillis);
            return true;
        }

        @Override
        public boolean hasValidToken(String memberId, long nowMillis) {
            evictExpired(tokenBucket, nowMillis);
            return tokenBucket.containsKey(memberId);
        }

        @Override
        public boolean hasActiveClaim(String memberId, long nowMillis) {
            evictExpired(claimBucket, nowMillis);
            return claimBucket.containsKey(memberId);
        }

        @Override
        public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
            evictExpired(tokenBucket, nowMillis);
            if (tokenBucket.containsKey(memberId)) {
                return false;
            }
            tokenBucket.put(memberId, nowMillis + tokenTtlMillis);
            return true;
        }

        @Override
        public long countActiveTokens(long nowMillis) {
            evictExpired(tokenBucket, nowMillis);
            return tokenBucket.size();
        }

        @Override
        public void removeToken(String memberId) {
            tokenBucket.remove(memberId);
        }
    }

    static final class HybridHarness implements AdmissionStrategyHarness {
        private final Map<String, Long> claims = new HashMap<>();
        private final Map<String, Long> tokenExpiry = new HashMap<>();
        private final Set<String> activeTokenMembers = new HashSet<>();

        @Override
        public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
            evictExpired(claims, nowMillis);
            if (claims.containsKey(memberId)) {
                return false;
            }
            claims.put(memberId, nowMillis + claimTtlMillis);
            return true;
        }

        @Override
        public boolean hasValidToken(String memberId, long nowMillis) {
            evictExpiredTokens(nowMillis);
            return tokenExpiry.containsKey(memberId);
        }

        @Override
        public boolean hasActiveClaim(String memberId, long nowMillis) {
            evictExpired(claims, nowMillis);
            return claims.containsKey(memberId);
        }

        @Override
        public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
            evictExpiredTokens(nowMillis);
            if (tokenExpiry.containsKey(memberId)) {
                return false;
            }
            tokenExpiry.put(memberId, nowMillis + tokenTtlMillis);
            activeTokenMembers.add(memberId);
            return true;
        }

        @Override
        public long countActiveTokens(long nowMillis) {
            evictExpiredTokens(nowMillis);
            return activeTokenMembers.size();
        }

        @Override
        public void removeToken(String memberId) {
            tokenExpiry.remove(memberId);
            activeTokenMembers.remove(memberId);
        }

        private void evictExpiredTokens(long nowMillis) {
            tokenExpiry.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue() <= nowMillis;
                if (expired) {
                    activeTokenMembers.remove(entry.getKey());
                }
                return expired;
            });
        }
    }

    private static void evictExpired(Map<String, Long> bucket, long nowMillis) {
        bucket.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
    }
}
