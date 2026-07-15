package spring.nexusfuture.word_diary.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 用于生成和解析 JWT Token，服务重启后 Token 仍然有效
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    /**
     * @param secret       JWT 签名密钥（application.yml 中配置）
     * @param expirationMs Token 有效期（毫秒），默认 7 天
     */
    public JwtUtil(@Value("${app.jwt.secret:word-diary-jwt-secret-key-2024-please-change-in-production}") String secret,
                   @Value("${app.jwt.expiration-ms:604800000}") long expirationMs) {
        // 确保密钥长度 >= 256 bits (32 bytes)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * 生成 JWT Token
     * @param userId 用户 ID
     * @return JWT 字符串
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token 获取 userId
     * @param token JWT 字符串
     * @return userId，如果 token 无效或过期则返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        return getUserIdFromToken(token) != null;
    }
}
