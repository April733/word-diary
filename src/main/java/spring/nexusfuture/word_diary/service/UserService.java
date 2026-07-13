package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;

public interface UserService {
    void register(RegisterReq req);
    UserVO login(LoginReq req);
    void updateLocale(Long userId, String locale);
}
