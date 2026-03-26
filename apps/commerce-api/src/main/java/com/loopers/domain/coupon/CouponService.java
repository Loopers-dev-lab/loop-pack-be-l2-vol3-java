package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public record RegisterCommand(
            String name, CouponType type, BigDecimal value,
            BigDecimal minOrderAmount, ZonedDateTime expiredAt, int totalQuantity
    ) {
    }

    @Transactional
    public CouponModel register(RegisterCommand command) {
        BigDecimal discountAmount = command.type() == CouponType.FIXED ? command.value() : null;
        Integer discountRate = command.type() == CouponType.RATE ? command.value().intValue() : null;

        return couponRepository.save(new CouponModel(
                command.name(), command.type(), discountAmount,
                discountRate, command.minOrderAmount(), command.expiredAt(), command.totalQuantity()
        ));
    }

    public record ModifyCommand(
            String name, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt
    ) {
    }

    @Transactional
    public CouponModel modifyInfo(Long id, ModifyCommand command) {
        CouponModel coupon = getById(id);
        coupon.modifyInfo(command.name(), command.value(),
                command.minOrderAmount(), command.expiredAt());
        return coupon;
    }

    @Transactional(readOnly = true)
    public CouponModel getById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getAll(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<CouponModel> getAllByIds(List<Long> ids) {
        return couponRepository.findAllByIdIn(ids);
    }

    @Transactional
    public void delete(Long id) {
        CouponModel coupon = getById(id);
        couponRepository.delete(coupon);
    }

    @Transactional
    public CouponModel getByIdWithLock(Long id) {
        return couponRepository.findByIdWithLock(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }
}
