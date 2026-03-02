package com.loopers.application.member.dto;

import com.loopers.domain.member.model.MemberCommand;

public record PutMemberPasswordReqDto(
        String loginId,
        String loginPassword,
        String currentPassword,
        String newPassword
) {
    public MemberCommand.ChangePassword toCommand() {
        return new MemberCommand.ChangePassword(loginId, loginPassword, currentPassword, newPassword);
    }
}
