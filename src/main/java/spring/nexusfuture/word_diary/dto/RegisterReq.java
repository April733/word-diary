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

    /** 手机号（与邮箱二选一） */
    @Pattern(regexp = "^\\+?\\d{6,20}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱（与手机号二选一） */
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 注册方式：phone 或 email */
    @NotBlank(message = "注册方式不能为空")
    private String registerType;

    /** 是否会员 1是 0否（兼容旧字段，新版注册不再使用） */
    private Integer isMember;
}
