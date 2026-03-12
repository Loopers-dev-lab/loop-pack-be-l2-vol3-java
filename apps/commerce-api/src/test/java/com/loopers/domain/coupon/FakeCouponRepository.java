package com.loopers.domain.coupon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class FakeCouponRepository implements CouponRepository {

    private final Map<Long, CouponModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponModel save(CouponModel couponModel) {
        if (couponModel.getId() == 0L) {
            setId(couponModel, idGenerator.getAndIncrement());
        }
        store.put(couponModel.getId(), couponModel);
        return couponModel;
    }

    @Override
    public Optional<CouponModel> findById(Long id) {
        return Optional.ofNullable(store.get(id))
                .filter(coupon -> coupon.getDeletedAt() == null);
    }

    @Override
    public Page<CouponModel> findAll(Pageable pageable) {
        List<CouponModel> activeModels = store.values().stream()
                .filter(coupon -> coupon.getDeletedAt() == null)
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), activeModels.size());

        List<CouponModel> pageContent = start >= activeModels.size()
                ? new ArrayList<>()
                : activeModels.subList(start, end);

        return new PageImpl<>(pageContent, pageable, activeModels.size());
    }

    private void setId(Object target, Long id) {
        try {
            var field = target.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
