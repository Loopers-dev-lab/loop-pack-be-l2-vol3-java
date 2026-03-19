package com.loopers.application.stock;

import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    // Command

    @Transactional
    public void reserve(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.reserveIfAvailable(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족하거나 존재하지 않는 상품입니다");
            }
        }
    }

    @Transactional
    public void confirm(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.confirmIfReserved(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "점유된 재고가 부족합니다");
            }
        }
    }

    @Transactional
    public void releaseReserved(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.releaseReservedIfEnough(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "해제할 점유 재고가 부족합니다");
            }
        }
    }

    @Transactional
    public void releaseConfirmed(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.releaseConfirmedIfEnough(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "복원할 확정 재고가 부족합니다");
            }
        }
    }

    // Query

    @Transactional(readOnly = true)
    public Set<Long> findProductIdsWithReservedStock() {
        return stockRepository.findProductIdsWithReservedStock();
    }
}
