package com.loopers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 역할: commerce-batch 모듈의 Spring 컨텍스트가 기동 가능한지 스모크 테스트한다.
 * {@code spring.batch.job.name} 기본값(NONE)은 실제 Job 이름으로 해석되어 기동 실패할 수 있어,
 * 이 테스트만 배치 Job 자동 실행을 끈다. {@link com.loopers.job.demo.DemoJobE2ETest} 등은 별도 프로퍼티로 Job을 지정한다.
 */
@SpringBootTest(
        properties = "spring.batch.job.enabled=false"
)
public class CommerceBatchApplicationTest {

    /** 빈 정의·설정 로딩 오류가 없는지 확인. */
    @Test
    void contextLoads() {}
}
