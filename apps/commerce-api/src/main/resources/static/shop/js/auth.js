const STORAGE_KEY = 'loopers_auth';

export const Auth = {
    get() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY));
        } catch { return null; }
    },

    save(loginId, password, name) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ loginId, password, name }));
    },

    clear() {
        localStorage.removeItem(STORAGE_KEY);
    },

    isLoggedIn() {
        return !!this.get();
    },

    getHeaders() {
        const auth = this.get();
        if (!auth) return {};
        return {
            'X-Loopers-LoginId': auth.loginId,
            'X-Loopers-LoginPw': auth.password,
        };
    },

    getName() {
        const auth = this.get();
        return auth ? auth.name : null;
    },
};
