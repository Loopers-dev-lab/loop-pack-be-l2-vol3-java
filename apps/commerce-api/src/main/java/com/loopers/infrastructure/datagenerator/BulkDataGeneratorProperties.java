package com.loopers.infrastructure.datagenerator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.data-generator")
public record BulkDataGeneratorProperties(
        boolean enabled,
        int brandCount,
        int productCount,
        int userCount,
        int likeCount,
        int orderCount,
        SoftDelete softDelete) {

    public BulkDataGeneratorProperties {
        if (brandCount <= 0) brandCount = 100;
        if (productCount <= 0) productCount = 100_000;
        if (userCount <= 0) userCount = 10_000;
        if (likeCount <= 0) likeCount = 500_000;
        if (orderCount <= 0) orderCount = 100_000;
        if (softDelete == null) softDelete = new SoftDelete(10, 3, 2);
    }

    public record SoftDelete(
            int brandPercent,
            int productWithoutLikePercent,
            int productWithLikePercent) {

        public SoftDelete {
            if (brandPercent <= 0) brandPercent = 10;
            if (productWithoutLikePercent <= 0) productWithoutLikePercent = 3;
            if (productWithLikePercent <= 0) productWithLikePercent = 2;
        }
    }
}
