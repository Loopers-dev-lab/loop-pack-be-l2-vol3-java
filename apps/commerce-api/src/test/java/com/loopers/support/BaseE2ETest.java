package com.loopers.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import com.loopers.utils.DatabaseCleanUp;

/**
 * E2E 테스트의 공통 설정을 제공한다.
 *
 * <p>RANDOM_PORT 환경에서 {@link TestRestTemplate}을 통해 실제 HTTP 요청을 수행하며,
 * 각 테스트 종료 후 데이터베이스를 자동으로 초기화한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseE2ETest {

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void cleanUp() {
        databaseCleanUp.truncateAllTables();
    }
}