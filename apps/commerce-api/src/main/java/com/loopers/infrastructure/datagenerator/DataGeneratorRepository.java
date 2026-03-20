package com.loopers.infrastructure.datagenerator;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DataGeneratorRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("brandCount", countTable("brands", true));
        stats.put("productCount", countTable("products", true));
        stats.put("likeCount", countTable("likes", false));
        stats.put("userCount", countTable("users", false));
        stats.put("orderCount", countTable("orders", false));
        stats.put("couponCount", countTable("coupons", true));
        stats.put("ownedCouponCount", countTable("owned_coupons", false));
        return stats;
    }

    public int batchInsertLikes(List<long[]> userProductPairs) {
        if (userProductPairs.isEmpty()) {
            return 0;
        }
        String sql = "INSERT IGNORE INTO likes (user_id, product_id, created_at) VALUES (?, ?, NOW())";
        jdbcTemplate.batchUpdate(sql, userProductPairs, 1000,
                (PreparedStatement ps, long[] pair) -> {
                    ps.setLong(1, pair[0]);
                    ps.setLong(2, pair[1]);
                });
        return userProductPairs.size();
    }

    public int batchInsertUsers(String prefix, int count, String encodedPassword, long defaultPoint) {
        String sql = "INSERT INTO users (login_id, password, name, birth_date, email, point, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW())";
        LocalDate baseBirthDate = LocalDate.of(1995, 1, 1);
        LocalDateTime now = LocalDateTime.now();

        int created = 0;
        for (int i = 1; i <= count; i++) {
            String loginId = prefix + i;
            String name = prefix.substring(0, Math.min(prefix.length(), 4)) + i;
            if (name.length() < 2) name = "user" + i;
            if (name.length() > 10) name = name.substring(0, 10);
            String email = loginId + "@test.com";
            LocalDate birthDate = baseBirthDate.plusDays(i % 3650);

            try {
                jdbcTemplate.update(sql, loginId, encodedPassword, name, birthDate, email, defaultPoint);
                created++;
            } catch (Exception ignored) {
                // duplicate login_id, skip
            }
        }
        return created;
    }

    public List<Long> findAllUserIds() {
        return jdbcTemplate.queryForList("SELECT id FROM users", Long.class);
    }

    public List<Map<String, Object>> findRandomProducts(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id, price, stock FROM products WHERE deleted_at IS NULL AND stock > 0 ORDER BY RAND() LIMIT ?",
                limit);
    }

    public int batchInsertOwnedCoupons(Long couponId, String couponName, String discountType,
                                        long discountValue, Long minOrderAmount,
                                        ZonedDateTime expiredAt, List<Long> userIds) {
        if (userIds.isEmpty()) return 0;

        String sql = "INSERT IGNORE INTO owned_coupons "
                + "(coupon_id, coupon_name, discount_type, discount_value, min_order_amount, "
                + "expired_at, user_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        Timestamp expiredTimestamp = Timestamp.from(expiredAt.toInstant());

        jdbcTemplate.batchUpdate(sql, userIds, 500,
                (PreparedStatement ps, Long userId) -> {
                    ps.setLong(1, couponId);
                    ps.setString(2, couponName);
                    ps.setString(3, discountType);
                    ps.setLong(4, discountValue);
                    if (minOrderAmount != null) {
                        ps.setLong(5, minOrderAmount);
                    } else {
                        ps.setNull(5, java.sql.Types.BIGINT);
                    }
                    ps.setTimestamp(6, expiredTimestamp);
                    ps.setLong(7, userId);
                });
        return userIds.size();
    }

    public void updateCouponIssuedQuantity(Long couponId, int quantity) {
        jdbcTemplate.update(
                "UPDATE coupons SET issued_quantity = issued_quantity + ? WHERE id = ?",
                quantity, couponId);
    }

    public List<Long> findAllProductIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE deleted_at IS NULL", Long.class);
    }

    public List<Long> findAllBrandIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM brands WHERE deleted_at IS NULL", Long.class);
    }

    public List<Long> findDeletedBrandIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM brands WHERE deleted_at IS NOT NULL", Long.class);
    }

    public int batchInsertBrands(List<String> brandNames) {
        if (brandNames.isEmpty()) return 0;
        String sql = "INSERT IGNORE INTO brands (name, created_at, updated_at) VALUES (?, NOW(), NOW())";
        jdbcTemplate.batchUpdate(sql, brandNames, 100,
                (PreparedStatement ps, String name) -> ps.setString(1, name));
        return brandNames.size();
    }

    public int batchInsertProducts(List<Object[]> products) {
        if (products.isEmpty()) return 0;
        String sql = "INSERT INTO products (brand_id, name, price, stock, like_count, thumbnail_url, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 0, ?, 0, NOW(), NOW())";
        jdbcTemplate.batchUpdate(sql, products, 1000,
                (PreparedStatement ps, Object[] p) -> {
                    ps.setLong(1, (Long) p[0]);
                    ps.setString(2, (String) p[1]);
                    ps.setInt(3, (int) p[2]);
                    ps.setInt(4, (int) p[3]);
                    ps.setString(5, (String) p[4]);
                });
        return products.size();
    }

    public int batchInsertProductImages(List<Object[]> images) {
        if (images.isEmpty()) return 0;
        String sql = "INSERT INTO product_images (product_id, image_url, image_type, sort_order, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, NOW(), NOW())";
        jdbcTemplate.batchUpdate(sql, images, 1000,
                (PreparedStatement ps, Object[] img) -> {
                    ps.setLong(1, (Long) img[0]);
                    ps.setString(2, (String) img[1]);
                    ps.setString(3, (String) img[2]);
                    ps.setInt(4, (int) img[3]);
                });
        return images.size();
    }

    public long getMaxUserId() {
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(user_id), 0) FROM likes", Long.class);
        return maxId != null ? maxId : 0L;
    }

    public long getMaxOrderId() {
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM orders", Long.class);
        return maxId != null ? maxId : 0L;
    }

    public List<Map<String, Object>> findProductsForOrders(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT p.id, p.name AS product_name, p.price, b.name AS brand_name "
                        + "FROM products p JOIN brands b ON p.brand_id = b.id "
                        + "LEFT JOIN (SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id) lc "
                        + "ON p.id = lc.product_id "
                        + "WHERE p.deleted_at IS NULL AND p.stock > 0 "
                        + "ORDER BY COALESCE(lc.cnt, 0) DESC LIMIT ?",
                limit);
    }

    public void batchInsertOrders(List<Object[]> orders) {
        if (orders.isEmpty()) return;
        String sql = "INSERT INTO orders "
                + "(user_id, total_price, original_total_price, status, discount_amount, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())";
        jdbcTemplate.batchUpdate(sql, orders, 1000,
                (PreparedStatement ps, Object[] o) -> {
                    ps.setLong(1, (Long) o[0]);
                    ps.setInt(2, (int) o[1]);
                    ps.setInt(3, (int) o[2]);
                    ps.setString(4, (String) o[3]);
                    ps.setInt(5, (int) o[4]);
                });
    }

    public void batchInsertOrderItems(List<Object[]> items) {
        if (items.isEmpty()) return;
        String sql = "INSERT INTO order_items "
                + "(order_id, product_id, order_price, quantity, product_name, brand_name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        jdbcTemplate.batchUpdate(sql, items, 1000,
                (PreparedStatement ps, Object[] item) -> {
                    ps.setLong(1, (Long) item[0]);
                    ps.setLong(2, (Long) item[1]);
                    ps.setInt(3, (int) item[2]);
                    ps.setInt(4, (int) item[3]);
                    ps.setString(5, (String) item[4]);
                    ps.setString(6, (String) item[5]);
                    ps.setString(7, (String) item[6]);
                });
    }

    public long countAllInTable(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0L;
    }

    public void batchSoftDeleteBrands(List<Long> brandIds) {
        if (brandIds.isEmpty()) return;
        String placeholders = String.join(",", brandIds.stream().map(id -> "?").toList());
        jdbcTemplate.update(
                "UPDATE brands SET deleted_at = NOW() WHERE id IN (" + placeholders + ")",
                brandIds.toArray());
    }

    public void batchSoftDeleteProducts(List<Long> productIds) {
        if (productIds.isEmpty()) return;
        int batchSize = 10_000;
        for (int i = 0; i < productIds.size(); i += batchSize) {
            List<Long> batch = productIds.subList(i, Math.min(i + batchSize, productIds.size()));
            String placeholders = String.join(",", batch.stream().map(id -> "?").toList());
            jdbcTemplate.update(
                    "UPDATE products SET deleted_at = NOW() WHERE id IN (" + placeholders + ")",
                    batch.toArray());
        }
    }

    public List<Long> findProductIdsByBrandIds(List<Long> brandIds) {
        if (brandIds.isEmpty()) return List.of();
        String placeholders = String.join(",", brandIds.stream().map(id -> "?").toList());
        return jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE brand_id IN (" + placeholders + ")",
                Long.class,
                brandIds.toArray());
    }

    public List<Long> findActiveProductIdsSample(int limit, long seed) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE deleted_at IS NULL ORDER BY id LIMIT ? OFFSET ?",
                Long.class,
                limit, seed % 1000);
    }

    public List<Long> findOldestActiveProductIds(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE deleted_at IS NULL ORDER BY id ASC LIMIT ?",
                Long.class,
                limit);
    }

    public List<Long> findActiveProductIdsWithLikes(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM products WHERE deleted_at IS NULL AND like_count > 0 "
                        + "ORDER BY like_count ASC LIMIT ?",
                Long.class,
                limit);
    }

    public void syncLikeCounts() {
        jdbcTemplate.update(
                "UPDATE products p LEFT JOIN ("
                        + "SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id"
                        + ") lc ON p.id = lc.product_id "
                        + "SET p.like_count = COALESCE(lc.cnt, 0)");
    }

    private long countTable(String tableName, boolean hasSoftDelete) {
        String sql = hasSoftDelete
                ? "SELECT COUNT(*) FROM " + tableName + " WHERE deleted_at IS NULL"
                : "SELECT COUNT(*) FROM " + tableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
}
