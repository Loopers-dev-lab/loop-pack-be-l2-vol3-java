package com.loopers.application.user;

import com.loopers.domain.user.UserAuthCacheRepository;
import com.loopers.domain.user.UserAuthCacheRepository.CachedAuth;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;
    private final UserAuthCacheRepository userAuthCacheRepository;

    // 캐시 검증용 HMAC 시크릿 — 운영 환경에서는 환경변수로 관리
    private static final byte[] HMAC_SECRET = "loopers-auth-cache-secret-2024".getBytes(StandardCharsets.UTF_8);

    public UserInfo authenticate(String loginId, String password) {
        CachedAuth cached = userAuthCacheRepository.find(loginId).orElse(null);
        if (cached != null) {
            // 캐시 HIT: HMAC 비교(≈10μs) — BCrypt(≈100ms) 완전 제거
            if (!computeFastHash(password).equals(cached.passwordFastHash())) {
                throw new CoreException(ErrorType.UNAUTHORIZED, "회원 정보가 올바르지 않습니다.");
            }
            return new UserInfo(cached.userId(), loginId, cached.name(),
                    LocalDate.parse(cached.birthday()), cached.email());
        }

        // 캐시 MISS: DB 조회 + BCrypt 검증 (최초 1회)
        UserModel user = userService.authenticate(loginId, password);
        UserInfo userInfo = UserInfo.from(user);
        userAuthCacheRepository.save(loginId, new CachedAuth(
                computeFastHash(password), user.getId(), user.getName(),
                user.getBirthday().toString(), user.getEmail()
        ));
        return userInfo;
    }

    public UserInfo signUp(SignupCommand command) {
        UserModel user = userService.signup(
            command.loginId(), command.password(), command.name(), command.birthday(), command.email()
        );
        return UserInfo.from(user);
    }

    public void changePassword(ChangePasswordCommand command) {
        userService.changePassword(command.loginId(), command.currentPassword(), command.newPassword());
        // 비밀번호 변경 후 캐시 무효화
        userAuthCacheRepository.evict(command.loginId());
    }

    // HMAC-SHA256(rawPassword, serverSecret) — BCrypt 대체용 fast verifier
    // BCrypt: ≈100ms | HMAC-SHA256: ≈10μs → 10,000배 차이
    private String computeFastHash(String rawPassword) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "인증 해시 계산 실패");
        }
    }
}
