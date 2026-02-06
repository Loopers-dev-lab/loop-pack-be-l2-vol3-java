package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberCommand;
import com.loopers.application.member.MemberInfo;
import com.loopers.domain.member.MemberModel;
import com.loopers.support.util.MaskingUtil;

import java.time.LocalDate;

public class MemberV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public MemberCommand.SignUp toCommand() {
            return new MemberCommand.SignUp(loginId, password, name, birthDate, email);
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {
        public MemberCommand.ChangePassword toCommand(String loginId, String headerPassword) {
            return new MemberCommand.ChangePassword(loginId, headerPassword, currentPassword, newPassword);
        }
    }

    public record MemberResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MemberResponse from(MemberModel model) {
            return new MemberResponse(
                model.getLoginId(),
                MaskingUtil.maskLast(model.getName(), 1),
                model.getBirthDate(),
                model.getEmail()
            );
        }
    }
}
