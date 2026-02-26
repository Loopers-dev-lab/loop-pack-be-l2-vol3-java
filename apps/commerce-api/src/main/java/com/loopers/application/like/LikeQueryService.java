package com.loopers.application.like;

import com.loopers.application.like.LikeProductReadPort.LikeProductView;
import com.loopers.domain.model.product.ProductPricing;
import com.loopers.domain.model.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class LikeQueryService implements LikeQueryUseCase {

    private final LikeProductReadPort likeProductReadPort;

    public LikeQueryService(LikeProductReadPort likeProductReadPort) {
        this.likeProductReadPort = likeProductReadPort;
    }

    @Override
    public List<LikeInfo> getMyLikes(UserId userId, String sort, Boolean saleYn, String status) {
        List<LikeProductView> likes = likeProductReadPort.findLikedProductsByUserId(userId);

        Stream<LikeInfo> stream = likes.stream()
                .map(lp -> {
                    boolean onSale = lp.salePrice() != null;
                    int discountRate = ProductPricing.calculateDiscountRate(lp.price(), lp.salePrice());
                    boolean soldOut = lp.stockQuantity() == 0;
                    return new LikeInfo(
                            lp.productId(), lp.productName(), lp.price(), lp.salePrice(),
                            onSale, discountRate, lp.brandName(), soldOut, lp.likedAt()
                    );
                });

        if (Boolean.TRUE.equals(saleYn)) {
            stream = stream.filter(LikeInfo::onSale);
        }
        if ("selling".equals(status)) {
            stream = stream.filter(info -> !info.soldOut());
        }

        Comparator<LikeInfo> comparator = switch (sort != null ? sort : "latest") {
            case "price_asc" -> Comparator.comparingInt(LikeInfo::price);
            case "discount_rate_desc" -> Comparator.comparingInt(LikeInfo::discountRate).reversed();
            case "brand_name_asc" -> Comparator.comparing(LikeInfo::brandName);
            default -> Comparator.comparing(LikeInfo::likedAt).reversed();
        };

        return stream.sorted(comparator).toList();
    }
}
