package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.service.QueueFeatureFlag;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/queue")
public class QueueAdminV1Controller {

    private final QueueFeatureFlag queueFeatureFlag;

    @Value("${queue.admin.key:admin-secret-key}")
    private String adminKey;

    @PutMapping("/toggle")
    public ApiResponse<Void> toggle(@RequestHeader("X-Admin-Key") String requestKey,
                                    @RequestParam boolean enabled) {
        if (!adminKey.equals(requestKey)) {
            throw new CoreException(ErrorType.FORBIDDEN, "관리자 인증에 실패했습니다.");
        }
        queueFeatureFlag.setEnabled(enabled);
        return ApiResponse.successNoContent();
    }
}
