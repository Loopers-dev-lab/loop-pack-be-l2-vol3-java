package com.loopers.fake;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import org.springframework.test.util.ReflectionTestUtils;

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
    public Optional<CouponIssue> findByIdWithLock(Long id) {
        return findById(id);
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
