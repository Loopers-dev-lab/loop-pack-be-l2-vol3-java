package com.loopers.application.like;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class LikeApplicationServiceIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("LikeApplicationService 통합: 등록 후 assertLiked 통과")
    void registerAndAssertLiked() {
        UUID productId = UUID.randomUUID();

        likeApplicationService.register("likemember", productId);
        likeApplicationService.assertLiked("likemember", productId);
    }
}
