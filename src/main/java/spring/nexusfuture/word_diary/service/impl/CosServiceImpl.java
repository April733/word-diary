package spring.nexusfuture.word_diary.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.service.CosService;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;

@Slf4j
@Service
public class CosServiceImpl implements CosService {

    @Value("${app.cos.secret-id}")
    private String secretId;

    @Value("${app.cos.secret-key}")
    private String secretKey;

    @Value("${app.cos.region:ap-guangzhou}")
    private String region;

    @Value("${app.cos.bucket:}")
    private String bucket;

    @Value("${app.cos.book-prefix:books/}")
    private String bookPrefix;

    private COSClient cosClient;

    @PostConstruct
    public void init() {
        if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            log.warn("COS 配置未设置 (secretId/secretKey 为空)，COS 服务将不可用");
            return;
        }
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.cosClient = new COSClient(cred, clientConfig);
        log.info("COS 客户端初始化完成: region={}, bucket={}", region, bucket);
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("COS 客户端已关闭");
        }
    }

    private void ensureClient() {
        if (cosClient == null) {
            throw new BizException("COS 服务未配置，请设置 COS_SECRET_ID 和 COS_SECRET_KEY 环境变量");
        }
    }

    private String buildKey(String key) {
        if (key.startsWith(bookPrefix)) {
            return key;
        }
        return bookPrefix + key;
    }

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        ensureClient();
        String fullKey = buildKey(key);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }

        PutObjectRequest putRequest = new PutObjectRequest(bucket, fullKey, inputStream, metadata);
        try {
            cosClient.putObject(putRequest);
            log.info("COS 上传成功: key={}, size={} bytes", fullKey, contentLength);
        } catch (CosClientException e) {
            log.error("COS 上传失败: key={}", fullKey, e);
            throw new BizException("文件上传到 COS 失败: " + e.getMessage());
        }

        // 返回存储的 key（相对路径），便于后续生成签名 URL
        return fullKey;
    }

    @Override
    public String generatePresignedUrl(String key) {
        ensureClient();
        if (bucket == null || bucket.isBlank()) {
            throw new BizException("COS bucket 未配置");
        }
        if (key == null || key.isBlank()) {
            throw new BizException("文件路径为空");
        }
        String fullKey = buildKey(key);

        Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000L); // 1 小时
        try {
            com.qcloud.cos.model.GeneratePresignedUrlRequest req =
                    new com.qcloud.cos.model.GeneratePresignedUrlRequest(bucket, fullKey, HttpMethodName.GET);
            req.setExpiration(expiration);
            URL url = cosClient.generatePresignedUrl(req);
            if (url == null) {
                throw new BizException("生成文件访问链接失败: 返回空 URL");
            }
            String result = url.toString();
            log.info("生成签名 URL: key={}, urlHost={}", fullKey, url.getHost());
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成签名 URL 失败: key={}, bucket={}, region={}", fullKey, bucket, region, e);
            throw new BizException("生成文件访问链接失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        ensureClient();
        String fullKey = buildKey(key);
        try {
            cosClient.deleteObject(bucket, fullKey);
            log.info("COS 删除成功: key={}", fullKey);
        } catch (CosClientException e) {
            log.error("COS 删除失败: key={}", fullKey, e);
            // 删除失败不阻断业务，仅记录日志
        }
    }

    @Override
    public InputStream download(String key) {
        ensureClient();
        String fullKey = buildKey(key);
        try {
            GetObjectRequest getRequest = new GetObjectRequest(bucket, fullKey);
            COSObject cosObject = cosClient.getObject(getRequest);
            log.info("COS 下载: key={}, size={}", fullKey, cosObject.getObjectMetadata().getContentLength());
            return cosObject.getObjectContent();
        } catch (CosClientException e) {
            log.error("COS 下载失败: key={}", fullKey, e);
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public long getFileSize(String key) {
        ensureClient();
        String fullKey = buildKey(key);
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(bucket, fullKey);
            return metadata.getContentLength();
        } catch (CosClientException e) {
            log.error("COS 获取文件大小失败: key={}", fullKey, e);
            return -1;
        }
    }
}
