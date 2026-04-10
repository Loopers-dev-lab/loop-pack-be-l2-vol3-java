package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.interfaces.api.ranking.RankingDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final RankingRedisRepository rankingRedisRepository;
    private final ProductRepository productRepository;

    public RankingDto.PagedRankingResponse getRankings(String date, int page, int size) {
        String resolvedDate = (date != null) ? date : LocalDate.now(KST).format(DATE_FORMATTER);

        long totalElements;
        List<RankingRedisRepository.RankingEntry> entries;

        try {
            long rawTotal = rankingRedisRepository.getTotalCount(resolvedDate);
            totalElements = Math.min(rawTotal, MAX_RANKING_SIZE);

            long start = (long) page * size;
            int totalPages = (int) Math.ceil((double) totalElements / size);

            if (start >= totalElements) {
                return new RankingDto.PagedRankingResponse(List.of(), totalElements, totalPages, page, size);
            }

            long end = Math.min(start + size - 1, totalElements - 1);
            entries = rankingRedisRepository.getTopN(resolvedDate, start, end);
        } catch (Exception e) {
            log.error("랭킹 Redis 조회 실패", e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "랭킹 서비스를 일시적으로 이용할 수 없습니다.");
        }

        List<Long> productIds = entries.stream()
            .map(RankingRedisRepository.RankingEntry::productId)
            .toList();

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
}
