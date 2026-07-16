package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
public class LogAspect {

    private final ObjectMapper objectMapper;

    public LogAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Pointcut("execution(* spring.nexusfuture.word_diary.controller..*(..))")
    public void controllerPointcut() {
    }

    @Pointcut("execution(* spring.nexusfuture.word_diary.service..*(..))")
    public void servicePointcut() {
    }

    @Around("controllerPointcut() || servicePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 获取请求 URL
        String url = "";
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                url = request.getMethod() + " " + request.getRequestURI();
            }
        } catch (Exception ignored) {
        }

        // 入参（过滤掉 HttpServletRequest/HttpServletResponse 等）
        Object[] args = joinPoint.getArgs();
        String params = Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest)
                        && !(arg instanceof jakarta.servlet.http.HttpServletResponse))
                .map(arg -> {
                    try {
                        return objectMapper.writeValueAsString(arg);
                    } catch (Exception e) {
                        return String.valueOf(arg);
                    }
                })
                .collect(Collectors.joining(", "));

        log.info(">>> [{}] {}.{} | 入参: {}", url, className, methodName, params);

        long start = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            log.error("<<< [{}] {}.{} | 异常: {} | 耗时: {}ms",
                    url, className, methodName, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;

        // 出参（跳过流式类型，避免 Jackson 误订阅 Flux）
        String resultStr;
        if (result instanceof org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                || result instanceof org.reactivestreams.Publisher) {
            resultStr = result.getClass().getSimpleName();
        } else {
            try {
                resultStr = objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                resultStr = String.valueOf(result);
            }
        }

        // 如果出参过长截断
        if (resultStr.length() > 2000) {
            resultStr = resultStr.substring(0, 2000) + "...(truncated)";
        }

        log.info("<<< [{}] {}.{} | 耗时: {}ms | 出参: {}", url, className, methodName, elapsed, resultStr);

        return result;
    }
}
