package spring.nexusfuture.word_diary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendSmsReq {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\+?\\d{6,20}$", message = "手机号格式不正确")
    private String phone;
}
