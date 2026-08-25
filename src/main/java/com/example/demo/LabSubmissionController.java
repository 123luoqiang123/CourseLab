package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
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




    // ==========================================
    // ★★★ 核心升级：同时接收文本数据与多个附件 ★★★
    // ==========================================
    // ==========================================
    // ★★★ 接收提交 → 后端 AI 看图判卷 → 存分 ★★★
    // ==========================================
    @PostMapping("/save")
    public ResponseEntity<?> saveSubmission(
            LabSubmission submission,
            @RequestParam(value = "labObjective", required = false) String labObjective,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        submission.setSubmitTime(LocalDateTime.now());

        // 1. 先落库为待批改状态（就算 AI 挂了，这条记录也已经在库里）
        submission.setStatus("0");
        submission.setScore(null);
        LabSubmission saved = labSubmissionRepository.save(submission);

        // 2. 后端 AI 批改（前端传什么分数一律不信，趁文件还在内存先批）
        Map<String, Object> ai = aiGradingService.grade(
                saved.getCodeContent(),
                saved.getLanguage(),
                labObjective,
                saved.getDependencies(),
                files);

        // 3. 保存附件到磁盘（沿用你原来的逻辑，一行没改）
        if (files != null && files.length > 0) {
            String uploadDir = System.getProperty("user.dir") + "/uploads/student_submissions/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                try {
                    String originalFilename = file.getOriginalFilename();
                    String extension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
                    String newFilename = UUID.randomUUID().toString() + extension;

                    File dest = new File(uploadDir + newFilename);
                    file.transferTo(dest);

                    LabSubmissionFile labFile = new LabSubmissionFile();
                    labFile.setSubmissionId(saved.getId());
                    labFile.setFileName(originalFilename);
                    labFile.setFilePath(dest.getAbsolutePath());

                    labSubmissionFileRepository.save(labFile);

                } catch (IOException e) {
                    e.printStackTrace();
                    return ResponseEntity.internalServerError().body("文件保存失败：" + e.getMessage());
                }
            }
        }

        // 4. 按 AI 结果更新分数与状态
        if (ai.get("score") != null) {
            saved.setScore(((Number) ai.get("score")).intValue());
            saved.setAiFeedback(String.valueOf(ai.get("feedback")));
            saved.setStatus("1");
            labSubmissionRepository.save(saved);
        }

        // 5. 把批改结果返回给前端展示
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", saved.getId());
        resp.put("score", ai.get("score"));
        resp.put("feedback", ai.get("feedback"));
        resp.put("detail", ai.get("detail"));
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // ★★★ 动态加载：获取历史提交记录 ★★★
    // ==========================================
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
                if (lab != null) {
                    labTitle = lab.getTitle();
                }
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

    @GetMapping("/download/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadSubmissionFile(@PathVariable Integer fileId) {
        try {
            LabSubmissionFile labFile = labSubmissionFileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件记录不存在"));

            java.io.File file = new java.io.File(labFile.getFilePath());
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
            String encodedFilename = java.net.URLEncoder.encode(labFile.getFileName(), "UTF-8").replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // ==========================================
    // ★★★ 批改大厅数据接口 ★★★
    // ==========================================


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
            if (sub.getLabId() != null) {
                CourseLab lab = courseLabRepository.findById(sub.getLabId()).orElse(null);
                if (lab != null) {
                    labTitle = lab.getTitle();
                }
            }
            map.put("labTitle", labTitle);

            // ★ 作者显示课程内真实姓名：labId → courseId → course_application.real_name
            String displayName = null;
            CourseLab lab = courseLabRepository.findById(sub.getLabId()).orElse(null);
            if (lab != null) {
                CourseApplication app = courseApplicationRepository
                        .findFirstByUserIdAndCourseIdAndStatus(sub.getUserId(), lab.getCourseId(), 1);
                if (app != null) displayName = app.getRealName();
            }
            map.put("studentName", displayName != null ? displayName : "未知学生");

            resultList.add(map);
        }

        return ResponseEntity.ok(resultList);
    }

    // ==========================================
    // ★★★ 1. 获取单条提交记录详细信息 (连上文件和链接，专门喂给抽屉) ★★★
    // ==========================================
    @GetMapping("/{id}")
    public ResponseEntity<?> getSubmissionById(@PathVariable Integer id) {
        LabSubmission sub = labSubmissionRepository.findById(id).orElse(null);
        if (sub == null) {
            return ResponseEntity.badRequest().body("找不到该提交记录");
        }

        // 把基础数据、文件、网页链接组装成一个 Map 传给前端，这样抽屉才读得到！
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

        // ★ 统一评价展示 (不管之前存的是什么，前端统一下发)
        map.put("feedback", sub.getAiFeedback());
        map.put("aiFeedback", sub.getAiFeedback()); // 兜底防止前端报 undefined
        map.put("score", sub.getScore());

        // ★ 查询关联的附件
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

    // ==========================================
    // ★★★ 2. 老师最终批改接口 (保存分数 + 统一评语) ★★★
    // ==========================================
    @PostMapping("/{id}/grade")
    public ResponseEntity<?> gradeSubmission(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        LabSubmission sub = labSubmissionRepository.findById(id).orElse(null);
        if (sub == null) {
            return ResponseEntity.badRequest().body("提交记录不存在");
        }

        if (payload.get("score") != null) {
            sub.setScore(Integer.valueOf(String.valueOf(payload.get("score"))));
        }

        // ★ 统一评价保存：前端发 feedback 还是 aiFeedback，我们统统存入底层唯一的字段
        if (payload.get("feedback") != null) {
            sub.setAiFeedback(String.valueOf(payload.get("feedback")));
        } else if (payload.get("aiFeedback") != null) {
            sub.setAiFeedback(String.valueOf(payload.get("aiFeedback")));
        }

        sub.setStatus("1");
        labSubmissionRepository.save(sub);

        return ResponseEntity.ok("批改成功！");
    }

    // ★ 教师人工批改：保存分数和评语，状态标为已人工批改
    @PutMapping("/{id}/review")
    public ResponseEntity<String> reviewSubmission(@PathVariable Integer id,
                                                   @RequestBody java.util.Map<String, Object> payload) {
        return labSubmissionRepository.findById(id).map(sub -> {
            if (payload.get("score") != null) {
                sub.setScore(Integer.parseInt(String.valueOf(payload.get("score"))));
            }
            if (payload.get("feedback") != null) {
                // 表里评语存在 ai_feedback 列，人工批改直接覆盖它
                sub.setAiFeedback(String.valueOf(payload.get("feedback")));
            }
            sub.setStatus("1");   // 1 = 已批改
            labSubmissionRepository.save(sub);
            return ResponseEntity.ok("批改保存成功");
        }).orElse(ResponseEntity.badRequest().body("提交记录不存在"));
    }

    // ★ 切换优秀作品：点一下星星调一次，1↔0 来回切
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