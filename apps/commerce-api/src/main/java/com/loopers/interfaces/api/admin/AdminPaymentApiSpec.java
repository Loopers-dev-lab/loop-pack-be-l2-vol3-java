package com.loopers.interfaces.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Admin Payment V1 API", description = "어드민 결제 API")
public interface AdminPaymentApiSpec {

    @Operation(summary = "결제 상태 동기화", description = "PG에 결제 상태를 조회하여 내부 시스템에 반영한다.")
    void syncPayment(@PathVariable Long orderId);
}