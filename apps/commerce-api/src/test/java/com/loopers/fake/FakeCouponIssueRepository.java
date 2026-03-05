package com.loopers.fake;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeCouponIssueRepository implements CouponIssueRepository {

    private final Map<Long, CouponIssue> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public CouponIssue save(CouponIssue couponIssue) {
        if (couponIssue.getId() == null) {
            long id = sequence++;
            ReflectionTestUtils.setField(couponIssue, "id", id);
        }
        store.put(couponIssue.getId(), couponIssue);
        return couponIssue;
    }

    @Override
    public Optional<CouponIssue> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public int markAsUsed(Long id, ZonedDateTime now) {
        CouponIssue issue = store.get(id);
        if (issue == null) return 0;
        if (issue.getStatus() != CouponIssueStatus.AVAILABLE) return 0;
        if (issue.isExpired(now)) return 0;
        issue.use(null, now);
        return 1;
    }

    @Override
    public List<CouponIssue> findAllByMemberId(Long memberId) {
        return store.values().stream()
            .filter(issue -> issue.getMemberId().equals(memberId))
            .toList();
    }

    @Override
    public List<CouponIssue> findAllByCouponId(Long couponId) {
        return store.values().stream()
            .filter(issue -> issue.getCouponId().equals(couponId))
            .toList();
    }
}
