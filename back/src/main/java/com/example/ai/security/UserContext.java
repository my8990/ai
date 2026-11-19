package com.example.ai.security;

import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser getOrNull() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser loginUser = HOLDER.get();
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，请先登录");
        }
        return loginUser;
    }

    public static void clear() {
        HOLDER.remove();
    }
}