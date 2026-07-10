package spring.nexusfuture.word_diary.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // Simple in-memory token store (TODO: replace with Redis/JWT in production)
    private static final Map<String, Long> TOKEN_STORE = new ConcurrentHashMap<>();

    private final WdUserMapper userMapper;

    public AuthInterceptor(WdUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public static void storeToken(String token, Long userId) {
        TOKEN_STORE.put(token, userId);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Allow CORS preflight (OPTIONS) requests through without auth
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // TODO: 临时放行，后续恢复鉴权
        String token = request.getHeader("Authorization");
        Long userId = 1L;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (TOKEN_STORE.containsKey(token)) {
                userId = TOKEN_STORE.get(token);
            }
        }
        request.setAttribute("userId", userId);

        // Load user info for membership check
        WdUser user = userMapper.selectById(userId);
        request.setAttribute("isMember", user != null && user.getIsMember() != null ? user.getIsMember() : 0);
        return true;

        // String path = request.getRequestURI();
        // // Skip auth for login/register
        // if (path.contains("/api/user/login") || path.contains("/api/user/register")) {
        //     return true;
        // }
        //
        // String token = request.getHeader("Authorization");
        // if (token != null && token.startsWith("Bearer ")) {
        //     token = token.substring(7);
        // }
        // if (token == null || !TOKEN_STORE.containsKey(token)) {
        //     throw new BizException(401, "未登录或登录已过期");
        // }
        //
        // Long userId = TOKEN_STORE.get(token);
        // request.setAttribute("userId", userId);
        // return true;
    }
}
