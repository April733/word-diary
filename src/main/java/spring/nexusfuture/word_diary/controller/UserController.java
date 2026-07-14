package spring.nexusfuture.word_diary.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.ResetPasswordReq;
import spring.nexusfuture.word_diary.dto.SendSmsReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.entity.WdInviteCode;
import spring.nexusfuture.word_diary.mapper.WdInviteCodeMapper;
import spring.nexusfuture.word_diary.service.SmsService;
import spring.nexusfuture.word_diary.service.UserService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final WdInviteCodeMapper inviteCodeMapper;
    private final SmsService smsService;

    /** 内存存储验证码：phone -> code，生产环境建议使用 Redis */
    private final Map<String, String> smsCodeStore = new ConcurrentHashMap<>();

    public UserController(UserService userService, WdInviteCodeMapper inviteCodeMapper,
                          SmsService smsService) {
        this.userService = userService;
        this.inviteCodeMapper = inviteCodeMapper;
        this.smsService = smsService;
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        // 校验短信验证码
        String storedCode = smsCodeStore.get(req.getPhone());
        if (storedCode == null) {
            return R.fail("请先获取短信验证码");
        }
        if (!storedCode.equals(req.getSmsCode())) {
            return R.fail("验证码错误");
        }
        // 验证通过后移除，防止重复使用
        smsCodeStore.remove(req.getPhone());

        userService.register(req);
        return R.ok();
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/send-sms")
    public R<Void> sendSms(@Valid @RequestBody SendSmsReq req) {
        // 检查手机号是否已注册
        if (userService.isPhoneExists(req.getPhone())) {
            return R.fail("该手机号已被注册");
        }
        // 生成6位随机验证码
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        smsService.sendVerifyCode(req.getPhone(), code);
        // 存储验证码
        smsCodeStore.put(req.getPhone(), code);
        return R.ok();
    }

    @PostMapping("/login")
    public R<UserVO> login(@RequestBody LoginReq req) {
        UserVO vo = userService.login(req);
        return R.ok(vo);
    }

    @PutMapping("/locale")
    public R<Void> updateLocale(@RequestAttribute Long userId, @RequestBody Map<String, String> body) {
        String locale = body.get("locale");
        userService.updateLocale(userId, locale);
        return R.ok();
    }

    @GetMapping("/check-phone")
    public R<Boolean> checkPhone(@RequestParam String phone) {
        boolean exists = userService.isPhoneExists(phone);
        return R.ok(exists);
    }

    /**
     * 校验邀请码是否有效：存在且未被使用
     * @return true=有效，false=无效
     */
    @GetMapping("/check-invite-code")
    public R<Boolean> checkInviteCode(@RequestParam String code) {
        WdInviteCode invite = inviteCodeMapper.selectByCode(code);
        if (invite == null) {
            return R.ok(false);
        }
        // 已被核销则视为无效
        boolean valid = invite.getUsedBy() == null;
        return R.ok(valid);
    }

    /**
     * 发送密码找回短信验证码（要求手机号已注册）
     */
    @PostMapping("/send-reset-sms")
    public R<Void> sendResetSms(@Valid @RequestBody SendSmsReq req) {
        if (!userService.isPhoneExists(req.getPhone())) {
            return R.fail("该手机号未注册");
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        smsService.sendVerifyCode(req.getPhone(), code);
        smsCodeStore.put("reset_" + req.getPhone(), code);
        return R.ok();
    }

    /**
     * 通过手机号+验证码重置密码
     */
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        String storedCode = smsCodeStore.get("reset_" + req.getPhone());
        if (storedCode == null) {
            return R.fail("请先获取短信验证码");
        }
        if (!storedCode.equals(req.getSmsCode())) {
            return R.fail("验证码错误");
        }
        smsCodeStore.remove("reset_" + req.getPhone());
        userService.resetPassword(req.getPhone(), req.getNewPassword());
        return R.ok();
    }
}
