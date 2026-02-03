package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record MemberInfo(Long id, String loginId, String name, LocalDate birthDate, String email) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }

    public static MemberInfo fromWithMaskedName(MemberModel model) {
        String maskedName = maskLastCharacter(model.getName());
        return new MemberInfo(
            model.getId(),
            model.getLoginId(),
            maskedName,
            model.getBirthDate(),
            model.getEmail()
        );
    }

    private static String maskLastCharacter(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
