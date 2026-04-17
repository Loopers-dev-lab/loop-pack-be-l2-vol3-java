package com.loopers.interfaces.api;

import com.loopers.CommerceApiApplication;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.interfaces.api.ranking.dto.FindRankingListApiResDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
        classes = CommerceApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=",
                "spring.kafka.listener.auto-startup=false"
        }
)
@ActiveProfiles("test")
@Import({
        MySqlTestContainersConfig.class,
        RedisTestContainersConfig.class,
        RankingV1ApiE2ETest.TestJpaConfig.class
})
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RankingV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            RedisTemplate<String, String> redisTemplate,
            JdbcTemplate jdbcTemplate,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    private ProductEntity saveProduct(String brandName, String productName, int price) {
        Brand brand = Brand.create(new BrandCommand.Create(brandName, "랭킹 테스트 브랜드"));
        BrandEntity brandEntity = brandJpaRepository.save(BrandEntity.toEntity(brand));
        Product product = Product.create(brandEntity.getId(), new ProductCommand.Create(brandEntity.getId(), productName, price, 100));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("period=daily 조회 시 Redis 일간 랭킹을 반환한다")
        @Test
        void dailyRankings() {
            ProductEntity first = saveProduct("나이키", "에어맥스", 120000);
            ProductEntity second = saveProduct("아디다스", "삼바", 90000);

            redisTemplate.opsForZSet().add("ranking:all:20260415", String.valueOf(first.getId()), 99.9d);
            redisTemplate.opsForZSet().add("ranking:all:20260415", String.valueOf(second.getId()), 88.8d);

            ResponseEntity<ApiResponse<FindRankingListApiResDto>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=daily&date=20260415&size=10&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(2),
                    () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(first.getId())
            );
        }

        @DisplayName("period=weekly 조회 시 weekly MV를 반환한다")
        @Test
        void weeklyRankings() {
            ProductEntity first = saveProduct("나이키", "에어맥스", 120000);
            ProductEntity second = saveProduct("아디다스", "삼바", 90000);

            insertWeeklyRanking("2026-W16", 1, first.getId(), 120.0d, 10, 20, 30);
            insertWeeklyRanking("2026-W16", 2, second.getId(), 110.0d, 8, 18, 28);

            ResponseEntity<ApiResponse<FindRankingListApiResDto>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=weekly&date=20260415&size=10&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(2),
                    () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(first.getId())
            );
        }

        @DisplayName("period=monthly 조회 시 monthly MV를 반환한다")
        @Test
        void monthlyRankings() {
            ProductEntity first = saveProduct("나이키", "에어맥스", 120000);
            ProductEntity second = saveProduct("아디다스", "삼바", 90000);

            insertMonthlyRanking("2026-04", 1, first.getId(), 220.0d, 20, 30, 40);
            insertMonthlyRanking("2026-04", 2, second.getId(), 210.0d, 18, 28, 38);

            ResponseEntity<ApiResponse<FindRankingListApiResDto>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=monthly&date=20260415&size=10&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(2),
                    () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(first.getId())
            );
        }

        @DisplayName("지원하지 않는 period는 400 BAD_REQUEST를 반환한다")
        @Test
        void invalidPeriod() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=yearly&date=20260415&size=10&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("page가 1 미만이면 400 BAD_REQUEST를 반환한다")
        @Test
        void invalidPage() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=weekly&date=20260415&size=10&page=0",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("size가 1 미만이면 400 BAD_REQUEST를 반환한다")
        @Test
        void invalidSize() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=weekly&date=20260415&size=0&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("데이터가 없으면 빈 rankings를 반환한다")
        @Test
        void emptyRankings() {
            ResponseEntity<ApiResponse<FindRankingListApiResDto>> response = testRestTemplate.exchange(
                    ENDPOINT + "?period=weekly&date=20260415&size=10&page=1",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().rankings()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalCount()).isZero()
            );
        }
    }

    private void insertWeeklyRanking(String periodKey, int rankNo, Long productId, double score, long viewCount, long likeCount, long orderCount) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly
                    (period_key, rank_no, product_id, score, view_count, like_count, order_count, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                periodKey, rankNo, productId, score, viewCount, likeCount, orderCount, LocalDateTime.now());
    }

    private void insertMonthlyRanking(String periodKey, int rankNo, Long productId, double score, long viewCount, long likeCount, long orderCount) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_monthly
                    (period_key, rank_no, product_id, score, view_count, like_count, order_count, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                periodKey, rankNo, productId, score, viewCount, likeCount, orderCount, LocalDateTime.now());
    }

    @TestConfiguration
    @EntityScan("com.loopers")
    @EnableJpaRepositories(basePackages = {"com.loopers.infrastructure", "com.loopers.batch.infrastructure"})
    static class TestJpaConfig {

        @Bean(name = "entityManagerFactory")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            vendorAdapter.setGenerateDdl(true);
            vendorAdapter.setShowSql(true);

            LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setPackagesToScan("com.loopers");
            factoryBean.setJpaVendorAdapter(vendorAdapter);

            HashMap<String, Object> properties = new HashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "create");
            properties.put("hibernate.show_sql", "true");
            properties.put("hibernate.format_sql", "true");
            properties.put("hibernate.jdbc.time_zone", "UTC");
            properties.put("hibernate.default_batch_fetch_size", "100");
            properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            factoryBean.setJpaPropertyMap(properties);
            return factoryBean;
        }

        @Bean
        PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
