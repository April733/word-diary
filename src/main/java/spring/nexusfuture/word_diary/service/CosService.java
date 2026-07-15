package spring.nexusfuture.word_diary.service;

import java.io.InputStream;

/**
 * 腾讯云 COS 对象存储服务接口
 */
public interface CosService {

    /**
     * 上传文件到 COS
     *
     * @param key         COS 对象键（路径）
     * @param inputStream 文件输入流
     * @param contentLength 文件大小
     * @param contentType 文件 MIME 类型
     * @return COS 访问 URL
     */
    String upload(String key, InputStream inputStream, long contentLength, String contentType);

    /**
     * 生成带签名的临时访问 URL（用于下载/读取）
     *
     * @param key COS 对象键
     * @return 签名 URL（默认 1 小时有效）
     */
    String generatePresignedUrl(String key);

    /**
     * 删除 COS 中的文件
     *
     * @param key COS 对象键
     */
    void delete(String key);

    /**
     * 下载 COS 文件并返回输入流
     *
     * @param key COS 对象键
     * @return 文件输入流（调用方负责关闭）
     */
    InputStream download(String key);

    /**
     * 获取文件大小
     *
     * @param key COS 对象键
     * @return 文件大小（字节），文件不存在返回 -1
     */
    long getFileSize(String key);
}
