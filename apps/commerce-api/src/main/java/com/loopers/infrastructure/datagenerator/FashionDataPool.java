package com.loopers.infrastructure.datagenerator;

import java.util.List;
import java.util.Random;

public final class FashionDataPool {

    private FashionDataPool() {
    }

    // === 브랜드 100개 ===
    static final List<String> BRAND_NAMES = List.of(
            // 글로벌 스포츠 (15)
            "나이키", "아디다스", "뉴발란스", "푸마", "리복",
            "컨버스", "아식스", "미즈노", "언더아머", "데상트",
            "휠라", "엘레세", "르꼬끄", "챔피온", "카파",
            // 글로벌 SPA (10)
            "자라", "유니클로", "H&M", "코스", "망고",
            "갭", "올드네이비", "포에버21", "프라이마크", "몬키",
            // 국내 스트릿 (15)
            "커버낫", "디스이즈네버댓", "마뗑킴", "아더에러", "엠엘비",
            "널디", "키르시", "그루브라임", "LMC", "인사일런스",
            "플랙", "비바스튜디오", "앤더슨벨", "이미스", "마르디메크르디",
            // 국내 베이직 (10)
            "무신사스탠다드", "탑텐", "스파오", "에잇세컨즈", "폴햄",
            "지오다노", "닉스", "프로젝트엠", "앤드지", "올젠",
            // 럭셔리 스포츠 (5)
            "톰브라운", "메종키츠네", "아미", "우영미", "르메르",
            // 아웃도어 (10)
            "노스페이스", "파타고니아", "아크테릭스", "컬럼비아", "살로몬",
            "호카", "메렐", "티엠비", "블랙야크", "코오롱스포츠",
            // 캐주얼/컨템포러리 (10)
            "폴로랄프로렌", "타미힐피거", "캘빈클라인", "라코스테", "게스",
            "빈폴", "헤지스", "닥스", "브룩스브라더스", "제이크루",
            // 스니커즈/슈즈 (5)
            "반스", "닥터마틴", "크록스", "버켄스탁", "타임버랜드",
            // 디자이너 (10)
            "이자벨마랑", "아크네스튜디오", "겐조", "오프화이트", "스투시",
            "팔라스", "슈프림", "베이프", "휴먼메이드", "갤러리디파트먼트",
            // 국내 여성 (10)
            "미샤", "잇미샤", "시스템", "지컷", "랩",
            "올리브데올리브", "듀엘", "로엠", "쏘울", "나인");

    // === 카테고리 ===
    enum Category {
        TOP("상의", 25, 19_900, 89_900, 100, 500, 0.05),
        BOTTOM("하의", 18, 29_900, 129_900, 100, 500, 0.05),
        OUTER("아우터", 12, 69_900, 399_900, 30, 200, 0.08),
        DRESS("원피스/세트", 8, 39_900, 199_900, 30, 200, 0.06),
        SHOES("슈즈", 15, 59_900, 299_900, 20, 150, 0.10),
        BAG("가방", 12, 39_900, 249_900, 30, 200, 0.06),
        ACCESSORY("악세서리", 10, 9_900, 149_900, 200, 1000, 0.03);

        final String label;
        final int weightPercent;
        final int minPrice;
        final int maxPrice;
        final int minStock;
        final int maxStock;
        final double soldOutRate;

        Category(String label, int weightPercent, int minPrice, int maxPrice,
                 int minStock, int maxStock, double soldOutRate) {
            this.label = label;
            this.weightPercent = weightPercent;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.minStock = minStock;
            this.maxStock = maxStock;
            this.soldOutRate = soldOutRate;
        }
    }

    // 카테고리별 아이템 타입
    private static final List<String> TOP_ITEMS = List.of(
            "반팔 티셔츠", "긴팔 티셔츠", "크루넥 맨투맨", "후드 스웨트셔츠", "옥스포드 셔츠",
            "오버사이즈 셔츠", "헨리넥 티", "피케 폴로", "크롭 티셔츠", "니트 스웨터",
            "카라 니트", "터틀넥 니트", "린넨 셔츠", "스트라이프 티셔츠", "그래픽 티셔츠");
    private static final List<String> BOTTOM_ITEMS = List.of(
            "스트레이트 데님", "슬림핏 데님", "와이드 데님", "치노 팬츠", "슬랙스",
            "조거 팬츠", "카고 팬츠", "숏 팬츠", "플리츠 스커트", "밴딩 팬츠",
            "부츠컷 데님", "코듀로이 팬츠");
    private static final List<String> OUTER_ITEMS = List.of(
            "라이더 자켓", "코치 자켓", "블레이저", "트렌치 코트", "숏패딩",
            "롱패딩", "바람막이", "플리스 자켓", "카디건", "데님 자켓",
            "무스탕", "볼버 자켓");
    private static final List<String> DRESS_ITEMS = List.of(
            "미니 원피스", "롱 원피스", "셔츠 원피스", "니트 원피스", "점프수트",
            "투피스 셋업", "블라우스 셋업");
    private static final List<String> SHOES_ITEMS = List.of(
            "캔버스 스니커즈", "러닝화", "로퍼", "첼시 부츠", "워커",
            "슬리퍼", "뮬", "플랫슈즈", "하이탑 스니커즈", "트레일 러닝화",
            "더비슈즈", "레이스업 부츠");
    private static final List<String> BAG_ITEMS = List.of(
            "미니 크로스백", "토트백", "백팩", "숄더백", "에코백",
            "메신저백", "클러치백", "버킷백", "웨이스트백", "노트북 백팩");
    private static final List<String> ACCESSORY_ITEMS = List.of(
            "볼캡", "비니", "버킷햇", "양말 세트", "벨트",
            "선글라스", "목걸이", "팔찌", "반지", "머플러",
            "장갑", "키링");

    private static final List<String> MODIFIERS = List.of(
            "오버핏", "슬림핏", "에센셜", "클래식", "프리미엄",
            "빈티지", "모던", "레트로", "시그니처", "베이직",
            "라이트", "헤비웨이트", "소프트", "워시드", "리버시블");

    private static final List<String> COLORS = List.of(
            "블랙", "화이트", "네이비", "차콜", "베이지",
            "카키", "인디고", "버건디", "올리브", "크림",
            "그레이", "스카이블루", "머스타드", "딥그린", "코랄");

    // === 브랜드 티어 (인기도) ===
    // S티어: index 0~9 (10개), A티어: 10~29 (20개), B티어: 30~99 (70개)
    static final int S_TIER_END = 10;
    static final int A_TIER_END = 30;

    // === 브랜드별 상품 수 분포 ===
    // 인기 브랜드(0~19): 2000~3000, 중간(20~49): 500~1000, 소규모(50~99): 100~300

    static int productsPerBrand(int brandIndex, int totalProducts, Random random) {
        if (brandIndex < 20) {
            return 2000 + random.nextInt(1001); // 2000~3000
        } else if (brandIndex < 50) {
            return 500 + random.nextInt(501); // 500~1000
        } else {
            return 100 + random.nextInt(201); // 100~300
        }
    }

    static Category pickCategory(Random random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (Category cat : Category.values()) {
            cumulative += cat.weightPercent;
            if (roll < cumulative) {
                return cat;
            }
        }
        return Category.ACCESSORY;
    }

    static String generateProductName(Category category, Random random) {
        List<String> items = switch (category) {
            case TOP -> TOP_ITEMS;
            case BOTTOM -> BOTTOM_ITEMS;
            case OUTER -> OUTER_ITEMS;
            case DRESS -> DRESS_ITEMS;
            case SHOES -> SHOES_ITEMS;
            case BAG -> BAG_ITEMS;
            case ACCESSORY -> ACCESSORY_ITEMS;
        };

        String item = items.get(random.nextInt(items.size()));
        String modifier = MODIFIERS.get(random.nextInt(MODIFIERS.size()));
        String color = COLORS.get(random.nextInt(COLORS.size()));

        String fullName = modifier + " " + item + " " + color;
        if (fullName.length() <= 99) {
            return fullName;
        }

        String withoutColor = modifier + " " + item;
        if (withoutColor.length() <= 99) {
            return withoutColor;
        }
        return item;
    }

    static int generatePrice(Category category, Random random) {
        // 로그정규분포: 저가 쪽에 더 많이 분포
        double logMin = Math.log(category.minPrice);
        double logMax = Math.log(category.maxPrice);
        double logPrice = logMin + random.nextGaussian() * (logMax - logMin) * 0.3 + (logMax - logMin) * 0.4;
        logPrice = Math.max(logMin, Math.min(logMax, logPrice));
        int price = (int) Math.exp(logPrice);
        return (price / 100) * 100; // 100원 단위 반올림
    }

    static int generateStock(Category category, Random random) {
        if (random.nextDouble() < category.soldOutRate) {
            return 0;
        }
        return category.minStock + random.nextInt(category.maxStock - category.minStock + 1);
    }
}
