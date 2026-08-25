package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/applications")

public class CourseApplicationController {

    @Autowired
    private CourseApplicationRepository applicationRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    // ★ 新增注入：审批通过后自动写选课表用
    @Autowired
    private CourseEnrollmentRepository enrollmentRepository;

    // ==========================================
    // ★★★ 0. 新增：学生提交加课申请 ★★★
    // ==========================================
    @PostMapping
    public ResponseEntity<String> submitApplication(@RequestBody CourseApplication application) {
        if (application.getCourseId() == null || !courseRepository.existsById(application.getCourseId())) {
            return ResponseEntity.badRequest().body("目标课程不存在");
        }
        if (enrollmentRepository.existsByUserIdAndCourseId(application.getUserId(), application.getCourseId())) {
            return ResponseEntity.badRequest().body("您已加入该课程，无需重复申请");
        }
        if (applicationRepository.existsByUserIdAndCourseIdAndStatus(
                application.getUserId(), application.getCourseId(), 0)) {
            return ResponseEntity.badRequest().body("您已提交过申请，请耐心等待教师审核");
        }
        application.setId(null);   // 防止前端误传 id 变成更新
        application.setStatus(0);  // 强制初始为待处理
        applicationRepository.save(application);
        return ResponseEntity.ok("申请提交成功");
    }

    // 1. 获取某个课程的"待审批"列表
    @GetMapping("/course/{courseId}/pending")
    public ResponseEntity<List<CourseApplication>> getPendingApplications(@PathVariable Long courseId) {
        List<CourseApplication> list = applicationRepository.findByCourseIdAndStatus(courseId, 0);

        // ★ 核心连表逻辑：遍历列表，把 userId 对应的 username 查出来塞进去
        for (CourseApplication app : list) {
            if (app.getUserId() != null) {
                Optional<Teacher> teacherOpt = teacherRepository.findById(app.getUserId());
                if (teacherOpt.isPresent()) {
                    app.setUserName(teacherOpt.get().getUsername());
                } else {
                    app.setUserName("未知账号");
                }
            }
        }

        return ResponseEntity.ok(list);
    }

    // ==========================================
    // ★★★ 2. 强制重新生成并返回 8 位随机加课码 (防撞) ★★★
    // ==========================================
    @PostMapping("/course/{courseId}/generate-code")
    public ResponseEntity<String> generateInviteCode(@PathVariable Long courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) return ResponseEntity.badRequest().body("课程不存在");

        Course course = courseOpt.get();

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (courseRepository.existsByInviteCode(code));

        course.setInviteCode(code);
        courseRepository.save(course);

        return ResponseEntity.ok(course.getInviteCode());
    }

    // 3. 老师同意申请（★ 升级：通过后自动把学生写入选课表，形成闭环）
    @PutMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<String> approveApplication(@PathVariable Long id) {
        Optional<CourseApplication> appOpt = applicationRepository.findById(id);
        if (appOpt.isPresent()) {
            CourseApplication app = appOpt.get();
            app.setStatus(1); // 1 = 已同意
            applicationRepository.save(app);
            // ★ 自动加入选课表（防重复）
            if (!enrollmentRepository.existsByUserIdAndCourseId(app.getUserId(), app.getCourseId())) {
                enrollmentRepository.save(new CourseEnrollment(app.getUserId(), app.getCourseId()));
            }
            return ResponseEntity.ok("已同意");
        }
        return ResponseEntity.badRequest().body("申请不存在");
    }

    // 4. 老师拒绝申请
    @PutMapping("/{id}/reject")
    public ResponseEntity<String> rejectApplication(@PathVariable Long id) {
        Optional<CourseApplication> appOpt = applicationRepository.findById(id);
        if (appOpt.isPresent()) {
            CourseApplication app = appOpt.get();
            app.setStatus(2); // 2 = 已拒绝
            applicationRepository.save(app);
            return ResponseEntity.ok("已拒绝");
        }
        return ResponseEntity.badRequest().body("申请不存在");
    }
}