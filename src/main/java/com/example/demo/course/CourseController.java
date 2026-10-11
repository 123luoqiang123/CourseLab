package com.example.demo.course;

import com.example.demo.application.CourseApplicationRepository;
import com.example.demo.application.CourseEnrollment;
import com.example.demo.application.CourseEnrollmentRepository;
import com.example.demo.auth.Teacher;
import com.example.demo.auth.TeacherRepository;
import com.example.demo.lab.CourseLab;
import com.example.demo.lab.CourseLabFile;
import com.example.demo.lab.CourseLabFileRepository;
import com.example.demo.lab.CourseLabRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;
import java.util.Map;

@RestController
public class CourseController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseChapterRepository courseChapterRepository;

    @Autowired
    private CourseLabRepository courseLabRepository;

    @Autowired
    private CourseEnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseApplicationRepository applicationRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseLabFileRepository courseLabFileRepository;



    // ==========================================
    // ★★★ 核心新增：生成8位不重复(大写+数字)加课码的防撞工具方法 ★★★
    // ==========================================
    private String generateUniqueInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (courseRepository.existsByInviteCode(code)); // 如果数据库里已经有了，就重新生成！绝对防撞！
        return code;
    }

    // ==========================================
    // ★★★ 核心新增：根据加课码查询课程基本信息 (用于学生弹窗确认) ★★★
    // ==========================================
    @GetMapping("/courses/invite/{code}")
    public ResponseEntity<?> getCourseByInviteCode(@PathVariable String code) {
        Optional<Course> courseOpt = courseRepository.findByInviteCode(code);
        if (courseOpt.isPresent()) {
            return ResponseEntity.ok(courseOpt.get());
        }
        return ResponseEntity.badRequest().body("加课码无效或课程不存在");
    }

    @GetMapping("/courses")
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @GetMapping("/courses/{id}")
    public Course getCourseById(@PathVariable Long id) {
        return courseRepository.findById(id).orElse(null);
    }

    @GetMapping("/courses/{id}/chapters")
    public List<CourseChapter> getCourseChapters(@PathVariable Long id) {
        return courseChapterRepository.findByCourseIdOrderBySortOrderAsc(id);
    }

    @GetMapping("/courses/{id}/labs")
    public List<CourseLab> getCourseLabs(@PathVariable Long id) {
        return courseLabRepository.findByCourseId(id);
    }

    @GetMapping("/teachers/{id}")
    public Teacher getTeacherById(@PathVariable Long id) {
        return teacherRepository.findById(id).orElse(null);
    }

    @PostMapping("/courses/{courseId}/chapters")
    public CourseChapter addVideoChapter(@PathVariable Long courseId, @RequestBody CourseChapter chapter) {
        chapter.setCourseId(courseId);
        return courseChapterRepository.save(chapter);
    }

    // ==========================================
    // ★★★ 核心升级：创建课程与模板深拷贝克隆 (带加课码) ★★★
    // ==========================================
    @PostMapping("/courses")
    @Transactional // ★ 新增：开启事务，保证克隆不完整时自动回滚，不留脏数据！
    public Course createCourse(@RequestBody Course course) {
        // 1. 如果是普通创建（空白创建）
        if (course.getCloneFromId() == null) {
            if (course.getCourseType() == null) {
                course.setCourseType("public"); // 默认公有
            }
            // ★ 新增逻辑：如果直接创建的是私有课，必须赋予一个8位唯一的加课码！
            if ("private".equals(course.getCourseType())) {
                course.setInviteCode(generateUniqueInviteCode());
            }
            if (course.getStatus() == null) {
                course.setStatus(0); // 默认 0：草稿/未发布状态
            }
            return courseRepository.save(course);
        }

        // 2. 如果是克隆模式（从公共模板私有化）
        Optional<Course> templateOpt = courseRepository.findById(course.getCloneFromId());
        if (templateOpt.isPresent()) {
            Course template = templateOpt.get();

            // 继承基本属性（标签等）
            course.setTags(template.getTags());

            // 保留原简介
            String originalDesc = template.getDescription() != null ? template.getDescription() : "暂无简介";
            course.setDescription(originalDesc);

            // ★★★ 关键新增：继承封面图 ★★★
            course.setCoverUrl(template.getCoverUrl());  // 直接把模板的封面 URL 复制过来

            // 建立血缘溯源关系，强制设为私有
            course.setOriginalCourseId(template.getId());
            course.setCourseType("private");
            course.setStatus(0); // 克隆后的私有课默认也是 0（草稿），需要老师确认无误后发布

            // ★ 新增逻辑：克隆出来的私有课，也要赋予一个全新的8位不重复加课码！
            course.setInviteCode(generateUniqueInviteCode());

            // 先保存壳子，获取新课程的独立 ID
            Course savedCourse = courseRepository.save(course);

            // 【深拷贝 1】：克隆所有章节
            List<CourseChapter> chapters = courseChapterRepository.findByCourseIdOrderBySortOrderAsc(template.getId());
            if (chapters != null) {
                for (CourseChapter c : chapters) {
                    CourseChapter newChapter = new CourseChapter();
                    newChapter.setCourseId(savedCourse.getId());
                    newChapter.setTitle(c.getTitle());
                    // ★ 核心修复：防止 videoUrl 为空被数据库拦截！
                    newChapter.setVideoUrl(c.getVideoUrl() != null ? c.getVideoUrl() : "");
                    newChapter.setSortOrder(c.getSortOrder() != null ? c.getSortOrder() : 0);
                    courseChapterRepository.save(newChapter);
                }
            }

// 【深拷贝 2】：克隆所有实验任务 + 实验附件
            List<CourseLab> labs = courseLabRepository.findByCourseId(template.getId());
            if (labs != null) {
                for (CourseLab l : labs) {
                    CourseLab newLab = new CourseLab();
                    newLab.setCourseId(savedCourse.getId());
                    newLab.setTitle(l.getTitle());
                    newLab.setDescription(l.getDescription() != null ? l.getDescription() : "");
                    newLab.setObjective(l.getObjective() != null ? l.getObjective() : "");
                    newLab.setExample(l.getExample() != null ? l.getExample() : "");
                    newLab.setStatus(l.getStatus() != null ? l.getStatus() : 0);

                    // ★ 接住保存后的实体，才能拿到新实验的 ID 给文件挂靠
                    CourseLab savedLab = courseLabRepository.save(newLab);

                    // 【深拷贝 3】★ 新增：克隆该实验的所有附件记录
                    if (l.getFiles() != null) {
                        for (CourseLabFile f : l.getFiles()) {
                            CourseLabFile newFile = new CourseLabFile();
                            newFile.setFileName(f.getFileName());
                            newFile.setFileUrl(f.getFileUrl());
                            newFile.setFileType(f.getFileType());
                            newFile.setCourseLab(savedLab);   // 挂到新实验上
                            courseLabFileRepository.save(newFile);
                        }
                    }
                }
            }

            return savedCourse;
        }

        // 容错：如果模板ID找不到，降级为普通保存
        if (course.getStatus() == null) course.setStatus(0);
        return courseRepository.save(course);
    }

    @PutMapping("/courses/{id}")
    public Course updateCourseInfo(@PathVariable Long id, @RequestBody Course updateData) {
        Course Course = courseRepository.findById(id).orElseThrow(() -> new RuntimeException("课程不存在"));
        Course.setTitle(updateData.getTitle());
        Course.setTags(updateData.getTags());
        Course.setDescription(updateData.getDescription());
        Course.setCourseIntro(updateData.getCourseIntro());
        return courseRepository.save(Course);
    }

    // --- 加入、检查、退出课程的核心逻辑 ---
    @PostMapping("/users/{userId}/join-course/{courseId}")
    public String joinCourse(@PathVariable Long userId, @PathVariable Long courseId) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            return "已经加入过该课程";
        }
        CourseEnrollment enrollment = new CourseEnrollment(userId, courseId);
        enrollmentRepository.save(enrollment);
        return "加入成功";
    }

    @GetMapping("/users/{userId}/check-course/{courseId}")
    public boolean checkJoinStatus(@PathVariable Long userId, @PathVariable Long courseId) {
        return enrollmentRepository.existsByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    @DeleteMapping("/users/{userId}/leave-course/{courseId}")
    public String leaveCourse(@PathVariable Long userId, @PathVariable Long courseId) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            enrollmentRepository.deleteByUserIdAndCourseId(userId, courseId);
            return "退课成功";
        }
        return "退课失败：未加入该课程";
    }

    @GetMapping("/users/{userId}/learning-courses")
    public List<Course> getLearningCourses(@PathVariable Long userId) {
        List<CourseEnrollment> enrollments = enrollmentRepository.findByUserId(userId);
        List<Long> courseIds = enrollments.stream()
                .map(CourseEnrollment::getCourseId)
                .collect(Collectors.toList());
        return courseRepository.findAllById(courseIds);
    }

    @GetMapping("/course-labs/{id}")
    public CourseLab getCourseLabById(@PathVariable Long id) {
        return courseLabRepository.findById(id).orElse(null);
    }

    // ==========================================
    // ★★★ 1. 保存实验基础信息的接口 ★★★
    // ==========================================
    @PutMapping("/labs/{id}")
    public CourseLab updateLab(@PathVariable Long id, @RequestBody CourseLab updatedLab) {
        CourseLab lab = courseLabRepository.findById(id).orElseThrow(() -> new RuntimeException("实验不存在"));

        lab.setTitle(updatedLab.getTitle());
        lab.setDescription(updatedLab.getDescription());
        lab.setObjective(updatedLab.getObjective());
        lab.setExample(updatedLab.getExample());

        return courseLabRepository.save(lab);
    }

    // ==========================================
    // ★★★ 2. 核心：接收并保存拖拽文件的接口 ★★★
    // ==========================================
    @PostMapping("/labs/{id}/files")
    public CourseLabFile uploadLabFile(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam("type") String type) {
        CourseLab lab = courseLabRepository.findById(id).orElseThrow(() -> new RuntimeException("实验不存在"));

        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String newFileName = UUID.randomUUID().toString() + "_" + originalFilename;
            String filePath = uploadDir + newFileName;

            file.transferTo(new File(filePath));

            CourseLabFile labFile = new CourseLabFile();
            labFile.setFileName(originalFilename);
            labFile.setFileUrl("/uploads/" + newFileName);
            labFile.setFileType(type);
            labFile.setCourseLab(lab);

            return courseLabFileRepository.save(labFile);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("啊呀，文件保存到硬盘失败了！");
        }
    }

    // ==========================================
    // ★★★ 3. 补漏：创建新实验的接口 ★★★
    // ==========================================
    @PostMapping("/courses/{courseId}/labs")
    public CourseLab createCourseLab(@PathVariable Long courseId, @RequestBody CourseLab newLab) {
        newLab.setCourseId(courseId);
        return courseLabRepository.save(newLab);
    }

    // ==========================================
    // ★★★ 4. 强制文件下载接口 ★★★
    // ==========================================
    @GetMapping("/uploads/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                String encodedFilename = java.net.URLEncoder.encode(resource.getFilename(), "UTF-8").replaceAll("\\+", "%20");

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==========================================
    // ★★★ 5. 绕过实体关联，原生 SQL 强删文件 ★★★
    // ==========================================
    @DeleteMapping("/labs/files/{fileId}")
    public ResponseEntity<?> deleteLabFile(@PathVariable Long fileId) {
        try {
            Optional<CourseLabFile> fileOpt = courseLabFileRepository.findById(fileId);

            if (fileOpt.isPresent()) {
                CourseLabFile labFile = fileOpt.get();

                // 1. 删硬盘文件
                File physicalFile = new File(System.getProperty("user.dir") + "/uploads/" + labFile.getFileName());
                if (physicalFile.exists()) {
                    physicalFile.delete();
                }

                // 2. 拔掉网线，直接用原生 SQL 轰炸数据库
                courseLabFileRepository.forceDeleteById(fileId);

                return ResponseEntity.ok().body("彻底删除成功");
            } else {
                return ResponseEntity.badRequest().body("文件不存在");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("删除失败");
        }
    }

    // ==========================================
    // ★★★ 6. 新增：一键部署/下线状态切换 (完美支持 Integer 0/1) ★★★
    // ==========================================
    @PutMapping("/labs/{id}/status")
    public CourseLab updateLabStatus(@PathVariable Long id, @RequestParam("status") Integer status) {
        CourseLab lab = courseLabRepository.findById(id).orElseThrow(() -> new RuntimeException("实验不存在"));
        lab.setStatus(status);
        return courseLabRepository.save(lab);
    }

    // ==========================================
    // ★★★ 7. 修复：彻底删除整个实验 (纯 JPA 安全版) ★★★
    // ==========================================
    @DeleteMapping("/labs/{id}")
    @Transactional
    public ResponseEntity<?> deleteLab(@PathVariable Long id) {
        try {
            CourseLab lab = courseLabRepository.findById(id).orElseThrow(() -> new RuntimeException("实验不存在"));

            if (lab.getFiles() != null && !lab.getFiles().isEmpty()) {
                courseLabFileRepository.deleteAll(lab.getFiles());
            }

            courseLabRepository.delete(lab);
            return ResponseEntity.ok().body("实验删除成功");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("删除失败: " + e.getMessage());
        }
    }

// ==========================================
// ★★★ 生成/重新生成 8位加课码接口 ★★★
// ==========================================
    @PutMapping("/courses/{id}/regenerate-code")
    public ResponseEntity<?> regenerateInviteCode(@PathVariable Long id) {
        Optional<Course> courseOpt = courseRepository.findById(id);
        if (!courseOpt.isPresent()) {
            return ResponseEntity.badRequest().body("课程不存在");
        }
        Course course = courseOpt.get();

        // ★ 复用防撞工具方法，保证新码全库唯一
        String newCode = generateUniqueInviteCode();

        course.setInviteCode(newCode);
        courseRepository.save(course);

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("inviteCode", newCode);
        return ResponseEntity.ok(response);
    }
    // ==========================================
// ★★★ 章节：删除 ★★★
// ==========================================
    @DeleteMapping("/chapters/{id}")
    public ResponseEntity<?> deleteChapter(@PathVariable Long id) {
        if (!courseChapterRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("章节不存在");
        }
        courseChapterRepository.deleteById(id);
        return ResponseEntity.ok("删除成功");
    }

    // ==========================================
// ★★★ 章节：上移/下移 (整表重排，兼容老数据 sort_order 全是 99/0 的情况) ★★★
// ==========================================
    @PutMapping("/chapters/{id}/move")
    @Transactional
    public ResponseEntity<?> moveChapter(@PathVariable Long id, @RequestParam String direction) {
        CourseChapter target = courseChapterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("章节不存在"));

        List<CourseChapter> list = courseChapterRepository.findByCourseIdOrderBySortOrderAsc(target.getCourseId());
        // 二次稳定排序：sortOrder 相同时按 id 排，保证顺序和前端展示一致
        list.sort(java.util.Comparator.comparing(CourseChapter::getSortOrder,
                        java.util.Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(CourseChapter::getId));

        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) { idx = i; break; }
        }
        int swapIdx = "up".equals(direction) ? idx - 1 : idx + 1;
        if (idx == -1 || swapIdx < 0 || swapIdx >= list.size()) {
            return ResponseEntity.badRequest().body("已经到顶/到底了");
        }

        java.util.Collections.swap(list, idx, swapIdx);
        // ★ 关键：换完后把整个列表的 sort_order 重新刷成 1,2,3...，一次性修复所有历史脏数据
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i + 1);
        }
        courseChapterRepository.saveAll(list);
        return ResponseEntity.ok("移动成功");
    }

    // ==========================================
// ★★★ 章节：本地视频文件上传 (返回可存库的 URL) ★★★
// ==========================================
    @PostMapping("/chapters/upload-video")
    public ResponseEntity<?> uploadChapterVideo(@RequestParam("file") MultipartFile file) {
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String newFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            file.transferTo(new File(uploadDir + newFileName));

            java.util.Map<String, String> resp = new java.util.HashMap<>();
            resp.put("url", "/videos/" + newFileName);
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("视频保存失败");
        }
    }

    // ==========================================
// ★★★ 章节：视频在线播放接口 (inline，区别于强制下载的 /uploads) ★★★
// ==========================================
    @GetMapping("/videos/{fileName:.+}")
    public ResponseEntity<Resource> playVideo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(System.getProperty("user.dir") + "/uploads/").resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ★ 修改实验的作品展示模式
    @PutMapping("/courses/labs/{labId}/showcase-mode")
    public ResponseEntity<?> updateShowcaseMode(@PathVariable Long labId, @RequestBody Map<String, Object> payload) {
        java.util.Optional<CourseLab> labOpt = courseLabRepository.findById(labId);
        if (!labOpt.isPresent()) {
            return ResponseEntity.badRequest().body("实验不存在");
        }
        int mode = Integer.parseInt(String.valueOf(payload.get("mode")));
        if (mode < 1 || mode > 3) {
            return ResponseEntity.badRequest().body("非法的模式值");
        }
        CourseLab lab = labOpt.get();
        lab.setShowcaseMode(mode);
        courseLabRepository.save(lab);
        return ResponseEntity.ok("展示模式已更新");
    }

    // ★ 删除课程（连带清理章节、实验、成员、申请）
    @DeleteMapping("/courses/{id}")
    @Transactional
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        if (!courseRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("课程不存在");
        }
        courseChapterRepository.deleteByCourseId(id);
        courseLabRepository.deleteByCourseId(id);
        enrollmentRepository.deleteByCourseId(id);
        applicationRepository.deleteByCourseId(id);
        courseRepository.deleteById(id);
        return ResponseEntity.ok("课程已删除");
    }

    @PutMapping("/courses/{id}/cover")
    public ResponseEntity<String> updateCover(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return courseRepository.findById(id).map(course -> {
            course.setCoverUrl(payload.get("coverUrl"));
            courseRepository.save(course);
            return ResponseEntity.ok("封面已更新");
        }).orElse(ResponseEntity.badRequest().body("课程不存在"));
    }
}