package com.loopers.domain.orderqueue;

import java.util.List;

public interface OrderQueueRepository {

    void upsert(String memberId, long enteredAt);

    Long rank(String memberId);

    List<String> peek(int limit);

    void remove(String memberId);
}
