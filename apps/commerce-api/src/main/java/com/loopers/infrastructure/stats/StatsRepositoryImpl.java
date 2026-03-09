package com.loopers.infrastructure.stats;

import com.loopers.domain.like.QLikeModel;
import com.loopers.domain.order.QOrderItemModel;
import com.loopers.domain.order.QOrderModel;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.QProductStockModel;
import com.loopers.domain.stats.StatsProjection;
import com.loopers.domain.stats.StatsRepository;
import com.loopers.support.enums.OrderStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTemplate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 도메인 {@link StatsRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * QueryDSL을 사용하여 운영 통계 데이터를 조회한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class StatsRepositoryImpl implements StatsRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 기간별 주문 현황 통계 개요를 조회한다.
     *
     * <p>결제 대기, 취소, 만료 건수를 각각 집계하여 반환한다.</p>
     *
     * @param startAt 조회 시작 일자
     * @param endAt   조회 종료 일자
     * @return 주문 현황 통계 개요
     */
    @Override
    public StatsProjection.Overview getOverview(LocalDate startAt, LocalDate endAt) {
        QOrderModel order = QOrderModel.orderModel;
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());

        StatsProjection.Overview result = queryFactory
                .select(Projections.constructor(StatsProjection.Overview.class,
                        new CaseBuilder()
                                .when(order.status.eq(OrderStatus.PENDING_PAYMENT)).then(1L)
                                .otherwise(0L).sum(),
                        new CaseBuilder()
                                .when(order.status.eq(OrderStatus.CANCELLED)).then(1L)
                                .otherwise(0L).sum(),
                        new CaseBuilder()
                                .when(order.status.eq(OrderStatus.EXPIRED)).then(1L)
                                .otherwise(0L).sum()
                ))
                .from(order)
                .where(order.delYn.eq("N"),
                        order.createdAt.goe(start),
                        order.createdAt.lt(end))
                .fetchOne();

        if (result == null) {
            return StatsProjection.Overview.builder()
                    .pendingCount(0).cancelledCount(0).expiredCount(0).build();
        }
        return result;
    }

    /**
     * 기간별 일별 주문 통계를 조회한다.
     *
     * <p>일자별로 주문 건수와 총 금액을 집계하여 반환한다.</p>
     *
     * @param startAt 조회 시작 일자
     * @param endAt   조회 종료 일자
     * @return 일별 주문 통계 목록
     */
    @Override
    public List<StatsProjection.DailyOrderStat> getDailyOrderStats(LocalDate startAt, LocalDate endAt) {
        QOrderModel order = QOrderModel.orderModel;
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        DateTemplate<LocalDate> dateExpr = Expressions.dateTemplate(
                LocalDate.class, "CAST({0} AS DATE)", order.createdAt);

        return queryFactory
                .select(Projections.constructor(StatsProjection.DailyOrderStat.class,
                        dateExpr,
                        order.count(),
                        order.totalAmount.sum()
                ))
                .from(order)
                .where(order.delYn.eq("N"),
                        order.createdAt.goe(start),
                        order.createdAt.lt(end))
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch();
    }

    /**
     * 좋아요 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 좋아요 수 기준 상위 상품 통계 목록
     */
    @Override
    public List<StatsProjection.ProductStat> getTopLikedProducts(int limit) {
        QLikeModel like = QLikeModel.likeModel;
        QProductModel product = QProductModel.productModel;

        return queryFactory
                .select(Projections.constructor(StatsProjection.ProductStat.class,
                        product.productId, product.productName, like.count()))
                .from(like)
                .join(product).on(like.productId.eq(product.productId))
                .where(product.delYn.eq("N"))
                .groupBy(product.productId, product.productName)
                .orderBy(like.count().desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 주문 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상위 상품 수
     * @return 주문 수 기준 상위 상품 통계 목록
     */
    @Override
    public List<StatsProjection.ProductStat> getTopOrderedProducts(int limit) {
        QOrderItemModel orderItem = QOrderItemModel.orderItemModel;

        return queryFactory
                .select(Projections.constructor(StatsProjection.ProductStat.class,
                        orderItem.productId,
                        orderItem.snapshotProductName,
                        orderItem.count()))
                .from(orderItem)
                .where(orderItem.delYn.eq("N"))
                .groupBy(orderItem.productId, orderItem.snapshotProductName)
                .orderBy(orderItem.count().desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 재고 부족 상품 목록을 조회한다.
     *
     * <p>가용 재고(on_hand - reserved)가 임계값 이하인 상품을 가용 재고 오름차순으로 반환한다.</p>
     *
     * @param threshold 재고 부족 판단 임계값
     * @return 재고 부족 상품 목록 (가용 재고 오름차순)
     */
    @Override
    public List<StatsProjection.LowStockProduct> getLowStockProducts(int threshold) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        QProductModel product = QProductModel.productModel;

        return queryFactory
                .select(Projections.constructor(StatsProjection.LowStockProduct.class,
                        product.productId, product.productName,
                        stock.onHand, stock.reserved,
                        stock.onHand.subtract(stock.reserved)))
                .from(stock)
                .join(product).on(stock.productId.eq(product.productId))
                .where(product.delYn.eq("N"),
                        stock.onHand.subtract(stock.reserved).loe(threshold))
                .orderBy(stock.onHand.subtract(stock.reserved).asc())
                .fetch();
    }
}
