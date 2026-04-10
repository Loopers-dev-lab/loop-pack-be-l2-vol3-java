import { DataGenApi, UserApi } from '../api.js';

export async function initDashboard() {
    const panel = document.getElementById('tab-dashboard');
    panel.innerHTML = `
        <h1 style="font-size:22px;font-weight:700;margin-bottom:20px">대시보드</h1>
        <div class="stats-grid" id="dash-stats">
            <div class="stat-card"><div class="label">로딩 중...</div><div class="value">-</div></div>
        </div>
        <div class="panel">
            <div class="panel-header"><h2>바로가기</h2></div>
            <div style="display:flex;gap:10px;flex-wrap:wrap">
                <button class="btn btn-primary" onclick="document.querySelector('[data-tab=brands]').click()">브랜드 관리</button>
                <button class="btn btn-primary" onclick="document.querySelector('[data-tab=products]').click()">상품 관리</button>
                <button class="btn btn-success" onclick="document.querySelector('[data-tab=\\'data-generator\\']').click()">데이터 생성기</button>
            </div>
        </div>
        <div class="panel" style="margin-top:16px">
            <div class="panel-header"><h2>대기열 시뮬레이션</h2></div>
            <p style="font-size:13px;color:#64748b;margin-bottom:12px">
                1) 유저를 일괄 생성한 뒤, 2) 생성된 유저를 대기열에 입장시킵니다.<br>
                서버에서 배치 처리하므로 빠릅니다. 비밀번호는 모두 <code>Test1234!</code>
            </p>

            <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
                <label style="font-size:13px;font-weight:600">유저 수</label>
                <input type="number" id="dash-queue-count" value="200" min="1" max="5000" style="width:80px;padding:6px 10px;border:1px solid #e2e8f0;border-radius:6px">
                <button class="btn btn-primary" id="dash-queue-create-btn">1. 유저 생성</button>
                <button class="btn btn-success" id="dash-queue-enter-btn" disabled>2. 대기열 입장</button>
                <span id="dash-queue-status" style="font-size:13px;color:#64748b"></span>
            </div>
            <div style="font-size:12px;color:#94a3b8;margin-bottom:4px" id="dash-queue-prefix-info"></div>
        </div>

        <div class="panel" style="margin-top:16px">
            <div class="panel-header"><h2>대용량 데이터 초기화</h2></div>
            <p style="font-size:13px;color:#64748b;margin-bottom:12px">
                100 브랜드, 10만 상품, 1만 유저, ~50만 좋아요, 10만 주문을 일괄 생성합니다.
                이미 데이터가 있으면 해당 Phase는 건너뜁니다.
            </p>
            <div style="display:flex;gap:10px;align-items:center">
                <button class="btn btn-danger" id="dash-bulk-init-btn">Bulk Init 실행</button>
                <span id="dash-bulk-init-status" style="font-size:13px;color:#64748b"></span>
            </div>
        </div>`;

    let queuePrefix = null;

    // 1. 유저 생성 (서버 배치 INSERT)
    document.getElementById('dash-queue-create-btn').addEventListener('click', async () => {
        const count = parseInt(document.getElementById('dash-queue-count').value) || 200;
        const createBtn = document.getElementById('dash-queue-create-btn');
        const enterBtn = document.getElementById('dash-queue-enter-btn');
        const status = document.getElementById('dash-queue-status');
        const prefixInfo = document.getElementById('dash-queue-prefix-info');
        createBtn.disabled = true;
        status.textContent = '유저 생성 중...';

        queuePrefix = 'qu' + String(Date.now()).slice(-4);

        try {
            const result = await DataGenApi.generateUsers(queuePrefix, count, 999999999);
            status.textContent = result.message;
            prefixInfo.textContent = `loginId: ${queuePrefix}0001 ~ ${queuePrefix}${String(count).padStart(4, '0')}`;
            enterBtn.disabled = false;
            Toast.success(result.message);
        } catch (e) {
            status.textContent = '생성 실패: ' + e.message;
        }
        createBtn.disabled = false;
    });

    // 2. 대기열 입장 (서버에서 Redis ZADD 일괄)
    document.getElementById('dash-queue-enter-btn').addEventListener('click', async () => {
        if (!queuePrefix) { Toast.error('유저를 먼저 생성하세요'); return; }
        const count = parseInt(document.getElementById('dash-queue-count').value) || 200;
        const enterBtn = document.getElementById('dash-queue-enter-btn');
        const status = document.getElementById('dash-queue-status');
        enterBtn.disabled = true;
        status.textContent = '대기열 입장 중...';

        try {
            const result = await DataGenApi.bulkQueueEnter(queuePrefix, count);
            status.textContent = result.message;
            Toast.success(result.message);
        } catch (e) {
            status.textContent = '입장 실패: ' + e.message;
        }
        enterBtn.disabled = false;
    });

    document.getElementById('dash-bulk-init-btn').addEventListener('click', async () => {
        const btn = document.getElementById('dash-bulk-init-btn');
        const status = document.getElementById('dash-bulk-init-status');
        btn.disabled = true;
        status.textContent = '시작 중...';
        try {
            const result = await DataGenApi.bulkInit();
            status.textContent = result.message;
            // 3초마다 stats 갱신
            const interval = setInterval(async () => {
                try {
                    await refreshDashStats();
                } catch (e) { /* ignore */ }
            }, 3000);
            // 3분 후 자동 중지
            setTimeout(() => { clearInterval(interval); btn.disabled = false; status.textContent = '완료 (Stats 확인)'; }, 180000);
        } catch (e) {
            status.textContent = '에러: ' + e.message;
            btn.disabled = false;
        }
    });

    await refreshDashStats();
}

async function refreshDashStats() {
    try {
        const stats = await DataGenApi.stats();
        document.getElementById('dash-stats').innerHTML = `
            <div class="stat-card"><div class="label">유저</div><div class="value">${stats.userCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">브랜드</div><div class="value">${stats.brandCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">상품</div><div class="value">${stats.productCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">좋아요</div><div class="value">${stats.likeCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">주문</div><div class="value">${stats.orderCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">쿠폰</div><div class="value">${stats.couponCount.toLocaleString()}</div></div>
            <div class="stat-card"><div class="label">발급 쿠폰</div><div class="value">${stats.ownedCouponCount.toLocaleString()}</div></div>`;
    } catch (e) {
        document.getElementById('dash-stats').innerHTML = `<div class="stat-card"><div class="label">에러</div><div class="value">${e.message}</div></div>`;
    }
}
