package com.loopers.job.likesync;

import com.loopers.batch.cache.LikeCountBatchCacheService;
import com.loopers.batch.job.likesync.LikeCountSyncJobConfig;
import com.loopers.batch.persistence.LikeBatchRepository;
import com.loopers.batch.persistence.LikeEntity;
import com.loopers.batch.persistence.ProductBatchRepository;
import com.loopers.batch.persistence.ProductEntity;
import com.loopers.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(TestRedisConfiguration.class)
@TestPropertySource(properties = "spring.batch.job.name=" + LikeCountSyncJobConfig.JOB_NAME)
@DisplayName("LikeCountSyncJob E2E 테스트")
class LikeCountSyncJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(LikeCountSyncJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductBatchRepository productRepository;

    @Autowired
    private LikeBatchRepository likeRepository;

    @Autowired
    private LikeCountBatchCacheService likeCountCacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String DIRTY_SET_KEY = "like:dirty:products";

    @BeforeEach
    void setUp() {
        cleanUpRedis();
        cleanUpDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedis();
        cleanUpDatabase();
    }

    private void cleanUpRedis() {
        var keys = redisTemplate.keys("like:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void cleanUpDatabase() {
        likeRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("동기화할 상품이 없을 때 정상 완료")
    void 동기화_대상_없음() throws Exception {
        // Arrange
        jobLauncherTestUtils.setJob(job);

        // Act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }

    @Test
    @DisplayName("dirty 상품의 좋아요 수를 DB에 동기화")
    void 좋아요_수_동기화() throws Exception {
        // Arrange
        ProductEntity product = new ProductEntity();
        product.setLikeCount(0L);
        productRepository.save(product);

        Long productId = product.getId();

        // dirty set에 상품 추가
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, String.valueOf(productId));

        jobLauncherTestUtils.setJob(job);

        // Act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());

        // DB의 like_count가 0으로 설정되어 있어야 함 (좋아요 없음)
        Optional<ProductEntity> updated = productRepository.findById(productId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getLikeCount()).isEqualTo(0L);

        // dirty set에서 제거되었는지 확인
        assertThat(likeCountCacheService.getDirtyProductIds()).doesNotContain(productId);
    }
}
