package com.loopers.application.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingApplicationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;

    @Transactional(readOnly = true)
    public List<RankingProductView> getDailyPage(LocalDate metricDate, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return rankingRepository.findDailyPage(metricDate, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingProductView> getHourlyPage(LocalDateTime metricHour, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return rankingRepository.findHourlyPage(metricHour, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingProductView> getWeeklyPage(LocalDate periodStartDate, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return rankingRepository.findWeeklyPage(periodStartDate, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingProductView> getMonthlyPage(LocalDate periodStartDate, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
        return rankingRepository.findMonthlyPage(periodStartDate, page, size);
    }

    @Transactional(readOnly = true)
    public RankingProductView getProductRank(UUID productId) {
        RankingProductView rankingView = rankingRepository.findProductRank(LocalDate.now(KOREA_ZONE), productId);
        if (rankingView != null) {
            return rankingView;
        }
        return new RankingProductView(productId, null, null);
    }
}
