package spring.nexusfuture.word_diary.service.impl;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.JwtUtil;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.entity.SysMemberTier;
import spring.nexusfuture.word_diary.entity.WdInviteCode;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.entity.WdUserBenefit;
import spring.nexusfuture.word_diary.mapper.WdInviteCodeMapper;
import spring.nexusfuture.word_diary.mapper.WdUserBenefitMapper;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;
import spring.nexusfuture.word_diary.service.MemberTierService;
import spring.nexusfuture.word_diary.service.UserService;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final WdUserMapper userMapper;
    private final WdInviteCodeMapper inviteCodeMapper;
    private final WdUserBenefitMapper userBenefitMapper;
    private final MemberTierService memberTierService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(WdUserMapper userMapper, WdInviteCodeMapper inviteCodeMapper,
                           WdUserBenefitMapper userBenefitMapper,
                           MemberTierService memberTierService,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.inviteCodeMapper = inviteCodeMapper;
        this.userBenefitMapper = userBenefitMapper;
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
        WdUser phoneExist = userMapper.selectByPhone(req.getPhone());
        if (phoneExist != null) {
            throw new BizException("手机号已被注册");
        }
        // 校验邀请码
        WdInviteCode inviteCode = inviteCodeMapper.selectByCode(req.getInviteCode());
        if (inviteCode == null) {
            throw new BizException("邀请码不存在");
        }
        if (inviteCode.getUsedBy() != null) {
            throw new BizException("邀请码已被使用");
        }
        // 根据邀请码确定会员等级，并获取对应的权益配置
        Integer tier = inviteCode.getMemberTier() != null ? inviteCode.getMemberTier()
                : memberTierService.getDefaultTier().getTier();
        SysMemberTier tierConfig = memberTierService.getByTier(tier);

        WdUser user = WdUser.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getUsername())
                .phone(req.getPhone())
                .isMember(0)
                .memberTier(tier)
                .build();
        userMapper.insert(user);

        // 将等级配置中的权益写入用户权益表
        WdUserBenefit benefit = WdUserBenefit.builder()
                .userId(user.getId())
                .tierSnapshot(tier)
                .maxBookshelves(tierConfig.getMaxBookshelves())
                .maxWordbooks(tierConfig.getMaxWordbooks())
                .maxWordsPerBook(tierConfig.getMaxWordsPerBook())
                .dailyImportLimit(tierConfig.getDailyImportLimit())
                .dailyAiReadingLimit(tierConfig.getDailyAiReadingLimit())
                .isAiEnabled(tierConfig.getIsAiEnabled())
                .channel("invite_code")
                .channelRefId(inviteCode.getId())
                .acquiredAt(LocalDateTime.now())
                .expiredAt(null) // 邀请码权益永久有效
                .build();
        userBenefitMapper.insert(benefit);

        // 核销邀请码
        inviteCodeMapper.useInviteCode(req.getInviteCode(), user.getId());
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
                .preferredLocale(user.getPreferredLocale())
                .build();
    }

    @Override
    public void updateLocale(Long userId, String locale) {
        userMapper.updateLocale(userId, locale);
    }

    @Override
    public boolean isPhoneExists(String phone) {
        return userMapper.selectByPhone(phone) != null;
    }

    @Override
    public void resetPassword(String phone, String newPassword) {
        WdUser user = userMapper.selectByPhone(phone);
        if (user == null) {
            throw new BizException("该手机号未注册");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
    }
}
