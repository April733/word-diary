FROM eclipse-temurin:21-jre-alpine

# 安装 Ghostscript（PDF 压缩用）
RUN apk add --no-cache ghostscript

WORKDIR /app
RUN mkdir -p /app/logs
ENV LOG_PATH=/app/logs
ENV TZ=Asia/Shanghai
COPY target/word-diary-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
