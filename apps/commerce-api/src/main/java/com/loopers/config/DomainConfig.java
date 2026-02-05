package com.loopers.config;

import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public MemberService memberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        return new MemberService(memberRepository, passwordEncoder);
    }
}
