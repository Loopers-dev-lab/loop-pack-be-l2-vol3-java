import { UserApi } from '../api.js';
import { Auth } from '../auth.js';

export async function initLogin() {
    if (Auth.isLoggedIn()) { location.hash = 'home'; return; }

    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="auth-container">
            <div class="auth-card">
                <h2>Loopers Shop</h2>
                <div class="auth-tabs">
                    <div class="auth-tab active" data-tab="login">로그인</div>
                    <div class="auth-tab" data-tab="signup">회원가입</div>
                </div>
                <div id="auth-form"></div>
            </div>
        </div>`;

    const tabs = app.querySelectorAll('.auth-tab');
    tabs.forEach(tab => tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        if (tab.dataset.tab === 'login') renderLogin();
        else renderSignup();
    }));

    renderLogin();
}

function renderLogin() {
    const form = document.getElementById('auth-form');
    form.innerHTML = `
        <div class="form-group">
            <label>아이디</label>
            <input id="login-id" placeholder="아이디를 입력하세요" autocomplete="username">
        </div>
        <div class="form-group">
            <label>비밀번호</label>
            <input id="login-pw" type="password" placeholder="비밀번호를 입력하세요" autocomplete="current-password">
        </div>
        <button class="btn btn-primary btn-lg" style="width:100%;margin-top:8px" id="login-btn">로그인</button>`;

    const loginAction = async () => {
        const loginId = document.getElementById('login-id').value.trim();
        const password = document.getElementById('login-pw').value;
        if (!loginId || !password) { Toast.error('아이디와 비밀번호를 입력해주세요'); return; }

        try {
            // 헤더 인증 방식이므로 임시 저장 후 me API 호출로 검증
            Auth.save(loginId, password, loginId);
            const me = await UserApi.me();
            Auth.save(loginId, password, me.name || loginId);
            Toast.success('로그인되었습니다');
            location.hash = 'home';
        } catch (e) {
            Auth.clear();
            Toast.error('로그인에 실패했습니다: ' + e.message);
        }
    };

    document.getElementById('login-btn').addEventListener('click', loginAction);
    document.getElementById('login-pw').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') loginAction();
    });
}

function renderSignup() {
    const form = document.getElementById('auth-form');
    form.innerHTML = `
        <div class="form-group">
            <label>아이디</label>
            <input id="su-id" placeholder="4~12자, 영문/숫자" maxlength="12">
            <div class="form-hint">4~12자, 영문/숫자만 가능</div>
        </div>
        <div class="form-group">
            <label>비밀번호</label>
            <input id="su-pw" type="password" placeholder="8~16자" maxlength="16">
            <div class="form-hint">8~16자, 대/소문자+숫자+특수문자 포함</div>
        </div>
        <div class="form-group">
            <label>이름</label>
            <input id="su-name" placeholder="2~10자" maxlength="10">
        </div>
        <div class="form-group">
            <label>생년월일</label>
            <input id="su-birth" placeholder="19900101" maxlength="8">
            <div class="form-hint">yyyyMMdd 형식 (예: 19900101)</div>
        </div>
        <div class="form-group">
            <label>이메일</label>
            <input id="su-email" type="email" placeholder="example@email.com">
        </div>
        <button class="btn btn-primary btn-lg" style="width:100%;margin-top:8px" id="signup-btn">회원가입</button>`;

    document.getElementById('signup-btn').addEventListener('click', async () => {
        const data = {
            loginId: document.getElementById('su-id').value.trim(),
            password: document.getElementById('su-pw').value,
            name: document.getElementById('su-name').value.trim(),
            birthDate: document.getElementById('su-birth').value.trim(),
            email: document.getElementById('su-email').value.trim(),
        };

        if (!data.loginId || !data.password || !data.name || !data.birthDate || !data.email) {
            Toast.error('모든 항목을 입력해주세요');
            return;
        }

        try {
            await UserApi.signup(data);
            Toast.success('회원가입이 완료되었습니다! 로그인해주세요.');
            // 로그인 탭으로 전환
            document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
            document.querySelector('[data-tab="login"]').classList.add('active');
            renderLogin();
            document.getElementById('login-id').value = data.loginId;
        } catch (e) { Toast.error(e.message); }
    });
}
