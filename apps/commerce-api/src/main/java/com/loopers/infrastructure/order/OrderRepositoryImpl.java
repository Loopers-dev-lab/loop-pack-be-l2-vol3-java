package com.loopers.infrastructure.order;

import com.loopers.domain.common.CursorResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderMapper orderMapper;
    private final JPAQueryFactory queryFactory;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository, OrderMapper orderMapper,
                                JPAQueryFactory queryFactory) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderMapper = orderMapper;
        this.queryFactory = queryFactory;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = orderMapper.toEntity(order);
        OrderEntity saved = orderJpaRepository.save(entity);
        return orderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdWithItems(id)
                .map(orderMapper::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findOrdersByUserIdAndCreatedAtBetween(userId, startAt, endAt)
                .stream()
                .map(orderMapper::toDomainWithoutItems)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAllByUserIdWithItems(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<Long> ids = orderJpaRepository.findOrdersByUserIdAndCreatedAtBetween(userId, startAt, endAt)
                .stream()
                .map(OrderEntity::getId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return List.of();
        }

        return orderJpaRepository.findAllByIdInWithItems(ids)
                .stream()
                .map(orderMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public CursorResult<Order> findAllByUserIdWithCursor(Long userId, ZonedDateTime startAt, ZonedDateTime endAt,
                                                          ZonedDateTime cursorCreatedAt, Long cursorId, int size) {
        QOrderEntity order = QOrderEntity.orderEntity;

        List<Order> fetched = queryFactory
                .selectFrom(order)
                .where(
                        order.userId.eq(userId),
                        order.createdAt.goe(startAt),
                        order.createdAt.lt(endAt),
                        orderCursorCondition(order, cursorCreatedAt, cursorId)
                )
                .orderBy(order.createdAt.desc(), order.id.desc())
                .limit(size + 1)
                .fetch()
                .stream()
                .map(orderMapper::toDomainWithoutItems)
                .toList();

        return CursorResult.of(fetched, size);
    }

    private BooleanExpression orderCursorCondition(QOrderEntity order, ZonedDateTime cursorCreatedAt, Long cursorId) {
        if (cursorCreatedAt == null || cursorId == null) {
            return null;
        }
        return order.createdAt.lt(cursorCreatedAt)
                .or(order.createdAt.eq(cursorCreatedAt).and(order.id.lt(cursorId)));
    }

    @Override
    public List<Order> findAll(int page, int size) {
        return orderJpaRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(orderMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return orderJpaRepository.count();
    }
}
