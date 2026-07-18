package spring.nexusfuture.word_diary.service.impl;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.AiRegions;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.JwtUtil;
import spring.nexusfuture.word_diary.common.UserRoles;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;
import spring.nexusfuture.word_diary.service.MemberTierService;
import spring.nexusfuture.word_diary.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final WdUserMapper userMapper;
    private final MemberTierService memberTierService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(WdUserMapper userMapper,
                           MemberTierService memberTierService,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.memberTierService = memberTierService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public void register(RegisterReq req) {
        WdUser exist = userMapper.selectByUsername(req.getUsername());
        if (exist != null) {
            throw new BizException("用户名已存在");
        }

        // 手机号/邮箱二选一校验
        if ("phone".equals(req.getRegisterType())) {
            if (req.getPhone() == null || req.getPhone().isBlank()) {
                throw new BizException("手机号不能为空");
            }
            if (userMapper.selectByPhone(req.getPhone()) != null) {
                throw new BizException("手机号已被注册");
            }
        } else if ("email".equals(req.getRegisterType())) {
            if (req.getEmail() == null || req.getEmail().isBlank()) {
                throw new BizException("邮箱不能为空");
            }
            if (userMapper.selectByEmail(req.getEmail()) != null) {
                throw new BizException("邮箱已被注册");
            }
        } else {
            throw new BizException("注册方式不正确");
        }

        // 暂不启用会员体系：注册不需要邀请码，默认免费档
        Integer tier = memberTierService.getDefaultTier().getTier();

        WdUser user = WdUser.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getUsername())
                .phone("phone".equals(req.getRegisterType()) ? req.getPhone() : null)
                .email("email".equals(req.getRegisterType()) ? req.getEmail() : null)
                .isMember(0)
                .memberTier(tier)
                .role(UserRoles.USER)
                .build();
        userMapper.insert(user);
    }

    @Override
    public UserVO login(LoginReq req) {
        WdUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId());
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .isMember(user.getIsMember())
                .memberTier(user.getMemberTier())
                .role(UserRoles.normalize(user.getRole()))
                .preferredLocale(user.getPreferredLocale())
                .preferredAiRegion(user.getPreferredAiRegion() != null ? user.getPreferredAiRegion() : AiRegions.PREF_AUTO)
                .build();
    }

    @Override
    public void updateLocale(Long userId, String locale) {
        userMapper.updateLocale(userId, locale);
    }

    @Override
    public void updateAiRegion(Long userId, String region) {
        userMapper.updateAiRegion(userId, AiRegions.normalizePreference(region));
    }

    @Override
    public boolean isPhoneExists(String phone) {
        return userMapper.selectByPhone(phone) != null;
    }

    @Override
    public boolean isEmailExists(String email) {
        return userMapper.selectByEmail(email) != null;
    }

    @Override
    public void resetPasswordByPhone(String phone, String newPassword) {
        WdUser user = userMapper.selectByPhone(phone);
        if (user == null) {
            throw new BizException("该手机号未注册");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
    }

    @Override
    public void resetPasswordByEmail(String email, String newPassword) {
        WdUser user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BizException("该邮箱未注册");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
    }
}
