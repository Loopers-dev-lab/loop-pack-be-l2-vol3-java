package com.loopers.application.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class BrandApplicationServiceIntegrationTest {

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("BrandApplicationService 통합: 생성 후 조회 가능")
    void createAndFind() {
        Brand created = brandApplicationService.create(new CreateBrandCommand("브랜드통합", "desc", "img"));

        Brand found = brandApplicationService.findById(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.name().value()).isEqualTo("브랜드통합");
    }
}
