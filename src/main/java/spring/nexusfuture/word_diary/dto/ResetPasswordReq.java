package spring.nexusfuture.word_diary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordReq {

    /** phone | email，与注册方式对应 */
    @NotBlank(message = "找回方式不能为空")
    private String resetType;

    private String phone;

    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{6,20}$",
             message = "密码需为6-20位，且包含大小写字母、数字和符号")
    private String newPassword;
}
