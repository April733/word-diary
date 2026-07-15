package spring.nexusfuture.word_diary.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库启动初始化：补全缺失字段
 */
@Slf4j
@Component
public class DatabaseInitConfig implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 检查 wd_book 表是否已有 file_size 字段（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
            var rs = conn.getMetaData().getColumns(null, null, "wd_book", "file_size");
            if (!rs.next()) {
                stmt.execute("ALTER TABLE wd_book ADD COLUMN file_size bigint DEFAULT 0 COMMENT '文件大小(字节)' AFTER file_type");
                log.info("数据库字段初始化完成: wd_book.file_size 已添加");
            } else {
                log.info("数据库字段初始化: file_size 字段已存在，跳过");
            }
            rs.close();

            // 检查 wd_ai_message 表是否已有 parent_id 字段
            var rs2 = conn.getMetaData().getColumns(null, null, "wd_ai_message", "parent_id");
            if (!rs2.next()) {
                stmt.execute("ALTER TABLE wd_ai_message ADD COLUMN parent_id bigint DEFAULT NULL COMMENT '关联的问题ID' AFTER session_id");
                log.info("数据库字段初始化完成: wd_ai_message.parent_id 已添加");
            } else {
                log.info("数据库字段初始化: parent_id 字段已存在，跳过");
            }
            rs2.close();

            // 修改 wd_ai_message.feature_key 长度（多选功能拼接后可能超过32）
            var rs3 = conn.getMetaData().getColumns(null, null, "wd_ai_message", "feature_key");
            if (rs3.next()) {
                int colSize = rs3.getInt("COLUMN_SIZE");
                if (colSize < 255) {
                    stmt.execute("ALTER TABLE wd_ai_message MODIFY COLUMN feature_key varchar(255) DEFAULT NULL COMMENT '触发此消息的AI功能标识'");
                    log.info("数据库字段初始化完成: wd_ai_message.feature_key 已扩展为 varchar(255)");
                }
            }
            rs3.close();
        } catch (Exception e) {
            log.warn("数据库字段初始化警告: {}", e.getMessage());
        }
    }
}
