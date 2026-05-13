package com.fund.assistant.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.fund.assistant.config.OssConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.fund.assistant.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
public class OssService {

    @Autowired
    private OssConfig ossConfig;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        ossClient = new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );
        log.info("OSS客户端初始化完成，Bucket：{}，Endpoint：{}", ossConfig.getBucketName(), ossConfig.getEndpoint());
    }

    /**
     * 上传头像文件到OSS
     * @param file 上传的文件
     * @return 可公开访问的头像URL
     */
    public String uploadAvatar(MultipartFile file) {
        String objectName = ossConfig.getAvatarPrefix() + generateObjectName(file);
        return doUpload(file, objectName, "头像上传失败，请稍后重试");
    }

    /**
     * 上传通用图片到OSS（用于AI图片识别等场景）
     * @param file 上传的图片文件
     * @return 可公开访问的图片URL
     */
    public String uploadImage(MultipartFile file) {
        String objectName = "chat/image/" + generateObjectName(file);
        return doUpload(file, objectName, "图片上传失败，请稍后重试");
    }

    private String generateObjectName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private String doUpload(MultipartFile file, String objectName, String errorMsg) {
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectResult result = ossClient.putObject(ossConfig.getBucketName(), objectName, inputStream);
            log.debug("OSS上传成功，ETag：{}，路径：{}", result.getETag(), objectName);
        } catch (IOException e) {
            log.error("OSS上传失败", e);
            throw new BusinessException(errorMsg);
        }
        return "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + objectName;
    }

    /**
     * 根据URL删除OSS文件
     */
    public void deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }
        try {
            // 从URL中提取objectName
            String prefix = "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/";
            if (!fileUrl.startsWith(prefix)) {
                return;
            }
            String objectName = fileUrl.substring(prefix.length());
            ossClient.deleteObject(ossConfig.getBucketName(), objectName);
            log.info("OSS删除成功：{}", objectName);
        } catch (Exception e) {
            log.warn("OSS删除文件失败：{}", fileUrl, e);
        }
    }
}
