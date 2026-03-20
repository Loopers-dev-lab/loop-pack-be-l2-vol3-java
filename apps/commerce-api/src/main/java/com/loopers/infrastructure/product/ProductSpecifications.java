package com.loopers.infrastructure.product;

import com.loopers.domain.product.query.ProductListCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<ProductEntity> from(ProductListCriteria criteria) {
        return from(criteria, false);
    }

    public static Specification<ProductEntity> from(ProductListCriteria criteria, boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!includeDeleted) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            } else if (criteria.deleted() != null) {
                predicates.add(Boolean.TRUE.equals(criteria.deleted())
                        ? cb.isNotNull(root.get("deletedAt"))
                        : cb.isNull(root.get("deletedAt")));
            }

            if (criteria.brandId() != null) {
                predicates.add(cb.equal(root.get("brandReferenceId"), criteria.brandId()));
            }
            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryReferenceId"), criteria.categoryId()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
