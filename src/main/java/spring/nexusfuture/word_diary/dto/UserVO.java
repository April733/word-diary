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
}
