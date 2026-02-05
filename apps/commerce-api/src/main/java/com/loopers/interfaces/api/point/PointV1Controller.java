package com.loopers.interfaces.api.point;

import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/points")
public class PointV1Controller {

    private final MemberService memberService;

    @GetMapping
    public ApiResponse<PointV1Dto.PointResponse> getPoint(
        @RequestHeader(value = "X-USER-ID", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-USER-ID 헤더가 필요합니다.");
        }

        Long point = memberService.findPointByLoginId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        return ApiResponse.success(new PointV1Dto.PointResponse(point));
    }
}
