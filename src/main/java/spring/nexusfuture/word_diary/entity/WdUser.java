package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdUser {

    /** 用户主键 */
    private Long id;

    /** 账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 密码 */
    private String password;

    /** 邮箱地址 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;
}
