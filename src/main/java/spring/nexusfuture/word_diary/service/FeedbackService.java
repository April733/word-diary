package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdFeedback;

import java.util.List;

/** 用户问题反馈与 Bug 报告。 */
public interface FeedbackService {

    /**
     * 保存用户反馈（可选同步发邮件通知管理员）。
     *
     * @param userId 提交用户 ID，可为 null
     * @param username 用户名或昵称
     * @param reporterEmail 联系邮箱，可为空
     * @param subject 主题
     * @param description 问题描述
     * @param errorDetail 错误堆栈或详情，可为空
     * @return 持久化后的反馈实体
     */
    WdFeedback saveFeedback(Long userId, String username, String reporterEmail, String subject, String description, String errorDetail);

    /**
     * 列出全部反馈（管理端）。
     *
     * @return 反馈列表
     */
    List<WdFeedback> listAll();

    /**
     * 按用户 ID 列出反馈。
     *
     * @param userId 用户 ID
     * @return 该用户的反馈列表
     */
    List<WdFeedback> listByUserId(Long userId);

    /**
     * 按处理状态筛选反馈。
     *
     * @param status 状态码
     * @return 匹配的反馈列表
     */
    List<WdFeedback> listByStatus(Integer status);

    /**
     * 更新反馈处理状态。
     *
     * @param id 反馈 ID
     * @param status 新状态码
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除反馈记录。
     *
     * @param id 反馈 ID
     */
    void deleteFeedback(Long id);
}
