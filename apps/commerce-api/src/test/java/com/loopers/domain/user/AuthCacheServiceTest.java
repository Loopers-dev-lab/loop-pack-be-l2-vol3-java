package com.loopers.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("AuthCacheService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthCacheServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplateMaster;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserService userService;

    private AuthCacheService authCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authCacheService = new AuthCacheService(redisTemplateMaster, userService, objectMapper);
    }

    // --- 캐시 미스 → DB 인증 ---

    @Test
    @DisplayName("캐시 미스 시 DB 인증 후 캐시 저장")
    void authenticate_CacheMiss_ShouldQueryDBAndCache() {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // 캐시 미스

        UserModel mockUser = createMockUser(1L, "testuser", "테스트유저");
        given(userService.authenticate("testuser", "password123")).willReturn(mockUser);

        // when
        UserModel result = authCacheService.authenticateWithCache("testuser", "password123");

        // then
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(userService).authenticate("testuser", "password123");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(300)));
    }

    // --- 캐시 히트 → DB 조회 없이 캐시에서 복원 ---

    @Test
    @DisplayName("캐시 히트 시 DB 조회 없이 캐시 데이터로 UserModel을 복원한다")
    void authenticate_CacheHit_ShouldRestoreFromCacheWithoutDB() throws Exception {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        String cachedJson = objectMapper.writeValueAsString(
                new AuthCacheService.AuthUserInfo(1L, "testuser", "테스트유저",
                        "19900101", "test@test.com", "서울시"));
        given(valueOperations.get(anyString())).willReturn(cachedJson);

        // when
        UserModel result = authCacheService.authenticateWithCache("testuser", "password123");

        // then — DB 조회 없이 캐시 데이터로 복원
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getLoginId()).isEqualTo("testuser");
        assertThat(result.getUserName()).isEqualTo("테스트유저");
        assertThat(result.getBirthday()).isEqualTo("19900101");
        assertThat(result.getEmail()).isEqualTo("test@test.com");
        assertThat(result.getAddress()).isEqualTo("서울시");
        verify(userService, never()).findByUserId(any());       // DB PK 조회 안 함
        verify(userService, never()).authenticate(any(), any()); // DB 인증 안 함
    }

    // --- 인증 실패 → 캐시 안 함 ---

    @Test
    @DisplayName("인증 실패 시 캐시 저장하지 않음")
    void authenticate_WrongPassword_ShouldNotCache() {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(userService.authenticate("testuser", "wrongpw"))
                .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> authCacheService.authenticateWithCache("testuser", "wrongpw"))
                .isInstanceOf(CoreException.class);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // --- Redis 장애 → DB fallback ---

    @Test
    @DisplayName("Redis 조회 실패 시 DB fallback으로 인증")
    void authenticate_RedisDown_ShouldFallbackToDB() {
        // given
        given(redisTemplateMaster.opsForValue()).willThrow(new RuntimeException("Redis connection refused"));

        UserModel mockUser = createMockUser(1L, "testuser", "테스트유저");
        given(userService.authenticate("testuser", "password123")).willReturn(mockUser);

        // when
        UserModel result = authCacheService.authenticateWithCache("testuser", "password123");

        // then
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(userService).authenticate("testuser", "password123");
    }

    // --- 비밀번호 변경 → 캐시 미스 ---

    @Test
    @DisplayName("비밀번호 변경 시 다른 passwordHash → 캐시 미스")
    void authenticate_PasswordChanged_ShouldMissCache() {
        // given: 구 비밀번호로 캐시된 상태
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // 다른 해시 → 미스

        UserModel mockUser = createMockUser(1L, "testuser", "테스트유저");
        given(userService.authenticate("testuser", "newPassword")).willReturn(mockUser);

        // when
        UserModel result = authCacheService.authenticateWithCache("testuser", "newPassword");

        // then — DB 인증이 수행됨
        verify(userService).authenticate("testuser", "newPassword");
    }

    // --- 헬퍼 ---

    private UserModel createMockUser(Long userId, String loginId, String userName) {
        UserModel user = mock(UserModel.class);
        lenient().when(user.getUserId()).thenReturn(userId);
        lenient().when(user.getLoginId()).thenReturn(loginId);
        lenient().when(user.getUserName()).thenReturn(userName);
        lenient().when(user.getBirthday()).thenReturn("19900101");
        lenient().when(user.getEmail()).thenReturn("test@test.com");
        lenient().when(user.getAddress()).thenReturn("서울시");
        return user;
    }
}
