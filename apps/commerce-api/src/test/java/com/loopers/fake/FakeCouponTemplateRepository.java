package com.loopers.fake;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake CouponTemplateRepository.
 */
public class FakeCouponTemplateRepository implements CouponTemplateRepository {

    private final Map<Long, CouponTemplate> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponTemplate save(CouponTemplate template) {
        Long id = template.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            template = CouponTemplate.reconstitute(
                id,
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getMaxDiscountAmount(),
                template.getMaxIssueCount(),
                template.getIssuedCount(),
                template.getExpiredAt(),
                template.getCreatedAt(),
                template.getDeletedAt()
            );
        }
        store.put(id, template);
        return template;
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<CouponTemplate> findByIdActive(Long id) {
        return findById(id)
            .filter(t -> !t.isDeleted());
    }

    @Override
    public Optional<CouponTemplate> findByIdWithLock(Long id) {
        return findByIdActive(id);
    }

    @Override
    public Page<CouponTemplate> findAllActive(Pageable pageable) {
        var list = store.values().stream()
            .filter(t -> !t.isDeleted())
            .sorted(Comparator.comparing(CouponTemplate::getCreatedAt).reversed())
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .toList();

        long total = store.values().stream()
            .filter(t -> !t.isDeleted())
            .count();

        return new PageImpl<>(list, pageable, total);
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    public int size() {
        return store.size();
    }
}
