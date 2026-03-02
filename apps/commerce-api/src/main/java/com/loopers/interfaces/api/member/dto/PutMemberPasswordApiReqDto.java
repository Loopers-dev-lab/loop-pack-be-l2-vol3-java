package com.loopers.interfaces.api.member.dto;

import com.loopers.application.member.dto.PutMemberPasswordReqDto;

public record PutMemberPasswordApiReqDto(
        String currentPassword,
        String newPassword
) {
    public PutMemberPasswordReqDto toDto(String loginId, String loginPassword) {
        return new PutMemberPasswordReqDto(
                loginId,
                loginPassword,
                currentPassword,
                newPassword
        );
    }
}
