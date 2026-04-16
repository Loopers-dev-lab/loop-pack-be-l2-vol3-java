package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.ranking.MvProductRank;
import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.interfaces.api.ranking.RankingDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_RANKING_SIZE = 100;

    private static final String DAILY_ZSET_PREFIX = "ranking:all:";

    private final RankingRedisRepository rankingRedisRepository;
    private final MvProductRankRepository mvProductRankRepository;
    private final ProductRepository productRepository;
    private final RankingProperties properties;

    public RankingDto.PagedRankingResponse getRankings(String scope, String date, int page, int size, Long memberId) {
        String resolvedDate = (date != null) ? date : LocalDate.now(KST).format(DATE_FORMATTER);

        return switch (scope) {
            case "weekly", "monthly" -> getFromMv(scope, resolvedDate, page, size);
            default -> getFromRedis(scope, resolvedDate, page, size, memberId);
        };
    }

    private RankingDto.PagedRankingResponse getFromMv(String scope, String date, int page, int size) {
        // 1. 당일 MV 조회
        List<MvProductRank> mvResults = mvProductRankRepository.findByPeriodKeyAndScope(
            date, scope, PageRequest.of(page, size));
        long totalElements = mvProductRankRepository.countByPeriodKeyAndScope(date, scope);

        // 2. 당일 데이터 없으면 전일 fallback
        if (mvResults.isEmpty()) {
            String previousDate = LocalDate.parse(date, DATE_FORMATTER)
                .minusDays(1).format(DATE_FORMATTER);
            mvResults = mvProductRankRepository.findByPeriodKeyAndScope(
                previousDate, scope, PageRequest.of(page, size));
            totalElements = mvProductRankRepository.countByPeriodKeyAndScope(previousDate, scope);

            if (!mvResults.isEmpty()) {
                log.info("MV 전일 fallback 적용: scope={}, date={} → {}", scope, date, previousDate);
            }
        }

        // 3. 전일도 없으면 빈 결과
        if (mvResults.isEmpty()) {
            return new RankingDto.PagedRankingResponse(List.of(), 0, 0, page, size);
        }

        totalElements = Math.min(totalElements, MAX_RANKING_SIZE);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 4. Product 상세 조합
        List<Long> productIds = mvResults.stream()
            .map(MvProductRank::getProductId).toList();

        Map<Long, ProductWithBrand> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(pwb -> pwb.product().getId(), pwb -> pwb));

        List<RankingDto.RankingResponse> data = new ArrayList<>();
        for (MvProductRank mv : mvResults) {
            ProductWithBrand pwb = productMap.get(mv.getProductId());
            if (pwb != null) {
                Product product = pwb.product();
                data.add(new RankingDto.RankingResponse(
                    mv.getProductId(), product.getName(), pwb.brandName(),
                    product.getPrice().getValue(), mv.getRanking(), mv.getScore()
                ));
            }
        }

        return new RankingDto.PagedRankingResponse(data, totalElements, totalPages, page, size);
    }

    private RankingDto.PagedRankingResponse getFromRedis(String scope, String date, int page, int size, Long memberId) {
        String prefix = resolveDailyPrefix(memberId);

        long totalElements;
        List<RankingRedisRepository.RankingEntry> entries;

        try {
            long rawTotal = rankingRedisRepository.getTotalCount(prefix, date);
            totalElements = Math.min(rawTotal, MAX_RANKING_SIZE);

            long start = (long) page * size;
            int totalPages = (int) Math.ceil((double) totalElements / size);

            if (start >= totalElements) {
                return new RankingDto.PagedRankingResponse(List.of(), totalElements, totalPages, page, size);
            }

            long end = Math.min(start + size - 1, totalElements - 1);
            entries = rankingRedisRepository.getTopN(prefix, date, start, end);
        } catch (Exception e) {
            log.error("랭킹 Redis 조회 실패", e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "랭킹 서비스를 일시적으로 이용할 수 없습니다.");
        }

        List<Long> productIds = entries.stream()
            .map(RankingRedisRepository.RankingEntry::productId).toList();

        Map<Long, ProductWithBrand> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(pwb -> pwb.product().getId(), pwb -> pwb));

        List<RankingDto.RankingResponse> data = new ArrayList<>();
        long rank = (long) page * size + 1;
        for (RankingRedisRepository.RankingEntry entry : entries) {
            ProductWithBrand pwb = productMap.get(entry.productId());
            if (pwb != null) {
                Product product = pwb.product();
                data.add(new RankingDto.RankingResponse(
                    entry.productId(), product.getName(), pwb.brandName(),
                    product.getPrice().getValue(), rank, entry.score()
                ));
            }
            rank++;
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new RankingDto.PagedRankingResponse(data, totalElements, totalPages, page, size);
    }

    private String resolveDailyPrefix(Long memberId) {
        RankingProperties.Experiment experiment = properties.experiment();
        if (experiment.enabled() && !experiment.variants().isEmpty() && memberId != null) {
            List<String> variantKeys = new ArrayList<>(experiment.variants().keySet());
            int variantIndex = (int) (Math.abs(memberId) % variantKeys.size());
            String selectedKey = variantKeys.get(variantIndex);
            RankingProperties.Variant variant = experiment.variants().get(selectedKey);
            return variant.zsetPrefix();
        }
        return DAILY_ZSET_PREFIX;
    }
}
