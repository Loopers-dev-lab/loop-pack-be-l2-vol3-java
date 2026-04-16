package com.loopers.domain.rank;

public enum RankPeriodType {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly"),
    QUARTERLY("mv_product_rank_quarterly");

    private final String tableName;

    RankPeriodType(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }
}
