package spring.nexusfuture.word_diary.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import spring.nexusfuture.word_diary.common.JwtUtil;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.common.UserRoles;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final WdUserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwtUtil, WdUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        if (path.contains("/api/user/login") || path.contains("/api/user/register")
                || path.contains("/api/admin/login")
                || path.contains("/api/user/check-phone") || path.contains("/api/user/check-email")
                || path.contains("/api/user/check-invite-code")
                || path.contains("/api/user/send-sms") || path.contains("/api/user/send-mail-code")
                || path.contains("/api/user/send-reset-sms") || path.contains("/api/user/send-reset-mail")
                || path.contains("/api/user/reset-password")
                || path.contains("/api/user/report-issue-public")
                || path.contains("/api/health")) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            writeJson(response, HttpStatus.UNAUTHORIZED, R.fail(401, "未登录或登录已过期"));
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            writeJson(response, HttpStatus.UNAUTHORIZED, R.fail(401, "未登录或登录已过期"));
            return false;
        }

        request.setAttribute("userId", userId);

        WdUser user = userMapper.selectById(userId);
        String role = UserRoles.USER;
        if (user != null) {
            request.setAttribute("isMember", user.getIsMember() != null ? user.getIsMember() : 0);
            request.setAttribute("memberTier", user.getMemberTier() != null ? user.getMemberTier() : 1);
            role = UserRoles.normalize(user.getRole());
        } else {
            request.setAttribute("isMember", 0);
            request.setAttribute("memberTier", 1);
        }
        request.setAttribute("role", role);

        if (requiresAdmin(path) && !UserRoles.isAdmin(role)) {
            writeJson(response, HttpStatus.FORBIDDEN, R.fail(403, "需要管理员权限"));
            return false;
        }
        return true;
    }

    private static boolean requiresAdmin(String path) {
        return path.contains("/api/admin/")
                || path.contains("/api/ai-models")
                || path.contains("/api/config");
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, R<?> body) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
