package com.loopers.application.user;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.user.RegisterUserCommand;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final ProductLikeService productLikeService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public UserInfo register(RegisterUserCommand command) {
        User user = userService.register(command);
        return UserInfo.from(user);
    }

    public UserInfo getUserInfo(String loginId, String password) {
        User user = userService.getUserInfo(loginId, password);
        return UserInfo.from(user);
    }

    @Transactional
    public void updatePassword(String loginId, String currentPassword, String newPassword) {
        userService.updatePassword(loginId, currentPassword, newPassword);
    }

    public Page<ProductInfo> getLikedProducts(Long userId, Pageable pageable) {
        Page<ProductLike> likes = productLikeService.getLikesByUserId(userId, pageable);

        List<Long> productIds = likes.getContent().stream()
                .map(ProductLike::getProductId)
                .toList();

        if (productIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, likes.getTotalElements());
        }

        List<Product> products = productService.getByIds(productIds);

        Map<Long, Brand> brandMap = getBrandMap(products);

        Map<Long, ProductInfo> productInfoMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> ProductInfo.from(p, brandMap.get(p.getBrandId()))));

        List<ProductInfo> orderedProducts = productIds.stream()
                .map(productInfoMap::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(orderedProducts, pageable, likes.getTotalElements());
    }

    private Map<Long, Brand> getBrandMap(List<Product> products) {
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        return brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));
    }
}
