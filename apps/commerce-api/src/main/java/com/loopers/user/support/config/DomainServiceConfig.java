package com.loopers.user.support.config;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.service.LoginIdDuplicateValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

	/**
	 * 유저 도메인 서비스 설정
	 * 1. 로그인 ID 중복 검증기 빈 등록
	 */

	// 1. 로그인 ID 중복 검증기 빈 등록
	@Bean
	public LoginIdDuplicateValidator loginIdDuplicateValidator(UserQueryRepository userQueryRepository) {
		return new LoginIdDuplicateValidator(userQueryRepository::existsByLoginId);
	}
}
