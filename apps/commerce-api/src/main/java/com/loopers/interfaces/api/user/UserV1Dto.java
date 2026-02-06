package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UserV1Dto {
    
    public record SignUpRequest(
        @NotBlank(message = "사용자 ID는 필수입니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]{1,10}$", message = "사용자 ID는 영문자와 숫자로만 구성되며 최대 10자입니다.")
        String userId,
        
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,
        
        @NotBlank(message = "이메일은 필수입니다.")
        String email,
        
        @NotBlank(message = "생년월일은 필수입니다.")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일 형식은 yyyy-MM-dd 이어야 합니다.")
        String birthDate,
        
        @NotBlank(message = "성별은 필수입니다.")
        String gender
    ) {
    }
    
    public record SignUpResponse(
        String userId,
        String email,
        String birthDate,
        String gender
    ) {
        public static SignUpResponse from(UserInfo userInfo) {
            return new SignUpResponse(
                userInfo.userId(),
                userInfo.email(),
                userInfo.birthDate(),
                userInfo.gender()
            );
        }
    }

    public record MyInfoResponse(
        String userId,
        String name,
        String email,
        String birthDate,
        String gender
    ) {
        public static MyInfoResponse from(UserInfo userInfo) {
            return new MyInfoResponse(
                userInfo.userId(),
                userInfo.name(),
                userInfo.email(),
                userInfo.birthDate(),
                userInfo.gender()
            );
        }
    }
}
