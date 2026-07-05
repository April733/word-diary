package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class RegisterReq {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
}
