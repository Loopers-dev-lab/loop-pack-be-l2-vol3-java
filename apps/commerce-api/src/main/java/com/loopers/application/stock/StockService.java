package com.loopers.application.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    // Command

    @Transactional
    public void reserve(Map<Long, Integer> productQuantities) {
        List<Stock> stocks = stockRepository.findAllByProductIdInForUpdate(productQuantities.keySet());

        if (stocks.size() != productQuantities.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 정보가 존재하지 않습니다");
        }

        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Stock stock = stockMap.get(entry.getKey());
            stock.reserve(entry.getValue());
        }
    }

    @Transactional
    public void confirm(Map<Long, Integer> productQuantities) {
        List<Stock> stocks = stockRepository.findAllByProductIdInForUpdate(productQuantities.keySet());

        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Stock stock = stockMap.get(entry.getKey());
            stock.confirm(entry.getValue());
        }
    }

    @Transactional
    public void releaseReserved(Map<Long, Integer> productQuantities) {
        List<Stock> stocks = stockRepository.findAllByProductIdInForUpdate(productQuantities.keySet());

        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Stock stock = stockMap.get(entry.getKey());
            stock.releaseReserved(entry.getValue());
        }
    }

    // Query

    @Transactional(readOnly = true)
    public Set<Long> findProductIdsWithReservedStock() {
        return stockRepository.findProductIdsWithReservedStock();
    }

    @Transactional
    public void releaseConfirmed(Map<Long, Integer> productQuantities) {
        List<Stock> stocks = stockRepository.findAllByProductIdInForUpdate(productQuantities.keySet());

        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Stock stock = stockMap.get(entry.getKey());
            stock.releaseConfirmed(entry.getValue());
        }
    }
}
