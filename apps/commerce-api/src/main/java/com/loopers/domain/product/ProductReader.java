package com.loopers.domain.product;

import static com.loopers.domain.product.ProductCacheConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.domain.shared.cache.CacheRepository;
import com.loopers.domain.shared.cache.CacheType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 캐시를 경유하여 상품을 조회하는 읽기 전용 도메인 서비스.
 *
 * <p>캐시 레이어링 전략을 사용한다:
 * <ul>
 *   <li>목록 캐시: ID 리스트 + hasNext만 저장</li>
 *   <li>상세 캐시: 상품 데이터의 단일 원본</li>
 * </ul>
 * 목록 조회 시 ID 리스트 캐시 → 상세 일괄 조회 → 부분 미스 시 DB fallback.</p>
 */
@DomainService
@RequiredArgsConstructor
public class ProductReader {

    private static final CacheType<ProductIdPage> ID_PAGE_TYPE = new CacheType<>() {};
    private static final String ALL_BRAND = "all";
    private static final int MAX_CACHEABLE_PAGE = 2;
    private static final long LOCK_TIMEOUT_SECONDS = 3;

    private final CacheRepository cacheRepository;
    private final ProductService productService;

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 활성 상품 목록을 페이지 단위로 조회한다.
     *
     * @param brandId  브랜드 ID (null이면 전체 브랜드)
     * @param sortType 정렬 기준
     * @param pageSize 페이지 정보
     * @return 활성 상품 목록 페이지
     */
    public Page<Product> readActiveProducts(Long brandId, ProductSortType sortType, PageSize pageSize) {
        ProductSortType resolvedSortType = sortType != null ? sortType : ProductSortType.DEFAULT;
        String listKey = buildListKey(brandId, resolvedSortType, pageSize);
        ProductIdPage idPage = cacheRepository.get(listKey, ID_PAGE_TYPE);

        if (Objects.nonNull(idPage)) {
            return resolveProductsFromIdPage(idPage);
        }

        ReentrantLock lock = locks.computeIfAbsent(listKey, k -> new ReentrantLock());
        if (tryLockWithTimeout(lock)) {
            try {
                ProductIdPage rechecked = cacheRepository.get(listKey, ID_PAGE_TYPE);
                if (Objects.nonNull(rechecked)) {
                    return resolveProductsFromIdPage(rechecked);
                }
                return fetchAndCacheProducts(brandId, resolvedSortType, pageSize);
            } finally {
                releaseLock(listKey, lock);
            }
        }

        return fetchAndCacheProducts(brandId, resolvedSortType, pageSize);
    }

    /**
     * 활성 상품 단건을 조회한다.
     *
     * @param productId 상품 ID
     * @return 활성 상품
     */
    public Product readActiveProduct(Long productId) {
        String key = DETAIL_KEY.of(productId);
        Product cached = cacheRepository.get(key, PRODUCT_TYPE);

        if (Objects.nonNull(cached)) {
            return cached;
        }

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        if (tryLockWithTimeout(lock)) {
            try {
                Product rechecked = cacheRepository.get(key, PRODUCT_TYPE);
                if (Objects.nonNull(rechecked)) {
                    return rechecked;
                }
                Product product = productService.getActiveProduct(productId);
                cacheRepository.put(key, product, detailTtl());
                return product;
            } finally {
                releaseLock(key, lock);
            }
        }

        return productService.getActiveProduct(productId);
    }

    private String buildListKey(Long brandId, ProductSortType sortType, PageSize pageSize) {
        String brandSegment = Objects.nonNull(brandId) ? String.valueOf(brandId) : ALL_BRAND;
        return LIST_KEY.of(brandSegment, sortType.name(), pageSize.page(), pageSize.size());
    }

    /**
     * ID 리스트 캐시 HIT 시, 상품 상세를 일괄 조회하고 부분 미스를 처리한다.
     */
    private Page<Product> resolveProductsFromIdPage(ProductIdPage idPage) {
        List<Long> ids = idPage.ids();
        List<Product> cached = cacheRepository.multiGet(idPage.detailKeys(), PRODUCT_TYPE);

        List<Long> missedIds = findMissedIds(ids, cached);
        Map<Long, Product> fetched = missedIds.isEmpty()
                ? Collections.emptyMap()
                : productService.getActiveProductsByIds(missedIds);

        cacheProducts(fetched);

        List<Product> products = mergeProducts(ids, cached, fetched);
        return new Page<>(products, idPage.hasNext());
    }

    private List<Long> findMissedIds(List<Long> ids, List<Product> cached) {
        List<Long> missedIds = new ArrayList<>();
        for (int i = 0; i < cached.size(); i++) {
            if (Objects.isNull(cached.get(i))) {
                missedIds.add(ids.get(i));
            }
        }
        return missedIds;
    }

    private List<Product> mergeProducts(List<Long> ids, List<Product> cached, Map<Long, Product> fetched) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Product product = cached.get(i);
            if (Objects.isNull(product)) {
                product = fetched.get(ids.get(i));
            }
            if (Objects.nonNull(product)) {
                products.add(product);
            }
        }
        return products;
    }

    /**
     * 목록 캐시 MISS 시, DB에서 조회하고 상세 캐시(개별 상품)와 목록 캐시(ID 리스트)에 저장한다.
     */
    private Page<Product> fetchAndCacheProducts(Long brandId, ProductSortType sortType, PageSize pageSize) {
        Page<Product> products = productService.getActiveProducts(brandId, sortType, pageSize);

        Map<Long, Product> productMap = new HashMap<>();
        products.content().forEach(product -> productMap.put(product.getId(), product));
        cacheProducts(productMap);

        if (isCacheablePage(pageSize.page())) {
            List<Long> ids = products.content().stream().map(Product::getId).toList();
            ProductIdPage idPage = new ProductIdPage(ids, products.hasNext());
            cacheRepository.put(buildListKey(brandId, sortType, pageSize), idPage, listTtl());
        }

        return products;
    }

    private void cacheProducts(Map<Long, Product> products) {
        if (products.isEmpty()) {
            return;
        }
        Map<String, Product> entries = new HashMap<>();
        products.forEach((id, product) -> entries.put(DETAIL_KEY.of(id), product));
        cacheRepository.multiPut(entries, ProductCacheConstants::detailTtl);
    }

    private boolean isCacheablePage(int page) {
        return page <= MAX_CACHEABLE_PAGE;
    }

    /**
     * 현재 보유 중인 Lock 수를 반환한다. 테스트 전용.
     */
    int lockCount() {
        return locks.size();
    }

    /**
     * Lock을 해제하고, 대기 중인 스레드가 없으면 map에서 제거하여 메모리 누수를 방지한다.
     */
    private void releaseLock(String key, ReentrantLock lock) {
        lock.unlock();
        if (!lock.hasQueuedThreads()) {
            locks.remove(key, lock);
        }
    }

    private boolean tryLockWithTimeout(ReentrantLock lock) {
        try {
            return lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 목록 캐시에 저장되는 ID 리스트와 페이지 메타데이터.
     */
    record ProductIdPage(List<Long> ids, boolean hasNext) {

        List<String> detailKeys() {
            return ids.stream()
                    .map(DETAIL_KEY::of)
                    .toList();
        }
    }
}
