package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String token;
    /** 是否会员 1是 0否 */
    private Integer isMember;
    /** 偏好语言，如 zh-CN / en / id / vi / th */
    private String preferredLocale;
}
