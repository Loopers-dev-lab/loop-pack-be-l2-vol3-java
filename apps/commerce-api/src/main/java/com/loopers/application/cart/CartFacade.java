package com.loopers.application.cart;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.UnavailableReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 장바구니 Facade (퍼사드)
 *
 * <p>CartService, ProductService, StockService, BrandService를
 * 조합(orchestration)하여 장바구니 비즈니스 플로우를 완성한다.</p>
 *
 * <ul>
 *   <li>트랜잭션 경계 설정</li>
 *   <li>상품 주문 가능 여부 및 재고 검증 후 장바구니 추가</li>
 *   <li>재고 검증 후 수량 변경</li>
 *   <li>장바구니 항목 + 상품/브랜드/재고 조합 조회</li>
 * </ul>
 *
 * <p>인증은 {@link com.loopers.interfaces.api.CustomerAuthInterceptor}에서 처리된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartFacade {

    private final CartService cartService;
    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;

    /**
     * 장바구니 목록을 조회한다.
     *
     * <p>장바구니 항목에 상품, 브랜드, 재고 정보를 조합하여
     * 주문 가능 여부와 불가 사유를 포함한 CartInfo 목록을 반환한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록 (상품/브랜드/재고 정보 포함)
     */
    public List<CartInfo> getCart(Long userId) {
        return buildCartInfoList(cartService.getCartItems(userId));
    }

    /**
     * 장바구니에 상품을 추가한다.
     *
     * <p>상품 주문 가능 여부와 재고 초과 여부를 검증한 뒤 장바구니에 추가한다.</p>
     *
     * @param userId    사용자 ID
     * @param productId 추가할 상품 ID
     * @param qty       수량
     */
    @Transactional
    public void addItem(Long userId, Long productId, int qty) {
        productService.findOrderableById(productId);

        ProductStockModel stock = stockService.findByProductId(productId);
        stock.validateCanHold(qty);

        cartService.addItem(userId, productId, qty);
    }

    /**
     * 장바구니 항목의 수량을 변경한다.
     *
     * <p>재고 초과 여부를 검증한 뒤 수량을 변경한다.</p>
     *
     * @param userId    사용자 ID
     * @param productId 수량을 변경할 상품 ID
     * @param newQty    변경할 새 수량
     */
    @Transactional
    public void changeQuantity(Long userId, Long productId, int newQty) {
        ProductStockModel stock = stockService.findByProductId(productId);
        stock.validateCanHold(newQty);

        cartService.changeQuantity(userId, productId, newQty);
    }

    /**
     * 장바구니에서 상품을 삭제한다.
     *
     * @param userId    사용자 ID
     * @param productId 삭제할 상품 ID
     */
    @Transactional
    public void removeItem(Long userId, Long productId) {
        cartService.removeItem(userId, productId);
    }

    /**
     * 관리자용 장바구니 목록을 조회한다.
     *
     * <p>인증 없이 사용자 ID로 직접 조회한다.
     * 장바구니 항목에 상품/브랜드/재고 정보를 조합하여 반환한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록 (상품/브랜드/재고 정보 포함)
     */
    public List<CartInfo> getCartForAdmin(Long userId) {
        return buildCartInfoList(cartService.getCartItems(userId));
    }

    /**
     * 장바구니 항목 목록에 상품/브랜드/재고 정보를 조합하여 CartInfo 목록을 생성한다.
     * <p>배치 조회로 N+1 쿼리를 방지한다 (3N+1 → 4 쿼리).</p>
     */
    private List<CartInfo> buildCartInfoList(List<CartItemModel> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = items.stream()
                .map(CartItemModel::getProductId).distinct().toList();

        Map<Long, ProductModel> productMap = productService.findAllByIds(productIds)
                .stream().collect(Collectors.toMap(ProductModel::getProductId, Function.identity()));

        Set<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId).collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.findAllByIds(brandIds)
                .stream().collect(Collectors.toMap(BrandModel::getBrandId, Function.identity()));

        Map<Long, ProductStockModel> stockMap = stockService.findAllByProductIds(productIds)
                .stream().collect(Collectors.toMap(ProductStockModel::getProductId, Function.identity()));

        List<CartInfo> result = new ArrayList<>();
        for (CartItemModel item : items) {
            ProductModel product = productMap.get(item.getProductId());
            BrandModel brand = brandMap.get(product.getBrandId());
            ProductStockModel stock = stockMap.get(item.getProductId());
            result.add(buildCartInfo(item, product, brand, stock));
        }
        return result;
    }

    /**
     * 장바구니 항목, 상품, 브랜드, 재고 모델에서 CartInfo를 조립한다.
     * 주문 가능 여부(available)와 불가 사유(unavailableReason)를 실시간으로 계산한다.
     */
    private CartInfo buildCartInfo(CartItemModel cartItem, ProductModel product,
                                   BrandModel brand, ProductStockModel stock) {
        UnavailableReason reason = calculateUnavailableReason(product, brand, stock, cartItem.getQuantity());
        return CartInfo.builder()
                .userId(cartItem.getUserId())
                .productId(cartItem.getProductId())
                .quantity(cartItem.getQuantity())
                .available(reason == null)
                .unavailableReason(reason)
                .productName(product.getProductName())
                .price(product.getPrice())
                .brandId(brand.getBrandId())
                .brandName(brand.getBrandName())
                .imageUrl(product.getImageUrl())
                .availableStock(stock != null ? stock.getAvailableQty() : 0)
                .build();
    }

    /**
     * 장바구니 항목의 주문 불가 사유를 계산한다. 주문 가능하면 null을 반환한다.
     * 비즈니스 규칙은 {@link UnavailableReason#evaluate}에 위임한다.
     */
    private UnavailableReason calculateUnavailableReason(
            ProductModel product, BrandModel brand,
            ProductStockModel stock, int requestedQty) {
        return UnavailableReason.evaluate(
                product.isDeleted(), product.getDisplayStatus(), product.getSaleStatus(),
                brand.isDeleted(), brand.getDisplayStatus(),
                stock != null ? stock.getAvailableQty() : 0, requestedQty);
    }
}
