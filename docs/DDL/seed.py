#!/usr/bin/env python3
"""
Loopers 감성 이커머스 대용량 데이터 시딩 스크립트

생성 대상:
  - 브랜드: 100개
  - 상품:   브랜드당 100,000개 (총 10,000,000개)
  - 재고:   상품당 1개 (product_stocks)

실행 방법:
  pip install mysql-connector-python
  python docs/DDL/seed.py

사전 조건:
  - MySQL(Docker)이 실행 중이어야 합니다.
    docker compose -f docker/infra-compose.yml up -d
  - loopers 데이터베이스와 테이블이 생성되어 있어야 합니다.
    (애플리케이션 기동 후 Hibernate ddl-auto: create 로 생성 or schema.sql 적용)
"""

import random
import sys
import time
from datetime import datetime

try:
    import mysql.connector
except ImportError:
    print("mysql-connector-python 패키지가 필요합니다.")
    print("  pip install mysql-connector-python")
    sys.exit(1)

# ──────────────────────────────────────────────
# 설정
# ──────────────────────────────────────────────
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'application',
    'password': 'application',
    'database': 'loopers',
    'charset': 'utf8mb4',
    'autocommit': False,
    'connection_timeout': 30,
}

BRAND_COUNT = 100
PRODUCTS_PER_BRAND = 1_000   # 총 10만건 (인덱스 검증용, 전체 테스트는 100_000으로 변경)
BATCH_SIZE = 2_000          # 한 번에 INSERT할 행 수 (메모리 ↔ 속도 트레이드오프)

CATEGORIES = ['의류', '신발', '가방', '액세서리', '뷰티', '스포츠', '홈리빙', '전자기기']
COLORS     = ['블랙', '화이트', '레드', '블루', '그린', '베이지', '그레이', '핑크', '옐로우', '네이비']
SIZES      = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'FREE', '220', '230', '240', '250', '260', '270', '280']


# ──────────────────────────────────────────────
# 유틸
# ──────────────────────────────────────────────
def fmt(n: int) -> str:
    return f"{n:,}"


def elapsed_str(seconds: float) -> str:
    m, s = divmod(int(seconds), 60)
    h, m = divmod(m, 60)
    if h:
        return f"{h}h {m}m {s}s"
    if m:
        return f"{m}m {s}s"
    return f"{s}s"


def progress_bar(current: int, total: int, width: int = 30) -> str:
    ratio = current / total if total else 0
    filled = int(width * ratio)
    bar = '█' * filled + '░' * (width - filled)
    return f"[{bar}] {ratio*100:.1f}%"


# ──────────────────────────────────────────────
# 메인 시딩 로직
# ──────────────────────────────────────────────
def insert_brands(cursor, now: str) -> list[int]:
    """브랜드 100개 삽입 후 brand_id 목록 반환"""
    rows = [
        (
            f'Brand-{i:03d}',
            f'브랜드 {i:03d}의 소개글입니다. 감성 이커머스 Loopers와 함께합니다.',
            f'서울시 강남구 테헤란로 {i * 10}길 {i}호',
            'ACTIVE',
            'N',
            now,
            now,
        )
        for i in range(1, BRAND_COUNT + 1)
    ]
    cursor.executemany(
        "INSERT INTO brands "
        "(brand_name, description, address, display_status, del_yn, created_at, updated_at) "
        "VALUES (%s, %s, %s, %s, %s, %s, %s)",
        rows,
    )
    last_id = cursor.lastrowid
    first_id = last_id - BRAND_COUNT + 1
    return list(range(first_id, last_id + 1))


def insert_products_for_brand(cursor, brand_id: int, brand_idx: int, now: str) -> None:
    """
    단일 브랜드의 상품 PRODUCTS_PER_BRAND개 + 재고를 배치로 삽입.
    MySQL auto_increment는 단일 연결의 단일 executemany에서 연속적으로 할당되므로
    lastrowid 기반 재고 PK 계산이 안전합니다.
    """
    product_insert_sql = (
        "INSERT INTO products "
        "(brand_id, product_name, description, price, category, color, size, "
        "display_status, sale_status, revision_seq, like_count, del_yn, created_at, updated_at) "
        "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
    )
    stock_insert_sql = (
        "INSERT INTO product_stocks "
        "(product_id, on_hand, reserved, created_at, updated_at) "
        "VALUES (%s, %s, %s, %s, %s)"
    )

    product_batch: list[tuple] = []
    inserted = 0

    for j in range(1, PRODUCTS_PER_BRAND + 1):
        price = random.randint(10, 9_900) * 100   # 1,000원 ~ 990,000원 (100원 단위)
        # display_status: 80% ACTIVE / 20% HIDDEN (인덱스 선택성 검증용 분포)
        display_status = 'ACTIVE' if random.random() < 0.8 else 'HIDDEN'
        # sale_status: 70% ON_SALE / 20% TEMP_SOLD_OUT / 10% STOPPED
        sale_status = random.choices(
            ['ON_SALE', 'TEMP_SOLD_OUT', 'STOPPED'], weights=[70, 20, 10]
        )[0]
        like_count = int(random.paretovariate(1.5))  # 롱테일 분포: 소수 상품에 좋아요 집중
        like_count = min(like_count, 100_000)
        product_batch.append((
            brand_id,
            f'상품-B{brand_id:04d}-P{j:06d}',
            f'브랜드 {brand_id}의 {j:,}번째 상품입니다.',
            price,
            random.choice(CATEGORIES),
            random.choice(COLORS),
            random.choice(SIZES),
            display_status,
            sale_status,
            0,
            like_count,
            'N',
            now,
            now,
        ))

        if len(product_batch) >= BATCH_SIZE:
            cursor.executemany(product_insert_sql, product_batch)
            last_id = cursor.lastrowid
            first_id = last_id - len(product_batch) + 1
            stock_batch = [
                (pid, random.randint(10, 1_000), 0, now, now)
                for pid in range(first_id, last_id + 1)
            ]
            cursor.executemany(stock_insert_sql, stock_batch)

            inserted += len(product_batch)
            product_batch = []

            pct = inserted / PRODUCTS_PER_BRAND
            bar = progress_bar(inserted, PRODUCTS_PER_BRAND)
            print(f"\r    {bar} {fmt(inserted)}/{fmt(PRODUCTS_PER_BRAND)}", end='', flush=True)

    # 남은 배치 처리
    if product_batch:
        cursor.executemany(product_insert_sql, product_batch)
        last_id = cursor.lastrowid
        first_id = last_id - len(product_batch) + 1
        stock_batch = [
            (pid, random.randint(10, 1_000), 0, now, now)
            for pid in range(first_id, last_id + 1)
        ]
        cursor.executemany(stock_insert_sql, stock_batch)
        inserted += len(product_batch)

    print(f"\r    {progress_bar(inserted, PRODUCTS_PER_BRAND)} {fmt(inserted)}/{fmt(PRODUCTS_PER_BRAND)}", flush=True)


def main() -> None:
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    print("=" * 60)
    print("  Loopers 대용량 데이터 시딩")
    print(f"  브랜드: {fmt(BRAND_COUNT)}개  |  상품/브랜드: {fmt(PRODUCTS_PER_BRAND)}개")
    print(f"  총 상품: {fmt(BRAND_COUNT * PRODUCTS_PER_BRAND)}개  |  배치 크기: {fmt(BATCH_SIZE)}")
    print("=" * 60)

    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] MySQL 연결 중... ({DB_CONFIG['host']}:{DB_CONFIG['port']})")
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
    except mysql.connector.Error as e:
        print(f"\n[ERROR] DB 연결 실패: {e}")
        print("  → docker compose -f docker/infra-compose.yml up -d 후 재시도하세요.")
        sys.exit(1)

    cursor = conn.cursor()

    # 세션 레벨 최적화 (bulk insert 속도 향상)
    cursor.execute("SET SESSION foreign_key_checks = 0")
    cursor.execute("SET SESSION unique_checks = 0")
    try:
        cursor.execute("SET SESSION innodb_flush_log_at_trx_commit = 2")  # 트랜잭션 커밋 IO 완화
    except mysql.connector.Error:
        pass  # SUPER 권한 없을 경우 무시

    # ── 브랜드 삽입 ──────────────────────────────
    print(f"\n[1/2] 브랜드 {fmt(BRAND_COUNT)}개 삽입 중...")
    t0 = time.time()  # 전체 시작 시간 기준
    brand_ids = insert_brands(cursor, now)
    conn.commit()
    print(f"  → 완료 ({elapsed_str(time.time() - t0)})  brand_id: {brand_ids[0]} ~ {brand_ids[-1]}")

    # ── 상품 + 재고 삽입 ──────────────────────────
    print(f"\n[2/2] 상품 + 재고 삽입 중... (브랜드당 {fmt(PRODUCTS_PER_BRAND)}개)")
    total_start = time.time()
    total_products = 0

    for idx, brand_id in enumerate(brand_ids):
        brand_start = time.time()
        print(f"\n  Brand {idx + 1:3d}/{BRAND_COUNT}  (brand_id={brand_id})")

        insert_products_for_brand(cursor, brand_id, idx, now)
        conn.commit()

        total_products += PRODUCTS_PER_BRAND
        brand_elapsed = time.time() - brand_start
        total_elapsed = time.time() - total_start
        remaining_brands = BRAND_COUNT - (idx + 1)
        avg_per_brand = total_elapsed / (idx + 1)
        eta = avg_per_brand * remaining_brands

        print(f"    소요: {elapsed_str(brand_elapsed)}  |  누적: {fmt(total_products)}개  |  ETA: {elapsed_str(eta)}")

    # 세션 설정 복원
    cursor.execute("SET SESSION foreign_key_checks = 1")
    cursor.execute("SET SESSION unique_checks = 1")
    try:
        cursor.execute("SET SESSION innodb_flush_log_at_trx_commit = 1")
    except mysql.connector.Error:
        pass
    conn.commit()

    cursor.close()
    conn.close()

    grand_total = time.time() - t0
    print("\n" + "=" * 60)
    print("  시딩 완료!")
    print(f"  총 브랜드: {fmt(BRAND_COUNT)}개")
    print(f"  총 상품:   {fmt(total_products)}개")
    print(f"  총 재고:   {fmt(total_products)}개 (product_stocks)")
    print(f"  소요 시간: {elapsed_str(grand_total)}")
    print("=" * 60)


if __name__ == '__main__':
    main()
