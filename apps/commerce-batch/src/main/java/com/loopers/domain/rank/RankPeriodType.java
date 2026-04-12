package com.loopers.domain.rank;

public enum RankPeriodType {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly");

    private final String tableName;

    RankPeriodType(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }
}
