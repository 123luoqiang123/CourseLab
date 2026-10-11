package com.example.demo.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*") // ★★★ 依然解决跨域问题 ★★★
public class FileController {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    @PostMapping
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 拿到文件流
            InputStream inputStream = file.getInputStream();
            // 2. 生成一个全球唯一的随机文件名，防止别人上传同名文件把你覆盖了
            String originalFilename = file.getOriginalFilename();
            String fileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;

            // 3. 召唤阿里云 OSS 机器人
            OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 4. 把文件狠狠砸进 courselab-files 这个 Bucket 里！
            ossClient.putObject(bucketName, fileName, inputStream);

            // 5. 关掉机器人
            ossClient.shutdown();

            // 6. 拼凑出这个文件在公网上的绝对路径 (https://courselab-files.oss-cn-hangzhou.aliyuncs.com/xxx.jpg)
            String url = "https://" + bucketName + "." + endpoint + "/" + fileName;

            // 7. 把这个高级的 URL 丢给前端！
            return ResponseEntity.ok(url);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("上传云端失败: " + e.getMessage());
        }
    }
}