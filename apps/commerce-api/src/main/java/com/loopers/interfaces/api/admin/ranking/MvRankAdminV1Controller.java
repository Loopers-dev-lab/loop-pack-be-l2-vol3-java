package com.loopers.interfaces.api.admin.ranking;

import com.loopers.application.ranking.MvRankStatusApp;
import com.loopers.application.ranking.MvRankStatusInfo;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/rankings/mv")
@RequiredArgsConstructor
@Tag(name = "MV Rank Admin", description = "랭킹 MV publication·version 상태 조회")
public class MvRankAdminV1Controller {

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";
    private static final int MAX_PAGE_SIZE = 500;

    private final MvRankStatusApp mvRankStatusApp;

    @GetMapping("/{periodType}")
    @Operation(summary = "periodType별 MV 상태 목록", description = "published_version/total/orphan row 수 페이징 조회")
    public ResponseEntity<ApiResponse<List<MvRankAdminV1Dto.StatusResponse>>> listStatus(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @Parameter(description = "WEEKLY | MONTHLY | QUARTERLY") @PathVariable String periodType,
            @Parameter(description = "페이징 offset (기본 0)") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "페이지 크기 (기본 50, 최대 500)") @RequestParam(defaultValue = "50") int size
    ) {
        validateAdmin(ldapHeader);
        RankPeriodType type = parsePeriodType(periodType);
        int clampedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int clampedOffset = Math.max(0, offset);
        List<MvRankStatusInfo> statuses = mvRankStatusApp.listStatuses(type, clampedOffset, clampedSize);
        return ResponseEntity.ok(ApiResponse.success(
                statuses.stream().map(MvRankAdminV1Dto.StatusResponse::from).toList()
        ));
    }

    @GetMapping("/{periodType}/{periodKey}")
    @Operation(summary = "단건 MV 상태 조회")
    public ResponseEntity<ApiResponse<MvRankAdminV1Dto.StatusResponse>> getStatus(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String periodType,
            @PathVariable String periodKey
    ) {
        validateAdmin(ldapHeader);
        RankPeriodType type = parsePeriodType(periodType);
        MvRankStatusInfo status = mvRankStatusApp.getStatus(type, periodKey);
        return ResponseEntity.ok(ApiResponse.success(MvRankAdminV1Dto.StatusResponse.from(status)));
    }

    private RankPeriodType parsePeriodType(String periodType) {
        try {
            return RankPeriodType.valueOf(periodType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 periodType: " + periodType);
        }
    }

    private void validateAdmin(String ldap) {
        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN, "관리자 권한 필요");
        }
    }
}
