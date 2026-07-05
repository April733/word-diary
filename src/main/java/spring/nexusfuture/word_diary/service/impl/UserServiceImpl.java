package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.controller.AuthInterceptor;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;
import spring.nexusfuture.word_diary.service.UserService;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final WdUserMapper userMapper;

    public UserServiceImpl(WdUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void register(RegisterReq req) {
        WdUser exist = userMapper.selectByUsername(req.getUsername());
        if (exist != null) {
            throw new BizException("用户名已存在");
        }
        WdUser user = WdUser.builder()
                .username(req.getUsername())
                .password(req.getPassword()) // TODO: 后续使用 BCrypt 加密
                .nickname(req.getNickname() != null ? req.getNickname() : req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .build();
        userMapper.insert(user);
    }

    @Override
    public UserVO login(LoginReq req) {
        WdUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (!user.getPassword().equals(req.getPassword())) { // TODO: BCrypt match
            throw new BizException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        AuthInterceptor.storeToken(token, user.getId());
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }
}
