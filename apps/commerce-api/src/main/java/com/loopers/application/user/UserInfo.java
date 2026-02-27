package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 정보 DTO.
 * 도메인 모델({@link UserModel})을 직접 노출하지 않고 인터페이스 레이어에 전달하기 위한 응답 객체이다.
 * 비밀번호를 제외하고 마스킹된 이름을 포함한다.
 */
@Getter
@Builder
public class UserInfo {
    private final String userId;
    private final String loginId;
    private final String maskedName;
    private final String birthday;
    private final String email;
    private final String address;

    /**
     * UserModel을 UserInfo DTO로 변환한다. password를 제외하고 maskedName을 포함한다.
     *
     * @param user 변환할 사용자 엔티티
     * @return 사용자 정보 DTO
     */
    public static UserInfo from(UserModel user) {
        return UserInfo.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .maskedName(user.getMaskedName())
                .birthday(user.getBirthday())
                .email(user.getEmail())
                .address(user.getAddress())
                .build();
    }
}
