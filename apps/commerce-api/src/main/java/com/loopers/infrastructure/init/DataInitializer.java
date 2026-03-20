package com.loopers.infrastructure.init;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 애플리케이션 시작 시 더미 데이터 생성.
 * local 프로파일에서만 실행.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final int USER_COUNT = 1_000;
    private static final int BRAND_COUNT = 5_000;
    private static final int PRODUCT_COUNT = 200_000;
    private static final int COUPON_COUNT = 50_000;

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    private final Random random = new Random();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (isDataExists()) {
            log.info("데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("=== 더미 데이터 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        List<User> users = createUsers();
        List<Brand> brands = createBrands();
        List<Product> products = createProducts(brands);
        createLikes(users, products);
        createCoupons();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("=== 더미 데이터 생성 완료 ({}ms) ===", elapsed);
    }

    private boolean isDataExists() {
        return userRepository.existsByLoginId("user000001");
    }

    private List<User> createUsers() {
        log.info("Users 생성 중... ({}개)", USER_COUNT);
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= USER_COUNT; i++) {
            String loginId = String.format("user%06d", i);
            String password = "Password1!";
            String name = "User" + i;
            String birthDate = generateBirthDate();
            String email = loginId + "@example.com";

            User user = new User(loginId, password, name, birthDate, email);
            users.add(userRepository.save(user));

            if (i % 100 == 0) {
                log.debug("Users 진행: {}/{}", i, USER_COUNT);
            }
        }

        log.info("Users 생성 완료: {}개", users.size());
        return users;
    }

    private List<Brand> createBrands() {
        log.info("Brands 생성 중... ({}개)", BRAND_COUNT);
        List<Brand> brands = new ArrayList<>();

        for (int i = 1; i <= BRAND_COUNT; i++) {
            String name = String.format("Brand-%04d", i);
            String description = "Brand " + i + " - Premium quality products";
            String logoUrl = "https://cdn.example.com/logos/brand-" + i + ".png";

            Brand brand = Brand.create(name, description, logoUrl);
            brands.add(brandRepository.save(brand));

            if (i % 500 == 0) {
                log.debug("Brands 진행: {}/{}", i, BRAND_COUNT);
            }
        }

        log.info("Brands 생성 완료: {}개", brands.size());
        return brands;
    }

    private List<Product> createProducts(List<Brand> brands) {
        log.info("Products 생성 중... ({}개)", PRODUCT_COUNT);
        List<Product> products = new ArrayList<>();

        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            Brand brand = brands.get((i - 1) % brands.size());
            String name = "Product-" + i;
            String description = "High quality product. Item number " + i;
            Money price = new Money(1000 + random.nextLong(500000));
            Stock stock = new Stock(random.nextInt(1000));
            String imageUrl = "https://cdn.example.com/products/" + i + ".jpg";

            Product product = Product.create(brand.getId(), name, description, price, stock, imageUrl);
            products.add(productRepository.save(product));

            if (i % 10000 == 0) {
                log.debug("Products 진행: {}/{}", i, PRODUCT_COUNT);
            }
        }

        log.info("Products 생성 완료: {}개", products.size());
        return products;
    }

    private void createLikes(List<User> users, List<Product> products) {
        log.info("Likes 생성 중...");
        int totalLikes = 0;

        for (Product product : products) {
            int likesCount = generateLikesCount();

            Set<Long> usedUserIds = new HashSet<>();
            for (int j = 0; j < likesCount && usedUserIds.size() < users.size(); j++) {
                User user = users.get(random.nextInt(users.size()));
                if (usedUserIds.contains(user.getId())) {
                    continue;
                }
                usedUserIds.add(user.getId());

                Like like = Like.create(user.getId(), product.getId());
                likeRepository.save(like);
                totalLikes++;
            }

            if (product.getId() % 10000 == 0) {
                log.debug("Likes 진행: productId={}, totalLikes={}", product.getId(), totalLikes);
            }
        }

        log.info("Likes 생성 완료: {}개", totalLikes);
    }

    private void createCoupons() {
        log.info("Coupons 생성 중... ({}개)", COUPON_COUNT);
        ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(365);

        for (int i = 1; i <= COUPON_COUNT; i++) {
            CouponTemplate coupon;

            if (random.nextDouble() < 0.6) {
                // 60% 정액 할인
                long[] values = {1000, 2000, 3000, 5000, 10000};
                long value = values[random.nextInt(values.length)];
                Money discountValue = new Money(value);
                Money minOrderAmount = new Money(10000 + random.nextLong(90000));

                coupon = CouponTemplate.createFixed(
                        "FixedCoupon-" + i,
                        discountValue,
                        minOrderAmount,
                        random.nextDouble() < 0.3 ? null : 100 + random.nextInt(9900),
                        expiredAt
                );
            } else {
                // 40% 정률 할인
                int[] rates = {5, 10, 15, 20, 30};
                int rate = rates[random.nextInt(rates.length)];
                Money minOrderAmount = new Money(20000 + random.nextLong(180000));

                coupon = CouponTemplate.createRate(
                        "RateCoupon-" + i,
                        rate,
                        minOrderAmount,
                        5000 + random.nextInt(45000),
                        random.nextDouble() < 0.3 ? null : 100 + random.nextInt(9900),
                        expiredAt
                );
            }

            couponTemplateRepository.save(coupon);

            if (i % 5000 == 0) {
                log.debug("Coupons 진행: {}/{}", i, COUPON_COUNT);
            }
        }

        log.info("Coupons 생성 완료: {}개", COUPON_COUNT);
    }

    private String generateBirthDate() {
        int year = 1970 + random.nextInt(35);
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);
        return String.format("%04d%02d%02d", year, month, day);
    }

    private int generateLikesCount() {
        double rand = random.nextDouble();
        if (rand < 0.01) {
            return 100 + random.nextInt(400);  // 상위 1%: 100-500
        } else if (rand < 0.10) {
            return 20 + random.nextInt(80);    // 상위 10%: 20-100
        } else if (rand < 0.40) {
            return 5 + random.nextInt(15);     // 상위 40%: 5-20
        } else {
            return random.nextInt(5);          // 나머지: 0-5
        }
    }
}
