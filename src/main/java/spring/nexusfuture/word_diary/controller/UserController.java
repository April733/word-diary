package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.LoginReq;
import spring.nexusfuture.word_diary.dto.RegisterReq;
import spring.nexusfuture.word_diary.dto.UserVO;
import spring.nexusfuture.word_diary.service.UserService;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public R<Void> register(@RequestBody RegisterReq req) {
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
}
