package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 재고 도메인 서비스.
 * <p>
 * 재고 생성, 조회 및 CAS(Compare-And-Set) 기반 재고 예약(hold), 해제(release), 확정(commit)을 담당한다.
 * 모든 재고 변경 연산은 조건부 UPDATE로 수행되어 오버셀(초과 판매)을 방지하며,
 * 다건 주문 시 productId 오름차순 정렬로 데드락을 방지한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final ProductStockRepository productStockRepository;

    /**
     * 상품의 초기 재고를 생성한다.
     *
     * @param productId 상품 ID
     * @param onHand    초기 총 재고 수량
     * @return 생성된 재고 엔티티
     */
    @Transactional
    public ProductStockModel createStock(Long productId, int onHand) {
        ProductStockModel stock = ProductStockModel.create(productId, onHand);
        return productStockRepository.save(stock);
    }

    /**
     * 상품 ID로 재고를 조회한다.
     *
     * @param productId 상품 ID
     * @return 재고 엔티티
     * @throws CoreException 재고가 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    /**
     * 상품 ID 목록으로 재고를 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 재고 엔티티 목록
     */
    public List<ProductStockModel> findAllByProductIds(Collection<Long> productIds) {
        return productStockRepository.findAllByProductIds(productIds);
    }

    public ProductStockModel findByProductId(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    /**
     * CAS(Compare-And-Set) 방식으로 재고를 예약(hold)한다.
     * <p>
     * 조건부 UPDATE({@code SET reserved += :qty WHERE (on_hand - reserved) >= :qty})로
     * 가용 재고가 충분한 경우에만 예약이 수행된다.
     * 영향받은 행이 0이면 재고 부족으로 예외를 발생시킨다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       예약할 수량
     * @throws CoreException 가용 재고 부족 시 (STOCK_NOT_ENOUGH)
     */
    @Transactional
    public void hold(Long productId, int qty) {
        int affected = productStockRepository.reserveStock(productId, qty);
        if (affected == 0) {
            throw new CoreException(ErrorType.STOCK_NOT_ENOUGH);
        }
    }

    /**
     * CAS(Compare-And-Set) 방식으로 예약된 재고를 해제(release)한다.
     * <p>
     * 조건부 UPDATE({@code SET reserved -= :qty WHERE reserved >= :qty})로
     * 예약 재고가 충분한 경우에만 해제가 수행된다.
     * 주문 취소/만료 시 사용된다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       해제할 수량
     * @throws CoreException 예약 재고 부족 시 (STOCK_NOT_ENOUGH)
     */
    @Transactional
    public void release(Long productId, int qty) {
        int affected = productStockRepository.releaseStock(productId, qty);
        if (affected == 0) {
            throw new CoreException(ErrorType.STOCK_NOT_ENOUGH);
        }
    }

    /**
     * CAS(Compare-And-Set) 방식으로 예약된 재고를 확정(commit)한다.
     * <p>
     * 결제 완료 시 예약 재고를 실제 출고로 확정하는 연산이다.
     * {@code reserved -= :qty, on_hand -= :qty} 형태로 동시에 차감한다.
     * </p>
     *
     * @param productId 상품 ID
     * @param qty       확정할 수량
     * @throws CoreException 확정 실패 시 (STOCK_NOT_ENOUGH)
     */
    @Transactional
    public void commit(Long productId, int qty) {
        int affected = productStockRepository.commitStock(productId, qty);
        if (affected == 0) {
            throw new CoreException(ErrorType.STOCK_NOT_ENOUGH);
        }
    }
}
