// Zipf 분포: 상위 20% 상품에 80% 트래픽 집중
export function getProductIdZipf(maxId) {
    const rand = Math.random();
    if (rand < 0.8) {
        return Math.floor(Math.random() * Math.ceil(maxId * 0.2)) + 1;
    }
    return Math.floor(Math.random() * Math.floor(maxId * 0.8)) + Math.ceil(maxId * 0.2) + 1;
}

// 고객 인증 헤더
export function authHeaders(loginId, loginPw) {
    return {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': loginPw,
    };
}

// 관리자 인증 헤더
export function adminHeaders() {
    return {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': 'loopers.admin',
    };
}

// 응답 검증 공통
export function checkResponse(res, name) {
    return {
        [`${name} status 200`]: (r) => r.status === 200,
        [`${name} has data`]: (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.meta && body.meta.result === 'SUCCESS';
            } catch (e) {
                return false;
            }
        },
    };
}
