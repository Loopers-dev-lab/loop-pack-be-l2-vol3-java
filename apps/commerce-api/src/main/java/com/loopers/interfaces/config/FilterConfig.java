package com.loopers.interfaces.config;

import com.loopers.interfaces.auth.MemberAuthenticationFilter;
import com.loopers.interfaces.auth.OrderAdmissionGateFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<MemberAuthenticationFilter> memberAuthenticationFilterRegistration(
            MemberAuthenticationFilter memberAuthenticationFilter
    ) {
        FilterRegistrationBean<MemberAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(memberAuthenticationFilter);
        registrationBean.addUrlPatterns("/api/v1/*", "/api/v1/*/*", "/api/v1/*/*/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OrderAdmissionGateFilter> orderAdmissionGateFilterRegistration(
            OrderAdmissionGateFilter orderAdmissionGateFilter
    ) {
        FilterRegistrationBean<OrderAdmissionGateFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(orderAdmissionGateFilter);
        registrationBean.addUrlPatterns("/api/v1/orders");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }
}
