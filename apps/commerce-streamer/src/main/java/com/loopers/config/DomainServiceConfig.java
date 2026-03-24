package com.loopers.config;

import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public CouponDomainService couponDomainService(CouponRepository couponRepository) {
        return new CouponDomainService(couponRepository);
    }

    @Bean
    public CouponIssueDomainService couponIssueDomainService(CouponIssueRepository couponIssueRepository) {
        return new CouponIssueDomainService(couponIssueRepository);
    }
}
