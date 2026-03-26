#!/bin/bash
# 결제 테스트를 위한 데이터 셋업 스크립트
# 실행: bash http/commerce-api/setup-testdata.sh

BASE_URL="http://localhost:8080"

echo "===== ① 브랜드 생성 ====="
BRAND=$(curl -s -X POST "$BASE_URL/api-admin/v1/brands" \
  -H "X-Loopers-Ldap: loopers.admin" \
  -H "Content-Type: application/json" \
  -d '{"name":"나이키","description":"스포츠 브랜드","logoImageUrl":"https://example.com/nike-logo.png"}')
echo "$BRAND"
BRAND_ID=$(echo "$BRAND" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "→ brandId: $BRAND_ID"

echo ""
echo "===== ② 상품 생성 ====="
PRODUCT=$(curl -s -X POST "$BASE_URL/api-admin/v1/products" \
  -H "X-Loopers-Ldap: loopers.admin" \
  -H "Content-Type: application/json" \
  -d "{\"brandId\":$BRAND_ID,\"name\":\"에어맥스 90\",\"price\":150000,\"description\":\"클래식 러닝화\",\"thumbnailImageUrl\":\"https://example.com/airmax90.png\"}")
echo "$PRODUCT"
PRODUCT_ID=$(echo "$PRODUCT" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "→ productId: $PRODUCT_ID"

echo ""
echo "===== ③ 유저 생성 ====="
USER=$(curl -s -X POST "$BASE_URL/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{"loginId":"testuser","password":"Test1234!","name":"테스트유저","email":"test@test.com"}')
echo "$USER"

echo ""
echo "===== ④ 주문 생성 ====="
ORDER=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "X-Loopers-LoginId: testuser" \
  -H "X-Loopers-LoginPw: Test1234!" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}")
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "→ orderId: $ORDER_ID"

echo ""
echo "===== ⑤ 결제 요청 ====="
PAYMENT=$(curl -s -X POST "$BASE_URL/api/v1/payments" \
  -H "X-Loopers-LoginId: testuser" \
  -H "X-Loopers-LoginPw: Test1234!" \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":$ORDER_ID,\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}")
echo "$PAYMENT"

echo ""
echo "===== 완료 ====="
echo "orderId=$ORDER_ID 로 결제 요청 완료. status=PENDING 확인 후 콜백 대기."