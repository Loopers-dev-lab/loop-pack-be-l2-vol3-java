package com.loopers.domain.user;

import com.loopers.domain.user.exception.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * User 도메인 서비스
 * 엔티티에 속하지 않는 순수 비즈니스 로직을 담당합니다.
 *
 * - 트랜잭션 관리 없음 (Application Service에서 처리)
 * - 인프라스트럭처 의존성 없음 (인터페이스만 의존)
 * - 순수 도메인 로직만 포함
 */
@Component
public class UserDomainService {

    /**
     * 새로운 사용자를 생성합니다.
     *
     * @param loginId 로그인 ID
     * @param rawPassword 평문 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @param gender 성별
     * @param passwordEncryptor 비밀번호 암호화 인터페이스
     * @return 생성된 User
     * @throws IllegalArgumentException 비밀번호에 생년월일이 포함된 경우
     */
    public User createUser(
        String loginId,
        RawPassword rawPassword,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender,
        PasswordEncryptor passwordEncryptor
    ) {
        // 비밀번호 암호화
        EncryptedPassword encryptedPassword =
            new EncryptedPassword(passwordEncryptor.encode(rawPassword.value()));

        // User 생성 (팩토리 메서드)
        return User.create(loginId, rawPassword, encryptedPassword, name, birthDate, email, gender);
    }

    /**
     * 사용자의 비밀번호를 변경합니다.
     *
     * @param user 대상 사용자
     * @param oldPassword 기존 평문 비밀번호
     * @param newPassword 새로운 평문 비밀번호
     * @param passwordEncryptor 비밀번호 암호화 인터페이스
     * @throws InvalidPasswordException 기존 비밀번호가 일치하지 않는 경우
     * @throws IllegalArgumentException 새 비밀번호에 생년월일이 포함된 경우
     */
    public void updatePassword(
        User user,
        RawPassword oldPassword,
        RawPassword newPassword,
        PasswordEncryptor passwordEncryptor
    ) {
        // 기존 비밀번호 검증
        if (!passwordEncryptor.matches(oldPassword.value(), user.getPassword())) {
            throw new InvalidPasswordException("기존 비밀번호가 일치하지 않습니다");
        }

        // 새 비밀번호 암호화
        EncryptedPassword encryptedNewPassword =
            new EncryptedPassword(passwordEncryptor.encode(newPassword.value()));

        // 비밀번호 변경
        user.changePassword(newPassword, encryptedNewPassword);
    }
}
