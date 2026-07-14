package spring.nexusfuture.word_diary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\+?\\d{6,20}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;

    @NotBlank(message = "验证码不能为空")
    private String smsCode;

    /** 是否会员 1是 0否（兼容旧字段，新版注册不再使用） */
    private Integer isMember;
}
