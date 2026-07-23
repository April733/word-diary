package spring.nexusfuture.word_diary.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e) {
        int status = e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 400;
        return ResponseEntity.status(status).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isEmpty()) {
            message = "参数校验失败";
        }
        return ResponseEntity.badRequest().body(R.fail(400, message));
    }

    /** 爬虫/浏览器扩展探测不存在的静态路径，不当成系统故障 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResource(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(404, "not found"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("数据写入失败", e);
        String detail = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (detail != null && detail.toLowerCase().contains("feature_key")) {
            return ResponseEntity.status(500).body(R.fail(500,
                    "保存失败：feature_key 过长，请确认 wd_ai_message.feature_key 已扩展为 varchar(255)"));
        }
        if (detail != null && (detail.contains("Data too long") || detail.contains("too long"))) {
            return ResponseEntity.status(500).body(R.fail(500, "保存失败：字段超长，请检查数据库列长度"));
        }
        return ResponseEntity.status(500).body(R.fail(500, "保存失败，请稍后重试或联系管理员"));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<R<Void>> handleResourceAccess(ResourceAccessException e) {
        log.warn("上游 AI/网络调用失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(R.fail(502, "AI 线路调用失败，请稍后重试或切换线路"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("未处理异常", e);
        // 不对用户暴露 SQL/堆栈细节
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "保存失败，请稍后重试或联系管理员"));
    }
}
