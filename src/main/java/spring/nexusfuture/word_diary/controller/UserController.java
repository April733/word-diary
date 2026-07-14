package spring.nexusfuture.word_diary.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.entity.WdInviteCode;
import spring.nexusfuture.word_diary.mapper.WdInviteCodeMapper;
import spring.nexusfuture.word_diary.service.UserService;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final WdInviteCodeMapper inviteCodeMapper;

    public UserController(UserService userService, WdInviteCodeMapper inviteCodeMapper) {
        this.userService = userService;
        this.inviteCodeMapper = inviteCodeMapper;
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        userService.register(req);
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
}
