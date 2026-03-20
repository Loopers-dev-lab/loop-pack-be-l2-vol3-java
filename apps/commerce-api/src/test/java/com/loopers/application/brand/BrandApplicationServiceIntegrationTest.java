package com.loopers.application.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class BrandApplicationServiceIntegrationTest {

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("BrandApplicationService 통합: 생성 후 조회 가능")
    void createAndFind() {
        Brand created = brandApplicationService.create(new CreateBrandCommand("브랜드통합", "desc", "img"));

        Brand found = brandApplicationService.findById(created.id());
        Long persistedPk = brandJpaRepository.findByReferenceId(created.id())
                .orElseThrow()
                .getId();

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(persistedPk).isNotNull().isPositive();
        assertThat(found.name().value()).isEqualTo("브랜드통합");
        assertThat(redisTemplate.opsForValue().get("brand:" + created.id())).isNotBlank();
    }

    @Test
    @DisplayName("BrandApplicationService 통합: 수정 후 Redis 읽기 모델도 갱신된다")
    void updateAlsoSyncsRedisReadModel() {
        Brand created = brandApplicationService.create(new CreateBrandCommand("브랜드수정", "old", "img"));

        Brand updated = brandApplicationService.update(created.id(), new UpdateBrandCommand("new", "new-img"));

        String cached = redisTemplate.opsForValue().get("brand:" + created.id());

        assertThat(updated.description()).isEqualTo("new");
        assertThat(cached).contains("new");
        assertThat(cached).contains("new-img");
    }

    @Test
    @DisplayName("BrandApplicationService 통합: 삭제 후 Redis 읽기 모델이 제거된다")
    void deleteAlsoEvictsRedisReadModel() {
        Brand created = brandApplicationService.create(new CreateBrandCommand("브랜드삭제", "desc", "img"));
        brandApplicationService.findById(created.id());

        brandApplicationService.delete(created.id());

        assertThat(redisTemplate.opsForValue().get("brand:" + created.id())).isNull();
    }
}
