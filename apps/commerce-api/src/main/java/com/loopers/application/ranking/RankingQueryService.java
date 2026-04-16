package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(RankingProperties.class)
public class RankingQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOURLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;
    private final ProductRankWeeklyJpaRepository weeklyRankRepository;
    private final ProductRankMonthlyJpaRepository monthlyRankRepository;
    private final RankingProperties properties;

    /**
     * 랭킹 목록을 조회합니다.
     *
     * @param date   조회할 날짜
     * @param size   페이지 크기
     * @param offset 시작 위치 (0-based)
     * @return 랭킹 결과 리스트
     */
    public List<RankingResult> getRankings(LocalDate date, int size, int offset) {
        String key = generateKey(date);

        // 1. ZREVRANGE로 점수 내림차순 조회
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // 2. productId 목록 추출
        List<Long> productIds = tuples.stream()
            .map(TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::parseLong)
            .toList();

        // 3. DB에서 상품 정보 일괄 조회 (IN 쿼리)
        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 4. 랭킹 결과 생성 (삭제된 상품 필터링)
        List<RankingResult> results = new ArrayList<>();
        int rank = offset + 1; // 1-based rank

        for (TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(Objects.requireNonNull(tuple.getValue()));
            Product product = productMap.get(productId);

            if (product != null) {
                results.add(RankingResult.of(
                    rank,
                    productId,
                    product.getName(),
                    product.getPrice().amount(),
                    product.getImageUrl(),
                    tuple.getScore()
                ));
            }
            rank++;
        }

        return results;
    }

    /**
     * 특정 상품의 랭킹 정보를 조회합니다.
     *
     * @param productId 상품 ID
     * @param date      조회할 날짜
     * @return 랭킹 정보 (없으면 null)
     */
    public RankInfo getProductRank(Long productId, LocalDate date) {
        String key = generateKey(date);
        String member = String.valueOf(productId);

        // ZREVRANK로 순위 조회 (0-based)
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(key, member);
        if (zeroBasedRank == null) {
            return null;
        }

        // ZSCORE로 점수 조회
        Double score = redisTemplate.opsForZSet().score(key, member);

        // 1-based로 변환
        return RankInfo.of(zeroBasedRank.intValue() + 1, score);
    }

    /**
     * 랭킹 전체 개수를 조회합니다. (ZCARD)
     *
     * @param date 조회할 날짜
     * @return 전체 개수
     */
    public long getTotalCount(LocalDate date) {
        String key = generateKey(date);
        Long size = redisTemplate.opsForZSet().size(key);
        return size != null ? size : 0;
    }

    private String generateKey(LocalDate date) {
        return properties.keyPrefix() + date.format(DATE_FORMATTER);
    }

    /**
     * 시간별 랭킹 목록을 조회합니다.
     *
     * @param dateTime 조회할 시간
     * @param size     페이지 크기
     * @param offset   시작 위치 (0-based)
     * @return 랭킹 결과 리스트
     */
    public List<RankingResult> getHourlyRankings(LocalDateTime dateTime, int size, int offset) {
        String key = generateHourlyKey(dateTime);

        // 1. ZREVRANGE로 점수 내림차순 조회
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // 2. productId 목록 추출
        List<Long> productIds = tuples.stream()
            .map(TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::parseLong)
            .toList();

        // 3. DB에서 상품 정보 일괄 조회 (IN 쿼리)
        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 4. 랭킹 결과 생성 (삭제된 상품 필터링)
        List<RankingResult> results = new ArrayList<>();
        int rank = offset + 1; // 1-based rank

        for (TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(Objects.requireNonNull(tuple.getValue()));
            Product product = productMap.get(productId);

            if (product != null) {
                results.add(RankingResult.of(
                    rank,
                    productId,
                    product.getName(),
                    product.getPrice().amount(),
                    product.getImageUrl(),
                    tuple.getScore()
                ));
            }
            rank++;
        }

        return results;
    }

    /**
     * 시간별 랭킹 전체 개수를 조회합니다. (ZCARD)
     *
     * @param dateTime 조회할 시간
     * @return 전체 개수
     */
    public long getHourlyTotalCount(LocalDateTime dateTime) {
        String key = generateHourlyKey(dateTime);
        Long size = redisTemplate.opsForZSet().size(key);
        return size != null ? size : 0;
    }

    private String generateHourlyKey(LocalDateTime dateTime) {
        return HOURLY_KEY_PREFIX + dateTime.format(HOURLY_FORMATTER);
    }

    // ========== 기간별 랭킹 조회 (일간/주간/월간 통합) ==========

    /**
     * 기간별 랭킹을 조회합니다.
     *
     * @param date   조회 기준 날짜
     * @param period 조회 기간 (DAILY, WEEKLY, MONTHLY)
     * @param size   페이지 크기
     * @param offset 시작 위치 (0-based)
     * @return 기간별 랭킹 결과 리스트
     */
    public List<PeriodRankingResult> getPeriodRankings(LocalDate date, RankingPeriod period, int size, int offset) {
        return switch (period) {
            case DAILY -> getDailyPeriodRankings(date, size, offset);
            case WEEKLY -> getWeeklyPeriodRankings(date, size, offset);
            case MONTHLY -> getMonthlyPeriodRankings(date, size, offset);
        };
    }

    /**
     * 기간별 랭킹의 전체 개수를 조회합니다.
     *
     * @param date   조회 기준 날짜
     * @param period 조회 기간 (DAILY, WEEKLY, MONTHLY)
     * @return 전체 개수
     */
    public long getPeriodTotalCount(LocalDate date, RankingPeriod period) {
        return switch (period) {
            case DAILY -> getTotalCount(date);
            case WEEKLY -> getWeeklyTotalCount(date);
            case MONTHLY -> getMonthlyTotalCount(date);
        };
    }

    private List<PeriodRankingResult> getDailyPeriodRankings(LocalDate date, int size, int offset) {
        String key = generateKey(date);

        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = tuples.stream()
            .map(TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::parseLong)
            .toList();

        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<PeriodRankingResult> results = new ArrayList<>();
        int rank = offset + 1;

        for (TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(Objects.requireNonNull(tuple.getValue()));
            Product product = productMap.get(productId);

            if (product != null) {
                results.add(PeriodRankingResult.ofDaily(
                    rank,
                    productId,
                    product.getName(),
                    product.getPrice().amount(),
                    product.getImageUrl(),
                    tuple.getScore(),
                    date
                ));
            }
            rank++;
        }

        return results;
    }

    private List<PeriodRankingResult> getWeeklyPeriodRankings(LocalDate date, int size, int offset) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<ProductRankWeeklyJpaEntity> entities = weeklyRankRepository
            .findByPeriodStartDateOrderByRankNumberAsc(weekStart);

        if (entities.isEmpty()) {
            return List.of();
        }

        // 페이징 적용
        int endIndex = Math.min(offset + size, entities.size());
        if (offset >= entities.size()) {
            return List.of();
        }
        List<ProductRankWeeklyJpaEntity> pagedEntities = entities.subList(offset, endIndex);

        // 상품 정보 조회
        List<Long> productIds = pagedEntities.stream()
            .map(ProductRankWeeklyJpaEntity::getProductId)
            .toList();

        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        return pagedEntities.stream()
            .map(entity -> {
                Product product = productMap.get(entity.getProductId());
                return PeriodRankingResult.ofPeriod(
                    entity.getRankNumber(),
                    entity.getProductId(),
                    product != null ? product.getName() : null,
                    product != null ? product.getPrice().amount() : null,
                    product != null ? product.getImageUrl() : null,
                    entity.getTotalScore(),
                    entity.getTotalViewCount(),
                    entity.getTotalLikeCount(),
                    entity.getTotalOrderCount(),
                    RankingPeriod.WEEKLY,
                    entity.getPeriodStartDate(),
                    entity.getPeriodEndDate()
                );
            })
            .filter(r -> r.productName() != null) // 삭제된 상품 필터링
            .toList();
    }

    private List<PeriodRankingResult> getMonthlyPeriodRankings(LocalDate date, int size, int offset) {
        LocalDate monthStart = YearMonth.from(date).atDay(1);

        List<ProductRankMonthlyJpaEntity> entities = monthlyRankRepository
            .findByPeriodStartDateOrderByRankNumberAsc(monthStart);

        if (entities.isEmpty()) {
            return List.of();
        }

        // 페이징 적용
        int endIndex = Math.min(offset + size, entities.size());
        if (offset >= entities.size()) {
            return List.of();
        }
        List<ProductRankMonthlyJpaEntity> pagedEntities = entities.subList(offset, endIndex);

        // 상품 정보 조회
        List<Long> productIds = pagedEntities.stream()
            .map(ProductRankMonthlyJpaEntity::getProductId)
            .toList();

        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        return pagedEntities.stream()
            .map(entity -> {
                Product product = productMap.get(entity.getProductId());
                return PeriodRankingResult.ofPeriod(
                    entity.getRankNumber(),
                    entity.getProductId(),
                    product != null ? product.getName() : null,
                    product != null ? product.getPrice().amount() : null,
                    product != null ? product.getImageUrl() : null,
                    entity.getTotalScore(),
                    entity.getTotalViewCount(),
                    entity.getTotalLikeCount(),
                    entity.getTotalOrderCount(),
                    RankingPeriod.MONTHLY,
                    entity.getPeriodStartDate(),
                    entity.getPeriodEndDate()
                );
            })
            .filter(r -> r.productName() != null) // 삭제된 상품 필터링
            .toList();
    }

    private long getWeeklyTotalCount(LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weeklyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(weekStart).size();
    }

    private long getMonthlyTotalCount(LocalDate date) {
        LocalDate monthStart = YearMonth.from(date).atDay(1);
        return monthlyRankRepository.findByPeriodStartDateOrderByRankNumberAsc(monthStart).size();
    }
}
