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
import java.util.Map;
import spring.nexusfuture.word_diary.mapper.WdInviteCodeMapper;
import spring.nexusfuture.word_diary.service.FeedbackService;
import spring.nexusfuture.word_diary.service.MailService;
import spring.nexusfuture.word_diary.service.SmsService;
import spring.nexusfuture.word_diary.service.UserService;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final WdInviteCodeMapper inviteCodeMapper;
    private final SmsService smsService;
    private final MailService mailService;
    private final FeedbackService feedbackService;

    // 使用 Redis 存储验证码与速率限制，避免进程内存不一致与重启丢失
    private final StringRedisTemplate redisTemplate;
    // 回退内存存储（当 Redis 未配置时使用），仅开发/过渡期使用
    private final java.util.Map<String, String> codeStore = new java.util.concurrent.ConcurrentHashMap<>();

    public UserController(UserService userService, WdInviteCodeMapper inviteCodeMapper,
                          SmsService smsService, MailService mailService, StringRedisTemplate redisTemplate,
                          FeedbackService feedbackService) {
        this.userService = userService;
        this.inviteCodeMapper = inviteCodeMapper;
        this.smsService = smsService;
        this.mailService = mailService;
        this.redisTemplate = redisTemplate;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        // 根据注册方式校验对应的验证码
        String codeKey;
        if ("phone".equals(req.getRegisterType())) {
            if (req.getPhone() == null || req.getPhone().isBlank()) {
                return R.fail("手机号不能为空");
            }
            codeKey = "phone_" + req.getPhone();
        } else if ("email".equals(req.getRegisterType())) {
            if (req.getEmail() == null || req.getEmail().isBlank()) {
                return R.fail("邮箱不能为空");
            }
            codeKey = "email_" + req.getEmail();
        } else {
            return R.fail("注册方式不正确");
        }

        String storedCode = null;
        if (redisTemplate != null) {
            storedCode = redisTemplate.opsForValue().get(codeKey);
        } else {
            storedCode = codeStore.get(codeKey);
        }
        if (storedCode == null) {
            return R.fail("请先获取验证码");
        }
        if (!storedCode.equals(req.getCode())) {
            return R.fail("验证码错误");
        }
        // 验证通过后移除，防止重复使用
        if (redisTemplate != null) {
            redisTemplate.delete(codeKey);
        } else {
            codeStore.remove(codeKey);
        }

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
        String phone = req.getPhone();
        // 频率限制：每分钟 1 次，每小时最多 10 次（可调整）
        String rateMinuteKey = "ratelimit:phone:minute:" + phone;
        String rateHourKey = "ratelimit:phone:hour:" + phone;
        Long minuteCount = redisTemplate.opsForValue().increment(rateMinuteKey);
        if (minuteCount != null && minuteCount == 1) {
            redisTemplate.expire(rateMinuteKey, 60, TimeUnit.SECONDS);
        }
        if (minuteCount != null && minuteCount > 1) {
            return R.fail("发送过于频繁，请稍后再试");
        }
        Long hourCount = redisTemplate.opsForValue().increment(rateHourKey);
        if (hourCount != null && hourCount == 1) {
            redisTemplate.expire(rateHourKey, 1, TimeUnit.HOURS);
        }
        if (hourCount != null && hourCount > 10) {
            return R.fail("发送次数超出限制，请稍后再试");
        }

        // 生成6位随机验证码并存储到 Redis 或回退到内存，10 分钟过期
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        smsService.sendVerifyCode(phone, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("phone_" + phone, code, Duration.ofMinutes(10));
        } else {
            codeStore.put("phone_" + phone, code);
        }
        return R.ok();
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-mail-code")
    public org.springframework.http.ResponseEntity<R<Void>> sendMailCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return org.springframework.http.ResponseEntity.badRequest().body(R.fail("邮箱不能为空"));
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return org.springframework.http.ResponseEntity.badRequest().body(R.fail("邮箱格式不正确"));
        }
        // 检查邮箱是否已注册
        if (userService.isEmailExists(email)) {
            return org.springframework.http.ResponseEntity.badRequest().body(R.fail("该邮箱已被注册"));
        }
        // 如果邮件服务未启用，返回 503，提示运维/管理员配置 SMTP
        if (!mailService.isMailEnabled()) {
            return org.springframework.http.ResponseEntity.status(503).body(R.fail("邮件服务未配置，请联系管理员"));
        }
        // 频率限制：仅在 Redis 可用时启用；每分钟 1 次，每小时最多 10 次（可调整）
        if (redisTemplate != null) {
            String rateMinuteKey = "ratelimit:email:minute:" + email;
            String rateHourKey = "ratelimit:email:hour:" + email;
            Long minuteCount = redisTemplate.opsForValue().increment(rateMinuteKey);
            if (minuteCount != null && minuteCount == 1) {
                redisTemplate.expire(rateMinuteKey, 60, TimeUnit.SECONDS);
            }
            if (minuteCount != null && minuteCount > 1) {
                return org.springframework.http.ResponseEntity.status(429).body(R.fail("发送过于频繁，请稍后再试"));
            }
            Long hourCount = redisTemplate.opsForValue().increment(rateHourKey);
            if (hourCount != null && hourCount == 1) {
                redisTemplate.expire(rateHourKey, 1, TimeUnit.HOURS);
            }
            if (hourCount != null && hourCount > 10) {
                return org.springframework.http.ResponseEntity.status(429).body(R.fail("发送次数超出限制，请稍后再试"));
            }
        }

        // 生成6位随机验证码并存储到 Redis 或回退到内存，10 分钟过期
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        mailService.sendVerifyCode(email, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("email_" + email, code, Duration.ofMinutes(10));
        } else {
            codeStore.put("email_" + email, code);
        }
        return org.springframework.http.ResponseEntity.ok(R.ok());
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

    @GetMapping("/check-email")
    public R<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        return R.ok(exists);
    }

    /** 匿名/公开提交问题反馈（无需登录） */
    @PostMapping("/report-issue-public")
    public R<Void> reportIssuePublic(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "匿名用户");
        String reporterEmail = body.getOrDefault("reporterEmail", "");
        String subject = body.getOrDefault("subject", "");
        String description = body.getOrDefault("description", "");
        String errorDetail = body.getOrDefault("errorDetail", "");
        feedbackService.saveFeedback(null, username, reporterEmail, subject, description, errorDetail);
        return R.ok();
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
        String phone = req.getPhone();
        // 最多每分钟 1 次
        String rateMinuteKey = "ratelimit:phone_reset:minute:" + phone;
        Long minuteCount = redisTemplate.opsForValue().increment(rateMinuteKey);
        if (minuteCount != null && minuteCount == 1) {
            redisTemplate.expire(rateMinuteKey, 60, TimeUnit.SECONDS);
        }
        if (minuteCount != null && minuteCount > 1) {
            return R.fail("发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", (int) (Math.random() * 1000000));
        smsService.sendVerifyCode(phone, code);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("phone_reset_" + phone, code, Duration.ofMinutes(10));
        } else {
            codeStore.put("phone_reset_" + phone, code);
        }
        return R.ok();
    }

    /**
     * 通过手机号+验证码重置密码
     */
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        String storedCode = null;
        if (redisTemplate != null) {
            storedCode = redisTemplate.opsForValue().get("phone_reset_" + req.getPhone());
        } else {
            storedCode = codeStore.get("phone_reset_" + req.getPhone());
        }
        if (storedCode == null) {
            return R.fail("请先获取短信验证码");
        }
        if (!storedCode.equals(req.getSmsCode())) {
            return R.fail("验证码错误");
        }
        if (redisTemplate != null) {
            redisTemplate.delete("phone_reset_" + req.getPhone());
        } else {
            codeStore.remove("phone_reset_" + req.getPhone());
        }
        userService.resetPassword(req.getPhone(), req.getNewPassword());
        return R.ok();
    }
}
