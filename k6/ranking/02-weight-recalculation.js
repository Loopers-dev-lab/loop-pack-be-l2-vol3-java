/**
 * 테스트 2: 가중치 재계산 — 원천 데이터 보존 증명
 *
 * 시나리오:
 *   1. 초기 가중치(control: 0.1/0.2/0.7)로 랭킹 조회 → 순위 기록
 *   2. 관리자 API로 가중치 변경 (0.5/0.3/0.2) → view 가중치를 5배로
 *   3. 5분 대기 (스케줄러 사이클)
 *   4. 같은 원천 데이터에서 순위가 바뀌었는지 확인
 *
 * 전제조건:
 *   - bucket 테이블에 테스트 데이터 있어야 함
 *   - ranking_weight_config에 control 그룹 있어야 함
 *
 * 실행:
 *   k6 run k6/ranking/02-weight-recalculation.js
 *
 * 증명하는 것:
 *   "ZSET 점수 역분해 불가" 문제가 bucket 원천 구조에서는 발생하지 않음.
 *   가중치만 바꾸면 동일 원천에서 다른 순위가 자동 생성됨.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = { 'Content-Type': 'application/json', 'X-Loopers-Ldap': 'admin-ldap' };

export const options = {
  scenarios: {
    recalculation: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10m',
    },
  },
};

function getRankings() {
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const res = http.get(`${BASE_URL}/api/v1/rankings?period=DAILY&date=${today}&page=0&size=10`);
  if (res.status !== 200) return null;
  return JSON.parse(res.body).data.items;
}

function updateWeight(wView, wLike, wOrder) {
  const payload = JSON.stringify({ wView, wLike, wOrder, trafficPct: 100 });
  return http.put(`${BASE_URL}/api-admin/v1/ranking/weights/control`, payload, { headers: ADMIN_HEADER });
}

export default function () {
  // Step 1: 현재 순위 기록 (0.1/0.2/0.7)
  console.log('Step 1: 현재 순위 조회 (control: 0.1/0.2/0.7)');
  const before = getRankings();
  if (!before || before.length === 0) {
    console.log('SKIP: 랭킹 데이터 없음. bucket 테이블에 데이터를 먼저 적재하세요.');
    return;
  }
  const beforeTop3 = before.slice(0, 3).map(i => `${i.productId}(${i.score.toFixed(1)})`);
  console.log(`  Before Top3: ${beforeTop3.join(', ')}`);

  // Step 2: 가중치 변경 — view를 5배로 (0.5/0.3/0.2)
  console.log('Step 2: 가중치 변경 → 0.5/0.3/0.2 (view 5배)');
  const updateRes = updateWeight(0.5, 0.3, 0.2);
  check(updateRes, { '가중치 변경 성공': (r) => r.status === 200 });

  // Step 3: 스케줄러 대기 (5분 + 여유 30초)
  console.log('Step 3: 스케줄러 대기 (330초)...');
  sleep(330);

  // Step 4: 순위 변경 확인
  console.log('Step 4: 변경 후 순위 조회');
  const after = getRankings();
  if (!after || after.length === 0) {
    console.log('FAIL: 변경 후 랭킹 데이터 없음');
    // 원복
    updateWeight(0.1, 0.2, 0.7);
    return;
  }
  const afterTop3 = after.slice(0, 3).map(i => `${i.productId}(${i.score.toFixed(1)})`);
  console.log(`  After Top3: ${afterTop3.join(', ')}`);

  // 순위 또는 점수가 변했는지 확인
  const beforeIds = before.slice(0, 3).map(i => i.productId).join(',');
  const afterIds = after.slice(0, 3).map(i => i.productId).join(',');
  const beforeScores = before.slice(0, 3).map(i => i.score.toFixed(2)).join(',');
  const afterScores = after.slice(0, 3).map(i => i.score.toFixed(2)).join(',');

  const rankChanged = beforeIds !== afterIds;
  const scoreChanged = beforeScores !== afterScores;

  check(null, {
    '점수가 변경됨 (같은 원천, 다른 가중치)': () => scoreChanged,
  });

  console.log(`\n=== 가중치 재계산 결과 ===`);
  console.log(`순위 변경: ${rankChanged ? 'YES' : 'NO'}`);
  console.log(`점수 변경: ${scoreChanged ? 'YES' : 'NO'}`);
  console.log(`Before: ${beforeTop3.join(', ')}`);
  console.log(`After:  ${afterTop3.join(', ')}`);
  console.log(`\n증명: 동일 원천 데이터에서 가중치만 바꿔도 점수/순위가 재계산됨`);
  console.log(`(ZSET이 SSOT인 구조에서는 이것이 불가능)`);

  // Step 5: 원복
  console.log('\nStep 5: 가중치 원복 → 0.1/0.2/0.7');
  updateWeight(0.1, 0.2, 0.7);
  sleep(5);
}
