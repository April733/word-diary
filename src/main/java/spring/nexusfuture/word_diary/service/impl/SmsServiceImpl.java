package spring.nexusfuture.word_diary.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeRequest;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.config.SmsConfig;
import spring.nexusfuture.word_diary.service.SmsService;

import java.util.UUID;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    private final SmsConfig smsConfig;
    private final Gson gson = new Gson();

    public SmsServiceImpl(SmsConfig smsConfig) {
        this.smsConfig = smsConfig;
    }

    @Override
    public void sendVerifyCode(String phone, String code) {
        if (smsConfig.getAccessKeyId() == null || smsConfig.getAccessKeyId().isBlank()) {
            log.warn("阿里云短信 AccessKey 未配置，模拟发送验证码到 {}: {}", phone, code);
            return;
        }

        try {
            DefaultProfile profile = DefaultProfile.getProfile(
                    "cn-hangzhou",
                    smsConfig.getAccessKeyId(),
                    smsConfig.getAccessKeySecret()
            );
            IAcsClient client = new DefaultAcsClient(profile);

            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest();
            request.setPhoneNumber(phone);
            request.setSignName(smsConfig.getSignName());
            request.setTemplateCode(smsConfig.getTemplateCode());
            request.setTemplateParam("{\"code\":\"" + code + "\",\"min\":\"5\"}");
            request.setOutId(UUID.randomUUID().toString());

            SendSmsVerifyCodeResponse response = client.getAcsResponse(request);

            log.info("阿里云短信回执: phone={}, code={}, message={}",
                    phone, response.getCode(), response.getMessage());

            if (!"OK".equals(response.getCode())) {
                throw new BizException("短信发送失败: " + response.getMessage());
            }

            log.info("短信验证码已发送到 {}", phone);
        } catch (BizException e) {
            throw e;
        } catch (ClientException e) {
            log.error("短信发送失败, phone={}, errorCode={}, errorMsg={}",
                    phone, e.getErrCode(), e.getErrMsg(), e);
            throw new BizException("短信发送失败: " + e.getErrMsg());
        }
    }
}
