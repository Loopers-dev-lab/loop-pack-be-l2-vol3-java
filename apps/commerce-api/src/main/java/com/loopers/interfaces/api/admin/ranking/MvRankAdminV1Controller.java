package com.loopers.interfaces.api.admin.ranking;

import com.loopers.application.ranking.MvRankStatusApp;
import com.loopers.application.ranking.MvRankStatusInfo;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/rankings/mv")
@RequiredArgsConstructor
public class MvRankAdminV1Controller {

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final MvRankStatusApp mvRankStatusApp;

    @GetMapping("/{periodType}")
    public ResponseEntity<ApiResponse<List<MvRankAdminV1Dto.StatusResponse>>> listStatus(
            @RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
            @PathVariable String periodType
    ) {
        validateAdmin(ldapHeader);
        RankPeriodType type = parsePeriodType(periodType);
        List<MvRankStatusInfo> statuses = mvRankStatusApp.listStatuses(type);
        return ResponseEntity.ok(ApiResponse.success(
                statuses.stream().map(MvRankAdminV1Dto.StatusResponse::from).toList()
        ));
    }

    @GetMapping("/{periodType}/{periodKey}")
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
