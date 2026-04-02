package com.loopers.domain.orderqueue;

public interface OrderQueueRepository {

    void upsert(String memberId, long enteredAt);

    Long rank(String memberId);
}
