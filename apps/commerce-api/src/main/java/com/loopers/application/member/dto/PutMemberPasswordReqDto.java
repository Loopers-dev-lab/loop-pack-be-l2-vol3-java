package com.loopers.application.member.dto;

public record PutMemberPasswordReqDto(
        String loginId,
        String loginPassword,
        String currentPassword,
        String newPassword
) {

}
