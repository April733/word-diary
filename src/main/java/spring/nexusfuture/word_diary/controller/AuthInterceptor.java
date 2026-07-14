package spring.nexusfuture.word_diary.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;
import spring.nexusfuture.word_diary.service.MemberTierService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // Simple in-memory token store (TODO: replace with Redis/JWT in production)
    private static final Map<String, Long> TOKEN_STORE = new ConcurrentHashMap<>();

    private final WdUserMapper userMapper;
    private final MemberTierService memberTierService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(WdUserMapper userMapper, MemberTierService memberTierService) {
        this.userMapper = userMapper;
        this.memberTierService = memberTierService;
    }

    public static void storeToken(String token, Long userId) {
        TOKEN_STORE.put(token, userId);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS preflight (OPTIONS) requests through without auth
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // Skip auth for login/register and public user APIs
        if (path.contains("/api/user/login") || path.contains("/api/user/register") || path.contains("/api/user/check-phone") || path.contains("/api/user/check-invite-code") || path.contains("/api/user/send-sms") || path.contains("/api/user/send-reset-sms") || path.contains("/api/user/reset-password")) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || !TOKEN_STORE.containsKey(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(401, "未登录或登录已过期")));
            return false;
        }

        Long userId = TOKEN_STORE.get(token);
        request.setAttribute("userId", userId);

        // Load user info for membership check
        WdUser user = userMapper.selectById(userId);
        if (user != null) {
            request.setAttribute("isMember", user.getIsMember() != null ? user.getIsMember() : 0);
            request.setAttribute("memberTier", user.getMemberTier() != null ? user.getMemberTier() : 1);
        } else {
            request.setAttribute("isMember", 0);
            request.setAttribute("memberTier", 1);
        }
        return true;
    }
}
