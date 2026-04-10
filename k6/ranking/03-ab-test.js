/**
 * 테스트 3: A/B 테스트 그룹별 ZSET 분리 검증
 *
 * 시나리오:
 *   1. experiment 그룹 생성 (wView=0.5, wLike=0.3, wOrder=0.2, traffic=50%)
 *   2. 스케줄러 대기 → 두 ZSET 생성 확인
 *   3. 다수의 userId로 랭킹 조회 → 그룹 분배 확인
 *   4. 같은 userId 반복 호출 → 항상 같은 그룹 (결정적)
 *   5. 두 그룹의 순위가 다른지 확인
 *   6. experiment 비활성화 → 정리
 *
 * 증명하는 것:
 *   - 재배포 없이 가중치 변경 + 점진적 전환
 *   - 같은 원천 데이터에서 다른 가중치 → 다른 순위
 *   - userId 해시 기반 결정적 분배
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = { 'Content-Type': 'application/json', 'X-Loopers-Ldap': 'admin-ldap' };

const controlCount = new Counter('group_control');
const experimentCount = new Counter('group_experiment');

export const options = {
  scenarios: {
    ab_test: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10m',
    },
  },
};

function getRankingsWithUserId(userId) {
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const headers = userId ? { 'X-Loopers-UserId': String(userId) } : {};
  const res = http.get(
    `${BASE_URL}/api/v1/rankings?period=DAILY&date=${today}&page=0&size=10`,
    { headers }
  );
  if (res.status !== 200) return null;
  return JSON.parse(res.body).data;
}

export default function () {
  // Step 1: experiment 그룹 생성
  console.log('Step 1: experiment 그룹 생성 (0.5/0.3/0.2, traffic=50%)');
  const createRes = http.post(
    `${BASE_URL}/api-admin/v1/ranking/weights`,
    JSON.stringify({
      groupName: 'experiment',
      wView: 0.5,
      wLike: 0.3,
      wOrder: 0.2,
      trafficPct: 50,
    }),
    { headers: ADMIN_HEADER }
  );
  check(createRes, { 'experiment 생성 성공': (r) => r.status === 200 });

  // Step 2: 스케줄러 대기
  console.log('Step 2: 스케줄러 대기 (330초)...');
  sleep(330);

  // Step 3: 다수 userId로 그룹 분배 확인
  console.log('Step 3: userId 100명 그룹 분배 확인');
  const groupResults = {};

  for (let userId = 1; userId <= 100; userId++) {
    const data = getRankingsWithUserId(userId);
    if (data) {
      const group = data.experimentGroup;
      groupResults[group] = (groupResults[group] || 0) + 1;
      if (group === 'control') controlCount.add(1);
      else experimentCount.add(1);
    }
  }

  console.log(`  그룹 분배: ${JSON.stringify(groupResults)}`);

  check(null, {
    'control 그룹 존재': () => (groupResults['control'] || 0) > 0,
    'experiment 그룹 존재': () => (groupResults['experiment'] || 0) > 0,
    '분배 비율 대략 50:50': () => {
      const c = groupResults['control'] || 0;
      const e = groupResults['experiment'] || 0;
      return c > 20 && e > 20; // 100명 중 각각 20명 이상
    },
  });

  // Step 4: 같은 userId 결정적 분배 확인
  console.log('Step 4: userId=42 결정적 분배 확인 (10회 반복)');
  const groups = new Set();
  for (let i = 0; i < 10; i++) {
    const data = getRankingsWithUserId(42);
    if (data) groups.add(data.experimentGroup);
  }

  check(null, {
    '같은 userId는 항상 같은 그룹': () => groups.size === 1,
  });
  console.log(`  userId=42 → 항상 "${[...groups][0]}" (${groups.size === 1 ? 'PASS' : 'FAIL'})`);

  // Step 5: 두 그룹 순위 비교
  console.log('Step 5: 두 그룹 순위 비교');

  // control 그룹에 배정되는 userId 찾기
  let controlUserId = null;
  let experimentUserId = null;
  for (let userId = 1; userId <= 100; userId++) {
    const data = getRankingsWithUserId(userId);
    if (data && data.experimentGroup === 'control' && !controlUserId) controlUserId = userId;
    if (data && data.experimentGroup === 'experiment' && !experimentUserId) experimentUserId = userId;
    if (controlUserId && experimentUserId) break;
  }

  if (controlUserId && experimentUserId) {
    const controlData = getRankingsWithUserId(controlUserId);
    const experimentData = getRankingsWithUserId(experimentUserId);

    if (controlData && experimentData && controlData.items.length > 0 && experimentData.items.length > 0) {
      const controlTop = controlData.items[0];
      const expTop = experimentData.items[0];

      console.log(`  Control 1위: pid=${controlTop.productId}, score=${controlTop.score.toFixed(2)}`);
      console.log(`  Experiment 1위: pid=${expTop.productId}, score=${expTop.score.toFixed(2)}`);

      const scoresDiffer = controlTop.score.toFixed(2) !== expTop.score.toFixed(2);
      check(null, {
        '두 그룹의 점수가 다름 (다른 가중치 적용)': () => scoresDiffer,
      });
    }
  }

  // Step 6: 정리 — experiment 비활성화
  console.log('\nStep 6: experiment 비활성화');
  http.del(`${BASE_URL}/api-admin/v1/ranking/weights/experiment`, null, { headers: ADMIN_HEADER });

  console.log('\n=== A/B 테스트 결과 ===');
  console.log(`그룹 분배: ${JSON.stringify(groupResults)}`);
  console.log(`결정적 분배: ${groups.size === 1 ? 'PASS' : 'FAIL'}`);
  console.log('증명: 재배포 없이 가중치 변경 + userId 해시 기반 결정적 분배');
}
