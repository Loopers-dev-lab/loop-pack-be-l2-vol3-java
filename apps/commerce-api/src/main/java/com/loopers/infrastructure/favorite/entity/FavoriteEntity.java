package com.loopers.infrastructure.favorite.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.favorite.model.Favorite;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "favorite", uniqueConstraints =
        @UniqueConstraint(columnNames = {"memberId", "productId"}))
public class FavoriteEntity extends BaseEntity {

    @Comment("회원 id")
    @Column
    private Long memberId;

    @Comment("상품 id")
    @Column
    private Long productId;

    protected FavoriteEntity() {}

    private FavoriteEntity(Long memberId, Long productId) {
        this.memberId = memberId;
        this.productId = productId;
    }

    public static FavoriteEntity toEntity(Favorite favorite) {
        return new FavoriteEntity(favorite.getMemberId(), favorite.getProductId());
    }

    public Favorite toModel() {
        return Favorite.reconstruct(
                this.getId(),
                this.memberId,
                this.productId
        );
    }
}
