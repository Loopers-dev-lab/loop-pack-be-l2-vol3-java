package com.loopers.infrastructure.product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProductLikeCountRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void updateLikeCount(UUID productId, int delta) {
        entityManager.createNativeQuery(
                        """
                        UPDATE products
                        SET like_count = like_count + :delta,
                            updated_at = NOW(6)
                        WHERE reference_id = UUID_TO_BIN(:productId)
                        """
                )
                .setParameter("delta", delta)
                .setParameter("productId", productId.toString())
                .executeUpdate();
    }
}
