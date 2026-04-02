package com.loopers.domain.coupon;

public class CouponIssueRequest {

    private final Long id;
    private final Long userId;
    private final Long templateId;
    private CouponIssueRequestStatus status;
    private String failReason;

    private CouponIssueRequest(Long id, Long userId, Long templateId, CouponIssueRequestStatus status, String failReason) {
        this.id = id;
        this.userId = userId;
        this.templateId = templateId;
        this.status = status;
        this.failReason = failReason;
    }

    public static CouponIssueRequest create(Long userId, Long templateId) {
        return new CouponIssueRequest(null, userId, templateId, CouponIssueRequestStatus.PENDING, null);
    }

    public static CouponIssueRequest restore(Long id, Long userId, Long templateId, CouponIssueRequestStatus status, String failReason) {
        return new CouponIssueRequest(id, userId, templateId, status, failReason);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTemplateId() { return templateId; }
    public CouponIssueRequestStatus getStatus() { return status; }
    public String getFailReason() { return failReason; }
}
