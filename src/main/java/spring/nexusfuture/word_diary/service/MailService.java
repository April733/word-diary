package spring.nexusfuture.word_diary.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.admin-to:april@example.com}")
    private String adminTo;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送问题报告给管理员
     */
    public void sendBugReport(String username, String description, String errorDetail) {
        if (!mailEnabled) {
            log.warn("邮件服务未启用，跳过发送。报告内容: user={}, description={}", username, description);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("word-diary@nexusfuture.com");
            message.setTo(adminTo);
            message.setSubject("[Word Diary] 用户问题报告 - " + username);
            message.setText(String.format(
                    "用户反馈问题报告\n\n" +
                    "用户: %s\n" +
                    "问题描述: %s\n" +
                    "错误详情: %s",
                    username, description, errorDetail
            ));
            mailSender.send(message);
            log.info("问题报告邮件已发送至 {}, user={}", adminTo, username);
        } catch (Exception e) {
            log.error("发送问题报告邮件失败: {}", e.getMessage());
        }
    }
}
