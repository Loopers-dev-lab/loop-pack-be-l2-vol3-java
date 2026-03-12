package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Entity
@Table(name = "likes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_likes_user_product", columnNames = {"user_id", "product_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLikeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private ProductLikeModel(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
        this.createdAt =  ZonedDateTime.now();
    }

    public static ProductLikeModel create(Long userId, Long productId) {
        validate(userId,productId);
        return new ProductLikeModel(userId,productId);
    }

    private static void validate(Long userId, Long productId) {
        if(userId == null){
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수값입니다.");
        }
        if(productId== null){
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수값입니다.");
        }
    }
}
