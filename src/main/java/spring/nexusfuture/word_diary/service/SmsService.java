package spring.nexusfuture.word_diary.service;

/** 短信验证码发送（第三方 SMS 网关）。 */
public interface SmsService {

    /**
     * 向手机号发送短信验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     */
    void sendVerifyCode(String phone, String code);
}
