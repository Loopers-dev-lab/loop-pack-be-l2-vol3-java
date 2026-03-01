package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;

import java.time.LocalDate;

/**
 * User Application Layer DTO
 *
 * Application Layer와 Interface Layer 간 데이터 전달 객체
 * - 도메인 객체(User)를 외부에 직접 노출하지 않음
 * - API 버전 변경에 영향받지 않는 안정적인 계약
 */
public class UserDto {

    /**
     * 사용자 정보 DTO
     * - 조회 결과를 반환할 때 사용
     */
    public record UserInfo(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        /**
         * 도메인 객체(User)로부터 Application DTO를 생성합니다.
         *
         * @param user 도메인 User 객체
         * @return UserInfo DTO
         */
        public static UserInfo from(User user) {
            return new UserInfo(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getBirthDate(),
                user.getEmail(),
                user.getGender()
            );
        }
    }

    /**
     * 사용자 등록 커맨드
     * - Interface Layer로부터 등록 요청을 받을 때 사용 (선택적)
     * - 현재는 개별 파라미터로 전달하지만, 향후 커맨드 패턴 적용 시 사용 가능
     */
    public record RegisterCommand(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
    }

    /**
     * 비밀번호 변경 커맨드
     */
    public record ChangePasswordCommand(
        Long userId,
        String oldPassword,
        String newPassword
    ) {
    }
}
