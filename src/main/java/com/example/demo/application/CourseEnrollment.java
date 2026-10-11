package com.example.demo.application;

import jakarta.persistence.*;

@Entity
@Table(name = "user_learning_course") // 自动在数据库生成这张表
public class CourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;   // 学习者的ID (你的teacher表ID)

    private Long courseId; // 课程的ID

    // 必须要的无参构造
    public CourseEnrollment() {}

    public CourseEnrollment(Long userId, Long courseId) {
        this.userId = userId;
        this.courseId = courseId;
    }

    // Getters 和 Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}