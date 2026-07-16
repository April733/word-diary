package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;

public interface UserService {
    void register(RegisterReq req);
    UserVO login(LoginReq req);
    void updateLocale(Long userId, String locale);
    boolean isPhoneExists(String phone);
    boolean isEmailExists(String email);
    void resetPassword(String phone, String newPassword);
}
