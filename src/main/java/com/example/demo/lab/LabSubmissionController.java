package com.example.demo.lab;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.demo.application.CourseApplication;
import com.example.demo.application.CourseApplicationRepository;
import com.example.demo.auth.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class LabSubmissionController {

    @Autowired
    private LabSubmissionRepository labSubmissionRepository;

    @Autowired
    private CourseLabRepository courseLabRepository;

    @Autowired
    private LabSubmissionFileRepository labSubmissionFileRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    @Autowired
    private AiGradingService aiGradingService;

    // ★★★ 引入阿里云配置 ★★★
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    // ==========================================
    // ★★★ 接收提交 → 后端 AI 看图判卷 → 存分 (已升级 OSS) ★★★
    // ==========================================
    @PostMapping("/save")
    public ResponseEntity<?> saveSubmission(
            LabSubmission submission,
            @RequestParam(value = "labObjective", required = false) String labObjective,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        submission.setSubmitTime(LocalDateTime.now());

        // 1. 先落库为待批改状态
        submission.setStatus("0");
        submission.setScore(null);
        LabSubmission saved = labSubmissionRepository.save(submission);

        // 2. 后端 AI 批改
        Map<String, Object> ai = aiGradingService.grade(
                saved.getCodeContent(),
                saved.getLanguage(),
                labObjective,
                saved.getDependencies(),
                files);

        // 3. ★★★ 核心改动：把附件狠狠砸进阿里云 OSS ★★★
        if (files != null && files.length > 0) {
            // 召唤阿里云机器人
            OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                for (MultipartFile file : files) {
                    if (file.isEmpty()) continue;
                    try {
                        String originalFilename = file.getOriginalFilename();
                        String extension = originalFilename != null && originalFilename.contains(".")
                                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";

                        // 加上 submissions/ 文件夹前缀，方便你在阿里云后台管理
                        String newFilename = "submissions/" + UUID.randomUUID().toString().replace("-", "") + extension;

                        // 上传到云端！
                        ossClient.putObject(bucketName, newFilename, file.getInputStream());

                        // 拼凑云端绝对路径
                        String url = "https://" + bucketName + "." + endpoint + "/" + newFilename;

                        LabSubmissionFile labFile = new LabSubmissionFile();
                        labFile.setSubmissionId(saved.getId());
                        labFile.setFileName(originalFilename);
                        labFile.setFilePath(url); // ★ 这里存的不再是 C 盘路径，而是阿里云的 URL！

                        labSubmissionFileRepository.save(labFile);

                    } catch (IOException e) {
                        e.printStackTrace();
                        return ResponseEntity.internalServerError().body("文件上传云端失败：" + e.getMessage());
                    }
                }
            } finally {
                ossClient.shutdown(); // 确保用完关掉
            }
        }

        // 4. 按 AI 结果更新分数与状态
        if (ai.get("score") != null) {
            saved.setScore(((Number) ai.get("score")).intValue());
            saved.setAiFeedback(String.valueOf(ai.get("feedback")));
            saved.setStatus("1");
            labSubmissionRepository.save(saved);
        }

        // 5. 返回结果
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", saved.getId());
        resp.put("score", ai.get("score"));
        resp.put("feedback", ai.get("feedback"));
        resp.put("detail", ai.get("detail"));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserSubmissions(@PathVariable Long userId) {
        List<LabSubmission> submissions = labSubmissionRepository.findByUserIdOrderBySubmitTimeDesc(userId);
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (LabSubmission sub : submissions) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", sub.getId());
            map.put("labId", sub.getLabId());
            map.put("status", sub.getStatus());
            map.put("score", sub.getScore());
            map.put("submitTime", sub.getSubmitTime());
            map.put("codeContent", sub.getCodeContent());
            map.put("webLink", sub.getWebLink());
            map.put("dependencies", sub.getDependencies());

            String labTitle = "未知实验";
            if (sub.getLabId() != null) {
                CourseLab lab = courseLabRepository.findById(sub.getLabId()).orElse(null);
                if (lab != null) labTitle = lab.getTitle();
            }
            map.put("labTitle", labTitle);
            map.put("language", sub.getLanguage() != null ? sub.getLanguage() : "Python");
            map.put("memory", sub.getMemoryCost() != null ? sub.getMemoryCost() + " MB" : "0 MB");
            map.put("timeCost", sub.getTimeCost() != null ? sub.getTimeCost() + " ms" : "0 ms");
            map.put("codeLength", sub.getCodeContent() != null ? sub.getCodeContent().length() + " B" : "0 B");

            resultList.add(map);
        }
        return ResponseEntity.ok(resultList);
    }

    // ==========================================
    // ★★★ 下载接口升级：直接重定向到阿里云 ★★★
    // ==========================================
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadSubmissionFile(@PathVariable Integer fileId) {
        try {
            LabSubmissionFile labFile = labSubmissionFileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件记录不存在"));

            String fileUrl = labFile.getFilePath();

            // 如果是以 http 开头的阿里云链接，直接重定向让浏览器去下
            if (fileUrl != null && fileUrl.startsWith("http")) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(java.net.URI.create(fileUrl))
                        .build();
            } else {
                return ResponseEntity.badRequest().body("旧版本地文件已失效，请重新上传！");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllSubmissions() {
        List<LabSubmission> list = labSubmissionRepository.findAll();
        list.sort((a, b) -> b.getSubmitTime().compareTo(a.getSubmitTime()));
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (LabSubmission sub : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", sub.getId());
            map.put("labId", sub.getLabId());
            map.put("status", sub.getStatus());
            map.put("score", sub.getScore());
            map.put("submitTime", sub.getSubmitTime());
            map.put("isFeatured", sub.getIsFeatured());

            String labTitle = "未知实验";
            String displayName = null;
            if (sub.getLabId() != null) {
                CourseLab lab = courseLabRepository.findById(sub.getLabId()).orElse(null);
                if (lab != null) {
                    labTitle = lab.getTitle();
                    CourseApplication app = courseApplicationRepository
                            .findFirstByUserIdAndCourseIdAndStatus(sub.getUserId(), lab.getCourseId(), 1);
                    if (app != null) displayName = app.getRealName();
                }
            }
            map.put("labTitle", labTitle);
            map.put("studentName", displayName != null ? displayName : "未知学生");
            resultList.add(map);
        }
        return ResponseEntity.ok(resultList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubmissionById(@PathVariable Integer id) {
        LabSubmission sub = labSubmissionRepository.findById(id).orElse(null);
        if (sub == null) return ResponseEntity.badRequest().body("找不到该提交记录");

        Map<String, Object> map = new HashMap<>();
        map.put("id", sub.getId());
        map.put("labId", sub.getLabId());
        map.put("status", sub.getStatus());
        map.put("submitTime", sub.getSubmitTime());
        map.put("language", sub.getLanguage());
        map.put("timeCost", sub.getTimeCost());
        map.put("memoryCost", sub.getMemoryCost());
        map.put("codeContent", sub.getCodeContent());
        map.put("webLink", sub.getWebLink());
        map.put("dependencies", sub.getDependencies());
        map.put("isFeatured", sub.getIsFeatured());
        map.put("feedback", sub.getAiFeedback());
        map.put("aiFeedback", sub.getAiFeedback());
        map.put("score", sub.getScore());

        List<LabSubmissionFile> files = labSubmissionFileRepository.findBySubmissionId(id);
        List<Map<String, Object>> fileList = new ArrayList<>();
        if (files != null) {
            for (LabSubmissionFile f : files) {
                Map<String, Object> fMap = new HashMap<>();
                fMap.put("id", f.getId());
                fMap.put("fileName", f.getFileName());
                fileList.add(fMap);
            }
        }
        map.put("files", fileList);

        return ResponseEntity.ok(map);
    }

    @PostMapping("/{id}/grade")
    public ResponseEntity<?> gradeSubmission(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        LabSubmission sub = labSubmissionRepository.findById(id).orElse(null);
        if (sub == null) return ResponseEntity.badRequest().body("提交记录不存在");

        if (payload.get("score") != null) sub.setScore(Integer.valueOf(String.valueOf(payload.get("score"))));
        if (payload.get("feedback") != null) {
            sub.setAiFeedback(String.valueOf(payload.get("feedback")));
        } else if (payload.get("aiFeedback") != null) {
            sub.setAiFeedback(String.valueOf(payload.get("aiFeedback")));
        }
        sub.setStatus("1");
        labSubmissionRepository.save(sub);
        return ResponseEntity.ok("批改成功！");
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<String> reviewSubmission(@PathVariable Integer id, @RequestBody java.util.Map<String, Object> payload) {
        return labSubmissionRepository.findById(id).map(sub -> {
            if (payload.get("score") != null) sub.setScore(Integer.parseInt(String.valueOf(payload.get("score"))));
            if (payload.get("feedback") != null) sub.setAiFeedback(String.valueOf(payload.get("feedback")));
            sub.setStatus("1");
            labSubmissionRepository.save(sub);
            return ResponseEntity.ok("批改保存成功");
        }).orElse(ResponseEntity.badRequest().body("提交记录不存在"));
    }

    @PutMapping("/{id}/feature")
    public ResponseEntity<?> toggleFeature(@PathVariable Integer id) {
        return labSubmissionRepository.findById(id).map(sub -> {
            int cur = (sub.getIsFeatured() == null) ? 0 : sub.getIsFeatured();
            sub.setIsFeatured(cur == 1 ? 0 : 1);
            labSubmissionRepository.save(sub);
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("isFeatured", sub.getIsFeatured());
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.badRequest().build());
    }
}