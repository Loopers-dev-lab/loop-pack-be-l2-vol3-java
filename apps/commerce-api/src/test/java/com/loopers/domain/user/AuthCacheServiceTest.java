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
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));
    }

    // --- 캐시 히트 → DB PK 조회만 ---

    @Test
    @DisplayName("캐시 히트 시 DB authenticate() 호출하지 않고 findByUserId()만 호출")
    void authenticate_CacheHit_ShouldNotCallAuthenticate() throws Exception {
        // given
        given(redisTemplateMaster.opsForValue()).willReturn(valueOperations);
        String cachedJson = objectMapper.writeValueAsString(
                new AuthCacheService.AuthUserInfo(1L, "testuser", "테스트유저"));
        given(valueOperations.get(anyString())).willReturn(cachedJson);

        UserModel mockUser = createMockUser(1L, "testuser", "테스트유저");
        given(userService.findByUserId(1L)).willReturn(mockUser);

        // when
        UserModel result = authCacheService.authenticateWithCache("testuser", "password123");

        // then
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(userService).findByUserId(1L);
        verify(userService, never()).authenticate(anyString(), anyString());
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
        return user;
    }
}
