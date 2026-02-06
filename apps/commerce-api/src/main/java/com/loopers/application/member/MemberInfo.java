package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 회원 정보 DTO (Data Transfer Object)
 *
 * Application Layer에서 사용하는 데이터 전송 객체
 * Domain Model을 외부로 직접 노출하지 않기 위해 사용
 */
@Getter
@Builder
public class MemberInfo {

    private final String loginId;
    private final String name;
    private final String maskedName;  // 마스킹된 이름
    private final LocalDate birthDate;
    private final String email;

    /**
     * Domain Model을 DTO로 변환
     *
     * @param member Domain Model
     * @return MemberInfo DTO
     */
    public static MemberInfo from(MemberModel member) {
        return MemberInfo.builder()
                .loginId(member.getLoginId())
                .name(member.getName())
                .maskedName(member.getMaskedName())
                .birthDate(member.getBirthDate())
                .email(member.getEmail())
                .build();
    }

    /**
     * Domain Model을 DTO로 변환 (마스킹 적용)
     *
     * @param member Domain Model
     * @param applyMasking 마스킹 적용 여부
     * @return MemberInfo DTO
     */
    public static MemberInfo from(MemberModel member, boolean applyMasking) {
        if (applyMasking) {
            return from(member);
        }

        // 마스킹 미적용 시 원본 이름 사용
        return MemberInfo.builder()
                .loginId(member.getLoginId())
                .name(member.getName())
                .maskedName(member.getName())
                .birthDate(member.getBirthDate())
                .email(member.getEmail())
                .build();
    }
}
