package com.loopers.interfaces.datagenerator;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.datagenerator.BulkDataGeneratorService;
import com.loopers.infrastructure.datagenerator.DataGeneratorRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.datagenerator.dto.AdminDataGeneratorV1Dto;
import jakarta.validation.Valid;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/data-generator")
public class AdminDataGeneratorV1Controller {

    private final DataGeneratorRepository dataGeneratorRepository;
    private final BulkDataGeneratorService bulkDataGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final ProductService productService;
    private final OrderFacade orderFacade;
    private final CouponFacade couponFacade;

    @PostMapping("/bulk-init")
    public ApiResponse<Map<String, String>> bulkInit() {
        if (bulkDataGeneratorService.isRunning()) {
            return ApiResponse.success(Map.of("message", "이미 실행 중입니다. Stats API로 진행 상황을 확인하세요."));
        }
        CompletableFuture.runAsync(bulkDataGeneratorService::generateAll);
        return ApiResponse.success(Map.of("message", "데이터 생성이 시작되었습니다. Stats API로 진행 상황을 확인하세요."));
    }

    @GetMapping("/stats")
    public ApiResponse<AdminDataGeneratorV1Dto.StatsResponse> getStats() {
        Map<String, Long> stats = dataGeneratorRepository.getStats();
        return ApiResponse.success(new AdminDataGeneratorV1Dto.StatsResponse(
                stats.get("brandCount"),
                stats.get("productCount"),
                stats.get("likeCount"),
                stats.get("userCount"),
                stats.get("orderCount"),
                stats.get("couponCount"),
                stats.get("ownedCouponCount"),
                Collections.emptyMap()));
    }

    @PostMapping("/likes")
    public ApiResponse<AdminDataGeneratorV1Dto.GenerateLikesResponse> generateLikes(
        @Valid @RequestBody AdminDataGeneratorV1Dto.GenerateLikesRequest request
    ) {
        long startUserId = dataGeneratorRepository.getMaxUserId() + 1;
        List<long[]> pairs = new ArrayList<>();

        for (Long productId : request.productIds()) {
            for (int i = 0; i < request.likesPerProduct(); i++) {
                pairs.add(new long[]{startUserId + i, productId});
            }
        }

        int created = dataGeneratorRepository.batchInsertLikes(pairs);
        return ApiResponse.success(new AdminDataGeneratorV1Dto.GenerateLikesResponse(
                created, 0,
                request.productIds().size() + "개 상품에 각 "
                        + request.likesPerProduct() + "개 좋아요 생성 완료"));
    }

    @PostMapping("/users")
    public ApiResponse<AdminDataGeneratorV1Dto.GenerateUsersResponse> generateUsers(
        @Valid @RequestBody AdminDataGeneratorV1Dto.GenerateUsersRequest request
    ) {
        long defaultPoint = request.defaultPoint() != null ? request.defaultPoint() : 0L;
        String encodedPassword = passwordEncoder.encode("Test1234!");

        int created = dataGeneratorRepository.batchInsertUsers(
                request.prefix(), request.count(), encodedPassword, defaultPoint);

        return ApiResponse.success(new AdminDataGeneratorV1Dto.GenerateUsersResponse(
                created,
                created + "명 유저 생성 완료 (비밀번호: Test1234!)"));
    }

    @PostMapping("/orders")
    public ApiResponse<AdminDataGeneratorV1Dto.GenerateOrdersResponse> generateOrders(
        @Valid @RequestBody AdminDataGeneratorV1Dto.GenerateOrdersRequest request
    ) {
        boolean isSpecificMode = "specific".equals(request.mode())
                && request.items() != null && !request.items().isEmpty();

        List<OrderCriteria.Create.CreateItem> specifiedItems = null;

        if (isSpecificMode) {
            specifiedItems = new ArrayList<>();
            for (AdminDataGeneratorV1Dto.GenerateOrdersRequest.OrderItemSpec spec : request.items()) {
                ProductModel product = productService.getById(spec.productId());
                specifiedItems.add(new OrderCriteria.Create.CreateItem(
                        product.getId(), spec.quantity(), product.getPrice()));
            }
        } else {
            List<Map<String, Object>> products = dataGeneratorRepository.findRandomProducts(100);
            if (products.isEmpty()) {
                return ApiResponse.success(new AdminDataGeneratorV1Dto.GenerateOrdersResponse(
                        0, 0, "주문 가능한 상품이 없습니다."));
            }
            specifiedItems = pickRandomItems(products,
                    request.itemsPerOrder() != null ? request.itemsPerOrder() : 3);
        }

        int created = 0;
        int failed = 0;

        for (Long userId : request.userIds()) {
            try {
                List<OrderCriteria.Create.CreateItem> orderItems = isSpecificMode
                        ? specifiedItems
                        : pickRandomItems(dataGeneratorRepository.findRandomProducts(100),
                                request.itemsPerOrder() != null ? request.itemsPerOrder() : 3);

                int totalCost = orderItems.stream()
                        .mapToInt(item -> item.expectedPrice() * item.quantity())
                        .sum();

                userService.addPoint(userId, totalCost + 1000L);

                orderFacade.createOrder(userId, new OrderCriteria.Create(orderItems));
                created++;
            } catch (Exception e) {
                log.warn("주문 생성 실패 (userId={}): {}", userId, e.getMessage());
                failed++;
            }
        }

        return ApiResponse.success(new AdminDataGeneratorV1Dto.GenerateOrdersResponse(
                created, failed,
                created + "건 주문 생성 완료, " + failed + "건 실패"));
    }

    @PostMapping("/coupons")
    public ApiResponse<AdminDataGeneratorV1Dto.GenerateCouponsResponse> generateCoupons(
        @Valid @RequestBody AdminDataGeneratorV1Dto.GenerateCouponsRequest request
    ) {
        CouponDiscountType discountType = CouponDiscountType.valueOf(request.discountType());
        ZonedDateTime expiredAt = ZonedDateTime.now().plusMonths(3);
        int couponsCreated = 0;
        int totalIssued = 0;

        List<CouponResult.Detail> createdCoupons = new ArrayList<>();

        for (int i = 0; i < request.count(); i++) {
            String couponName = discountType == CouponDiscountType.RATE
                    ? request.discountValue() + "% 할인 쿠폰 #" + (i + 1)
                    : request.discountValue() + "원 할인 쿠폰 #" + (i + 1);

            CouponResult.Detail detail = couponFacade.registerCoupon(new CouponCriteria.Create(
                    couponName,
                    discountType,
                    request.discountValue(),
                    request.minOrderAmount(),
                    request.totalQuantityPerCoupon(),
                    expiredAt));
            createdCoupons.add(detail);
            couponsCreated++;
        }

        if (request.issueToAllUsers()) {
            List<Long> userIds = dataGeneratorRepository.findAllUserIds();
            for (CouponResult.Detail coupon : createdCoupons) {
                int issued = dataGeneratorRepository.batchInsertOwnedCoupons(
                        coupon.id(), coupon.name(), discountType.name(),
                        request.discountValue(), request.minOrderAmount(),
                        expiredAt, userIds);
                dataGeneratorRepository.updateCouponIssuedQuantity(coupon.id(), issued);
                totalIssued += issued;
            }
        }

        return ApiResponse.success(new AdminDataGeneratorV1Dto.GenerateCouponsResponse(
                couponsCreated, totalIssued,
                couponsCreated + "개 쿠폰 생성, " + totalIssued + "개 발급 완료"));
    }

    private List<OrderCriteria.Create.CreateItem> pickRandomItems(
            List<Map<String, Object>> products, int count) {
        List<Map<String, Object>> shuffled = new ArrayList<>(products);
        Collections.shuffle(shuffled);

        List<OrderCriteria.Create.CreateItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            Map<String, Object> p = shuffled.get(i);
            int price = ((Number) p.get("price")).intValue();
            int stock = ((Number) p.get("stock")).intValue();
            int qty = Math.min(ThreadLocalRandom.current().nextInt(1, 4), stock);
            if (qty <= 0) continue;

            items.add(new OrderCriteria.Create.CreateItem(
                    ((Number) p.get("id")).longValue(), qty, price));
        }

        if (items.isEmpty() && !products.isEmpty()) {
            Map<String, Object> p = products.get(0);
            items.add(new OrderCriteria.Create.CreateItem(
                    ((Number) p.get("id")).longValue(), 1,
                    ((Number) p.get("price")).intValue()));
        }
        return items;
    }
}
