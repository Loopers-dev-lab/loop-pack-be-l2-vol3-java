package com.loopers.application.product;

import com.loopers.contract.kafka.LikeCountChangedMessage;
import com.loopers.infrastructure.product.LikeEventHandledRepository;
import com.loopers.infrastructure.product.ProductLikeCountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductLikeCountConsumerService {

    private final LikeEventHandledRepository likeEventHandledRepository;
    private final ProductLikeCountRepository productLikeCountRepository;

    public ProductLikeCountConsumerService(
            LikeEventHandledRepository likeEventHandledRepository,
            ProductLikeCountRepository productLikeCountRepository
    ) {
        this.likeEventHandledRepository = likeEventHandledRepository;
        this.productLikeCountRepository = productLikeCountRepository;
    }

    @Transactional
    public void consume(String consumerGroup, LikeCountChangedMessage message) {
        boolean inserted = likeEventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        productLikeCountRepository.updateLikeCount(message.productId(), message.delta());
    }
}
