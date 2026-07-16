package spring.nexusfuture.word_diary.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.admin-to:aikoread@163.com}")
    private String adminTo;

    @Value("${app.mail.from:noreply@aikoread.top}")
    private String mailFrom;

    @Value("${app.mail.from-name:AIKoread}")
    private String mailFromName;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void init() {
        log.info("===== MailService 配置加载 =====");
        log.info("mailEnabled: {}", mailEnabled);
        log.info("mailFrom: {}", mailFrom);
        log.info("mailFromName: {}", mailFromName);
        log.info("adminTo: {}", adminTo);
        log.info("=================================");
    }

    public boolean isMailEnabled() {
        return mailEnabled;
    }

    public String getAdminTo() {
        return adminTo;
    }

    public void sendVerifyCode(String to, String code) {
        if (!mailEnabled) {
            log.warn("邮件服务未启用，跳过发送验证码。to={}", to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject("[Word Diary] 邮箱验证码");
            helper.setText(String.format(
                    "您的验证码是：%s\n\n" +
                    "该验证码 10 分钟内有效，请勿泄露给他人。\n\n" +
                    "Word Diary",
                    code
            ));
            mailSender.send(message);
            log.info("邮箱验证码已发送至 {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("发送邮箱验证码失败: {}", e.getMessage(), e);
        }
    }

    public void sendBugReport(String username, String description, String errorDetail) {
        if (!mailEnabled) {
            log.warn("邮件服务未启用，跳过发送。报告内容: user={}, description={}", username, description);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(adminTo);
            helper.setSubject("[Word Diary] 用户问题报告 - " + username);
            helper.setText(String.format(
                    "用户反馈问题报告\n\n" +
                    "用户: %s\n" +
                    "问题描述: %s\n" +
                    "错误详情: %s",
                    username, description, errorDetail
            ));
            mailSender.send(message);
            log.info("问题报告邮件已发送至 {}, user={}", adminTo, username);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("发送问题报告邮件失败: {}", e.getMessage(), e);
        }
    }
}
