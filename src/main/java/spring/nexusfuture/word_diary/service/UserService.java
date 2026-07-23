package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.request.LoginReq;
import spring.nexusfuture.word_diary.dto.request.RegisterReq;
import spring.nexusfuture.word_diary.dto.response.UserVO;
import spring.nexusfuture.word_diary.entity.WdUser;

/** 用户注册、登录、资料与密码管理。 */
public interface UserService {

    /**
     * 注册新用户（校验手机/邮箱唯一性与验证码）。
     *
     * @param req 注册信息（账号、密码、验证码等）
     */
    void register(RegisterReq req);

    /**
     * 用户登录，校验凭证并签发会话。
     *
     * @param req 登录请求（账号、密码等）
     * @return 用户信息 VO（含 token）
     */
    UserVO login(LoginReq req);

    /**
     * 更新用户界面语言偏好。
     *
     * @param userId 当前用户 ID
     * @param locale 语言区域，如 zh-CN
     */
    void updateLocale(Long userId, String locale);

    /**
     * 更新用户 AI 路由区偏好（auto/cn/global）。
     *
     * @param userId 当前用户 ID
     * @param region 路由区偏好
     */
    void updateAiRegion(Long userId, String region);

    /**
     * 判断手机号是否已被注册。
     *
     * @param phone 手机号
     * @return 已存在为 true
     */
    boolean isPhoneExists(String phone);

    /**
     * 判断邮箱是否已被注册。
     *
     * @param email 邮箱
     * @return 已存在为 true
     */
    boolean isEmailExists(String email);

    /**
     * 通过手机验证码重置密码。
     *
     * @param phone 手机号
     * @param newPassword 新密码
     */
    void resetPasswordByPhone(String phone, String newPassword);

    /**
     * 通过邮箱验证码重置密码。
     *
     * @param email 邮箱
     * @param newPassword 新密码
     */
    void resetPasswordByEmail(String email, String newPassword);

    /**
     * 鉴权用：按 ID 查用户，不存在返回 null。
     *
     * @param userId 用户 ID
     * @return 用户实体；不存在时为 null
     */
    WdUser findById(Long userId);
}
