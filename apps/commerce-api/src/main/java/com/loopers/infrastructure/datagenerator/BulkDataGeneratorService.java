package com.loopers.infrastructure.datagenerator;

import com.loopers.domain.user.PasswordEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkDataGeneratorService {

    private final DataGeneratorRepository dataGeneratorRepository;
    private final PasswordEncoder passwordEncoder;
    private final BulkDataGeneratorProperties properties;
    private final CacheManager cacheManager;

    private volatile boolean running = false;

    public boolean isRunning() {
        return running;
    }

    public void generateAll() {
        if (running) {
            log.warn("BulkDataGenerator is already running. Skipping.");
            return;
        }
        running = true;
        try {
            log.info("=== BulkDataGenerator START === brands={}, products={}, users={}, likes={}, orders={}",
                    properties.brandCount(), properties.productCount(),
                    properties.userCount(), properties.likeCount(), properties.orderCount());

            long totalStart = System.currentTimeMillis();

            long totalBrands = dataGeneratorRepository.countAllInTable("brands");
            long totalProducts = dataGeneratorRepository.countAllInTable("products");
            long totalUsers = dataGeneratorRepository.countAllInTable("users");
            long totalLikes = dataGeneratorRepository.countAllInTable("likes");
            long totalOrders = dataGeneratorRepository.countAllInTable("orders");

            // Phase 1: Brands
            if (totalBrands < properties.brandCount()) {
                generateBrands();
                totalBrands = dataGeneratorRepository.countAllInTable("brands");
            } else {
                log.info("  Phase 1: Brands {} already exist. Skipping.", totalBrands);
            }

            // Phase 1.5: Soft delete 10% brands
            if (dataGeneratorRepository.findDeletedBrandIds().isEmpty() && totalBrands >= properties.brandCount()) {
                List<Long> activeBrandIds = dataGeneratorRepository.findAllBrandIds();
                softDeleteBrands(activeBrandIds);
            } else {
                log.info("  Phase 1.5: Brand soft-delete already applied. Skipping.");
            }

            // Phase 2: Products
            if (totalProducts < properties.productCount()) {
                generateProducts(dataGeneratorRepository.findAllBrandIds());
                totalProducts = dataGeneratorRepository.countAllInTable("products");
            } else {
                log.info("  Phase 2: Products {} already exist. Skipping.", totalProducts);
            }

            // Phase 2.5: Soft delete products Wave 1
            if (totalProducts >= properties.productCount()
                    && dataGeneratorRepository.getStats().get("productCount") >= properties.productCount()) {
                softDeleteProductsWave1();
            } else if (totalProducts >= properties.productCount()) {
                log.info("  Phase 2.5: Product soft-delete Wave 1 already applied. Skipping.");
            }

            // Phase 2.7: Product Images
            long totalProductImages = dataGeneratorRepository.countAllInTable("product_images");
            if (totalProductImages == 0 && totalProducts >= properties.productCount()) {
                generateProductImages();
            } else {
                log.info("  Phase 2.7: Product images {} already exist. Skipping.", totalProductImages);
            }

            // Phase 3: Users
            if (totalUsers < properties.userCount()) {
                generateUsers();
            } else {
                log.info("  Phase 3: Users {} already exist. Skipping.", totalUsers);
            }

            // Phase 4: Likes
            if (totalLikes < properties.likeCount()) {
                generateLikes();
            } else {
                log.info("  Phase 4: Likes {} already exist. Skipping.", totalLikes);
            }

            // Phase 4.5: Soft delete products Wave 2
            long deletedProducts = totalProducts - dataGeneratorRepository.getStats().get("productCount");
            // Wave 1 삭제 ~13K, Wave 2 추가 2K = 총 ~15K
            if (totalProducts >= properties.productCount() && deletedProducts > 0 && deletedProducts < 14_000) {
                softDeleteProductsWave2();
            } else if (deletedProducts >= 14_000) {
                log.info("  Phase 4.5: Product soft-delete Wave 2 already applied. Skipping.");
            }

            // Phase 5: Orders
            if (totalOrders < properties.orderCount()) {
                generateOrders();
            } else {
                log.info("  Phase 5: Orders {} already exist. Skipping.", totalOrders);
            }

            // Phase 6: Ranking Scores
            if (totalProducts >= properties.productCount()) {
                generateRankingScores();
            }

            // Phase 7: Clear all caches (Redis에 이전 세션 캐시 제거)
            evictAllCaches();

            Map<String, Long> finalStats = dataGeneratorRepository.getStats();
            log.info("=== BulkDataGenerator DONE === elapsed={}s | brands={}, products={}, users={}, likes={}, orders={}",
                    elapsed(totalStart),
                    finalStats.get("brandCount"), finalStats.get("productCount"),
                    finalStats.get("userCount"), finalStats.get("likeCount"), finalStats.get("orderCount"));
        } finally {
            running = false;
        }
    }

    private List<Long> generateBrands() {
        long start = System.currentTimeMillis();
        dataGeneratorRepository.batchInsertBrands(
                FashionDataPool.BRAND_NAMES.subList(
                        0, Math.min(properties.brandCount(), FashionDataPool.BRAND_NAMES.size())));
        List<Long> brandIds = dataGeneratorRepository.findAllBrandIds();
        log.info("  Phase 1: Brands {} created ({}s)", brandIds.size(), elapsed(start));
        return brandIds;
    }

    private void softDeleteBrands(List<Long> allBrandIds) {
        long start = System.currentTimeMillis();
        int deleteCount = allBrandIds.size() * properties.softDelete().brandPercent() / 100;
        dataGeneratorRepository.batchSoftDeleteBrands(
                allBrandIds.subList(allBrandIds.size() - deleteCount, allBrandIds.size()));
        log.info("  Phase 1.5: Brands {} soft-deleted ({}s)", deleteCount, elapsed(start));
    }

    private void generateProducts(List<Long> activeBrandIds) {
        if (activeBrandIds.isEmpty()) {
            log.warn("  Phase 2: No active brands available. Skipping product generation.");
            return;
        }

        long start = System.currentTimeMillis();
        Random random = new Random(42);
        int totalProducts = properties.productCount();
        int batchSize = 10_000;

        // 브랜드별 상품 할당량 계산
        int[] productsPerBrand = new int[activeBrandIds.size()];
        int allocated = 0;
        for (int i = 0; i < activeBrandIds.size(); i++) {
            productsPerBrand[i] = FashionDataPool.productsPerBrand(i, totalProducts, random);
            allocated += productsPerBrand[i];
        }

        // 총합을 totalProducts에 맞춤
        double scale = (double) totalProducts / allocated;
        allocated = 0;
        for (int i = 0; i < productsPerBrand.length; i++) {
            productsPerBrand[i] = Math.max(1, (int) (productsPerBrand[i] * scale));
            allocated += productsPerBrand[i];
        }
        // 나머지를 첫 번째 브랜드에 조정
        productsPerBrand[0] += totalProducts - allocated;

        List<Object[]> batch = new ArrayList<>(batchSize);
        int created = 0;

        for (int brandIdx = 0; brandIdx < activeBrandIds.size(); brandIdx++) {
            Long brandId = activeBrandIds.get(brandIdx);
            int count = productsPerBrand[brandIdx];

            for (int j = 0; j < count; j++) {
                FashionDataPool.Category category = FashionDataPool.pickCategory(random);
                String name = FashionDataPool.generateProductName(category, random);
                int price = FashionDataPool.generatePrice(category, random);
                int stock = FashionDataPool.generateStock(category, random);

                batch.add(new Object[]{brandId, name, price, stock, "/image.png"});

                if (batch.size() >= batchSize) {
                    dataGeneratorRepository.batchInsertProducts(batch);
                    created += batch.size();
                    batch.clear();
                    log.info("  Phase 2: Products {}/{} ({}s)", created, totalProducts, elapsed(start));
                }
            }
        }

        if (!batch.isEmpty()) {
            dataGeneratorRepository.batchInsertProducts(batch);
            created += batch.size();
            batch.clear();
        }

        log.info("  Phase 2: Products {} created ({}s)", created, elapsed(start));
    }

    private void generateProductImages() {
        long start = System.currentTimeMillis();
        List<Long> productIds = dataGeneratorRepository.findAllProductIds();
        if (productIds.isEmpty()) {
            log.warn("  Phase 2.7: No active products. Skipping image generation.");
            return;
        }

        String imageUrl = "/image.png";
        int batchSize = 10_000;
        List<Object[]> batch = new ArrayList<>(batchSize);
        int created = 0;

        for (Long productId : productIds) {
            // MAIN 이미지 2장
            for (int i = 0; i < 2; i++) {
                batch.add(new Object[]{productId, imageUrl, "MAIN", i});
            }
            // DETAIL 이미지 3장
            for (int i = 0; i < 3; i++) {
                batch.add(new Object[]{productId, imageUrl, "DETAIL", i});
            }

            if (batch.size() >= batchSize) {
                dataGeneratorRepository.batchInsertProductImages(batch);
                created += batch.size();
                batch.clear();
                log.info("  Phase 2.7: Product images {}/{} ({}s)",
                        created, productIds.size() * 5, elapsed(start));
            }
        }

        if (!batch.isEmpty()) {
            dataGeneratorRepository.batchInsertProductImages(batch);
            created += batch.size();
            batch.clear();
        }

        log.info("  Phase 2.7: Product images {} created ({}s)", created, elapsed(start));
    }

    private void softDeleteProductsWave1() {
        long start = System.currentTimeMillis();

        // 삭제된 브랜드 소속 상품 연쇄 삭제
        List<Long> deletedBrandProductIds = findDeletedBrandProductIds();
        if (!deletedBrandProductIds.isEmpty()) {
            dataGeneratorRepository.batchSoftDeleteProducts(deletedBrandProductIds);
            log.info("  Phase 2.5: Cascade-deleted {} products from deleted brands ({}s)",
                    deletedBrandProductIds.size(), elapsed(start));
        }

        // 활성 브랜드의 오래된 시즌 상품 단종
        int beforeLikeCount = properties.productCount() * properties.softDelete().productWithoutLikePercent() / 100;
        List<Long> oldProductIds = dataGeneratorRepository.findOldestActiveProductIds(beforeLikeCount);
        if (!oldProductIds.isEmpty()) {
            dataGeneratorRepository.batchSoftDeleteProducts(oldProductIds);
            log.info("  Phase 2.5: Discontinued {} old season products ({}s)",
                    oldProductIds.size(), elapsed(start));
        }

        log.info("  Phase 2.5: Product soft-delete Wave 1 completed ({}s)", elapsed(start));
    }

    private List<Long> findDeletedBrandProductIds() {
        // 삭제된 브랜드의 ID 조회 → 해당 브랜드 상품 ID 조회
        List<Long> deletedBrandIds = dataGeneratorRepository.findDeletedBrandIds();
        if (deletedBrandIds.isEmpty()) return List.of();
        return dataGeneratorRepository.findProductIdsByBrandIds(deletedBrandIds);
    }

    private void softDeleteProductsWave2() {
        long start = System.currentTimeMillis();
        // 좋아요가 있지만 단종된 상품 (like_count 낮은 순)
        int afterLikeCount = properties.productCount() * properties.softDelete().productWithLikePercent() / 100;
        List<Long> productsWithLikes = dataGeneratorRepository.findActiveProductIdsWithLikes(afterLikeCount);
        if (!productsWithLikes.isEmpty()) {
            dataGeneratorRepository.batchSoftDeleteProducts(productsWithLikes);
            log.info("  Phase 4.5: Discontinued {} products with likes ({}s)",
                    productsWithLikes.size(), elapsed(start));
        }
        log.info("  Phase 4.5: Product soft-delete Wave 2 completed ({}s)", elapsed(start));
    }

    private void generateUsers() {
        long start = System.currentTimeMillis();
        String encodedPassword = passwordEncoder.encode("Test1234!");
        int created = dataGeneratorRepository.batchInsertUsers(
                "bulk", properties.userCount(), encodedPassword, 1_000_000L);
        log.info("  Phase 3: Users {} created ({}s)", created, elapsed(start));
    }

    private void generateLikes() {
        long start = System.currentTimeMillis();
        List<Long> productIds = dataGeneratorRepository.findAllProductIds();
        List<Long> userIds = dataGeneratorRepository.findAllUserIds();

        if (productIds.isEmpty() || userIds.isEmpty()) {
            log.warn("  Phase 4: No products or users. Skipping like generation.");
            return;
        }

        int totalLikes = properties.likeCount();

        // Zipf distribution (exponent 1.2): 상위 1% 상품이 ~70% 좋아요 차지
        double[] weights = new double[productIds.size()];
        double sumWeights = 0;
        for (int i = 0; i < productIds.size(); i++) {
            weights[i] = 1.0 / Math.pow(i + 1, 1.2);
            sumWeights += weights[i];
        }

        int[] likesPerProduct = new int[productIds.size()];
        int assigned = 0;
        for (int i = 0; i < productIds.size() && assigned < totalLikes; i++) {
            likesPerProduct[i] = Math.min(
                    (int) (weights[i] / sumWeights * totalLikes),
                    userIds.size());
            assigned += likesPerProduct[i];
        }
        for (int i = 0; assigned < totalLikes && i < productIds.size(); i++) {
            if (likesPerProduct[i] < userIds.size()) {
                likesPerProduct[i]++;
                assigned++;
            }
        }

        // Generate pairs in chunks
        Random random = new Random(42);
        int chunkSize = 50_000;
        List<long[]> chunk = new ArrayList<>(chunkSize);
        int totalCreated = 0;

        for (int i = 0; i < productIds.size(); i++) {
            int count = likesPerProduct[i];
            if (count == 0) continue;

            long productId = productIds.get(i);
            Set<Integer> selectedUserIndices = new HashSet<>();
            int attempts = 0;
            while (selectedUserIndices.size() < count && attempts < count * 3) {
                selectedUserIndices.add(random.nextInt(userIds.size()));
                attempts++;
            }

            for (int userIndex : selectedUserIndices) {
                chunk.add(new long[]{userIds.get(userIndex), productId});

                if (chunk.size() >= chunkSize) {
                    dataGeneratorRepository.batchInsertLikes(chunk);
                    totalCreated += chunk.size();
                    chunk.clear();
                    log.info("  Phase 4: Likes {}/{} ({}s)", totalCreated, totalLikes, elapsed(start));
                }
            }
        }

        if (!chunk.isEmpty()) {
            dataGeneratorRepository.batchInsertLikes(chunk);
            totalCreated += chunk.size();
            chunk.clear();
        }

        log.info("  Phase 4: Likes {} created ({}s)", totalCreated, elapsed(start));
    }

    private void generateOrders() {
        long start = System.currentTimeMillis();
        List<Long> userIds = dataGeneratorRepository.findAllUserIds();
        // 좋아요 순 정렬된 상품 풀 (인덱스 0 = 가장 인기 많은 상품)
        List<Map<String, Object>> productPool = dataGeneratorRepository.findProductsForOrders(5000);

        if (userIds.isEmpty() || productPool.isEmpty()) {
            log.warn("  Phase 5: No users or products. Skipping order generation.");
            return;
        }

        Random random = new Random(42);
        int totalOrders = properties.orderCount();
        int ordersPerUser = Math.max(1, totalOrders / userIds.size());
        int chunkSize = 1000;
        int created = 0;
        int poolSize = productPool.size();

        List<Object[]> orderBatch = new ArrayList<>(chunkSize);
        List<List<Object[]>> itemsPerOrder = new ArrayList<>(chunkSize);

        for (Long userId : userIds) {
            if (created + orderBatch.size() >= totalOrders) break;

            for (int o = 0; o < ordersPerUser && created + orderBatch.size() < totalOrders; o++) {
                int itemCount = 1 + random.nextInt(3);
                int totalPrice = 0;
                List<Object[]> items = new ArrayList<>(itemCount);

                for (int i = 0; i < itemCount; i++) {
                    // Power-law: 인기 상품(낮은 인덱스)일수록 더 자주 선택
                    int productIndex = (int) (Math.pow(random.nextDouble(), 1.5) * poolSize);
                    Map<String, Object> product = productPool.get(productIndex);
                    int price = ((Number) product.get("price")).intValue();
                    int qty = 1 + random.nextInt(3);
                    totalPrice += price * qty;
                    items.add(new Object[]{
                            ((Number) product.get("id")).longValue(),
                            price, qty,
                            (String) product.get("product_name"),
                            (String) product.get("brand_name"),
                            "ORDERED"});
                }

                orderBatch.add(new Object[]{userId, totalPrice, totalPrice, "ORDERED", 0});
                itemsPerOrder.add(items);
            }

            if (orderBatch.size() >= chunkSize) {
                created += flushOrderBatch(orderBatch, itemsPerOrder);
                log.info("  Phase 5: Orders {}/{} ({}s)", created, totalOrders, elapsed(start));
            }
        }

        if (!orderBatch.isEmpty()) {
            created += flushOrderBatch(orderBatch, itemsPerOrder);
        }

        log.info("  Phase 5: Orders {} created ({}s)", created, elapsed(start));
    }

    private int flushOrderBatch(List<Object[]> orderBatch, List<List<Object[]>> itemsPerOrder) {
        long maxId = dataGeneratorRepository.getMaxOrderId();
        dataGeneratorRepository.batchInsertOrders(orderBatch);

        List<Object[]> allItems = new ArrayList<>();
        for (int i = 0; i < orderBatch.size(); i++) {
            long orderId = maxId + 1 + i;
            for (Object[] item : itemsPerOrder.get(i)) {
                allItems.add(new Object[]{
                        orderId, item[0], item[1], item[2], item[3], item[4], item[5]});
            }
        }
        dataGeneratorRepository.batchInsertOrderItems(allItems);

        int size = orderBatch.size();
        orderBatch.clear();
        itemsPerOrder.clear();
        return size;
    }

    private void generateRankingScores() {
        LocalDate today = LocalDate.now();
        if (dataGeneratorRepository.countRankingScoresByDate(today) > 0) {
            log.info("  Phase 6: Ranking scores for {} already exist. Skipping.", today);
            return;
        }

        long start = System.currentTimeMillis();
        List<Long> productIds = dataGeneratorRepository.findAllProductIds();
        if (productIds.isEmpty()) {
            log.warn("  Phase 6: No active products. Skipping ranking score generation.");
            return;
        }

        Random random = new Random(42);
        double baseScore = 10000.0;
        int batchSize = 10_000;
        List<Object[]> batch = new ArrayList<>(batchSize);
        int created = 0;

        // Zipf 분포: 상위 상품일수록 높은 점수 + 약간의 랜덤 노이즈
        for (int i = 0; i < productIds.size(); i++) {
            double weight = 1.0 / Math.pow(i + 1, 0.8);
            double score = weight * baseScore + random.nextDouble() * 10;
            batch.add(new Object[]{productIds.get(i), today, score});

            if (batch.size() >= batchSize) {
                dataGeneratorRepository.batchInsertRankingScores(batch);
                created += batch.size();
                batch.clear();
                log.info("  Phase 6: Ranking scores {}/{} ({}s)",
                        created, productIds.size(), elapsed(start));
            }
        }

        if (!batch.isEmpty()) {
            dataGeneratorRepository.batchInsertRankingScores(batch);
            created += batch.size();
            batch.clear();
        }

        log.info("  Phase 6: Ranking scores {} created for {} ({}s)",
                created, today, elapsed(start));
    }

    private void evictAllCaches() {
        long start = System.currentTimeMillis();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        log.info("  Phase 7: All caches evicted ({}s)", elapsed(start));
    }

    private String elapsed(long startMs) {
        return String.format("%.1f", (System.currentTimeMillis() - startMs) / 1000.0);
    }
}
