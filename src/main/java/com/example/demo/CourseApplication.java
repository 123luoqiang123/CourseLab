package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_application")
public class CourseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Column(name = "student_number", nullable = false, length = 50)
    private String studentNumber;

    @Column(name = "status")
    private Integer status; // 0=待处理, 1=已通过, 2=已拒绝

    @Column(name = "apply_time")
    private LocalDateTime applyTime;

    @Transient // 不存数据库，仅用于给前端展示申请人的账号名
    private String userName;

    // 保存前自动兜底：状态默认0待处理，时间默认当前
    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = 0;
        if (this.applyTime == null) this.applyTime = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getApplyTime() { return applyTime; }
    public void setApplyTime(LocalDateTime applyTime) { this.applyTime = applyTime; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}