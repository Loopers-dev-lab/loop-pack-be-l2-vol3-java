package com.loopers.application.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingApplicationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;

    @Transactional(readOnly = true)
    public List<RankingProductView> getTop(int limit) {
        if (limit < 1 || limit > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "limit은 1 이상 100 이하여야 합니다.");
        }
        return rankingRepository.findTop(LocalDate.now(KOREA_ZONE), limit);
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
