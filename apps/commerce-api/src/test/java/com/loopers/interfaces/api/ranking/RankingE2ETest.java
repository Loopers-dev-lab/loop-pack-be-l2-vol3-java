package com.loopers.interfaces.api.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RankingE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    private static final String RANKING_KEY = "ranking:all:" + TODAY;

    private Long productId1;
    private Long productId2;
    private Long productId3;

    @BeforeEach
    void setUp() {
        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("NIKE"), new BrandDescription("나이키"))
        );

        Product p1 = productJpaRepository.save(
            Product.create(brand.getId(), "에어맥스", "러닝화", BigDecimal.valueOf(179000), 50)
        );
        Product p2 = productJpaRepository.save(
            Product.create(brand.getId(), "조던1", "농구화", BigDecimal.valueOf(219000), 30)
        );
        Product p3 = productJpaRepository.save(
            Product.create(brand.getId(), "덩크로우", "캐주얼", BigDecimal.valueOf(139000), 100)
        );

        productId1 = p1.getId();
        productId2 = p2.getId();
        productId3 = p3.getId();

        // ZSET에 점수 적재 (streamer가 하는 역할을 시뮬레이션)
        // 주문 1건(0.7) + 좋아요 2건(0.4) + 조회 3건(0.3) = 1.4
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, productId1.toString(), 1.4);
        // 주문 2건(1.4) + 조회 1건(0.1) = 1.5
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, productId2.toString(), 1.5);
        // 조회 5건(0.5)
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, productId3.toString(), 0.5);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/rankings: 점수 순으로 랭킹 목록을 조회한다")
    @Test
    void getRankings_returnsOrderedByScore() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("date", TODAY)
                .param("size", "20")
                .param("page", "0"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(3))
            // 1위: 조던1 (1.5)
            .andExpect(jsonPath("$.data.content[0].rank").value(1))
            .andExpect(jsonPath("$.data.content[0].productId").value(productId2))
            .andExpect(jsonPath("$.data.content[0].productName").value("조던1"))
            .andExpect(jsonPath("$.data.content[0].brandName").value("NIKE"))
            .andExpect(jsonPath("$.data.content[0].score").value(1.5))
            // 2위: 에어맥스 (1.4)
            .andExpect(jsonPath("$.data.content[1].rank").value(2))
            .andExpect(jsonPath("$.data.content[1].productId").value(productId1))
            .andExpect(jsonPath("$.data.content[1].productName").value("에어맥스"))
            // 3위: 덩크로우 (0.5)
            .andExpect(jsonPath("$.data.content[2].rank").value(3))
            .andExpect(jsonPath("$.data.content[2].productId").value(productId3));
    }

    @DisplayName("GET /api/v1/rankings: date 미입력 시 오늘 날짜로 조회한다")
    @Test
    void getRankings_defaultDate_returnsToday() throws Exception {
        mockMvc.perform(get("/api/v1/rankings"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @DisplayName("GET /api/v1/rankings: 데이터가 없는 날짜는 빈 목록을 반환한다")
    @Test
    void getRankings_noData_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("date", "20200101"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @DisplayName("GET /api/v1/products/{id}: 상품 상세 조회 시 랭킹 순위가 포함된다")
    @Test
    void getProduct_includesRank() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + productId2))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(productId2))
            .andExpect(jsonPath("$.data.name").value("조던1"))
            .andExpect(jsonPath("$.data.rank").value(1))
            .andExpect(jsonPath("$.data.rankScore").value(1.5));
    }

    @DisplayName("GET /api/v1/products/{id}: 랭킹에 없는 상품은 rank가 null이다")
    @Test
    void getProduct_notRanked_rankIsNull() throws Exception {
        Brand brand = brandJpaRepository.findAll().get(0);
        Product unranked = productJpaRepository.save(
            Product.create(brand.getId(), "미등록상품", "설명", BigDecimal.valueOf(50000), 10)
        );

        mockMvc.perform(get("/api/v1/products/" + unranked.getId()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.rank").doesNotExist())
            .andExpect(jsonPath("$.data.rankScore").doesNotExist());
    }

    @DisplayName("일자 변경: 어제 날짜의 랭킹도 정상적으로 조회된다 (TTL 이내)")
    @Test
    void getRankings_pastDate_returnsData() throws Exception {
        String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yesterdayKey = "ranking:all:" + yesterday;

        // 어제 날짜 키에 점수 적재
        redisTemplate.opsForZSet().incrementScore(yesterdayKey, productId1.toString(), 2.5);
        redisTemplate.opsForZSet().incrementScore(yesterdayKey, productId2.toString(), 1.8);

        mockMvc.perform(get("/api/v1/rankings")
                .param("date", yesterday)
                .param("size", "20")
                .param("page", "0"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content.length()").value(2))
            // 1위: 에어맥스 (2.5)
            .andExpect(jsonPath("$.data.content[0].rank").value(1))
            .andExpect(jsonPath("$.data.content[0].productId").value(productId1))
            .andExpect(jsonPath("$.data.content[0].score").value(2.5))
            // 2위: 조던1 (1.8)
            .andExpect(jsonPath("$.data.content[1].rank").value(2))
            .andExpect(jsonPath("$.data.content[1].productId").value(productId2))
            .andExpect(jsonPath("$.data.content[1].score").value(1.8));
    }

    @DisplayName("가중치 검증: 주문 1건(0.7) > 좋아요 3건(0.6)")
    @Test
    void weightVerification_orderBeatsLikes() throws Exception {
        redisCleanUp.truncateAll();

        // 상품A: 좋아요 3건 = 0.6
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, productId1.toString(), 0.6);
        // 상품B: 주문 1건 = 0.7
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, productId2.toString(), 0.7);

        mockMvc.perform(get("/api/v1/rankings")
                .param("date", TODAY))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].productId").value(productId2))
            .andExpect(jsonPath("$.data.content[0].score").value(0.7))
            .andExpect(jsonPath("$.data.content[1].productId").value(productId1))
            .andExpect(jsonPath("$.data.content[1].score").value(0.6));
    }
}
