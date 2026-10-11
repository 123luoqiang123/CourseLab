package com.example.demo.course;

import com.example.demo.auth.Teacher;
import jakarta.persistence.*;

@Entity
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description; // 一句话简介

    @Column(name = "course_intro")
    private String courseIntro; // 精装长图文介绍

    private String tags; // 课程标签 (比如 "数据分析,Python")

    // ✨ 重点在这里：直接关联 Teacher 实体，抛弃旧的 teacher_id 变量！
    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;
    // ============ 新增：公私有与克隆溯源字段 ============

    @Column(name = "course_type")
    private String courseType; // "public" 或 "private"

    @Column(name = "status")
    private Integer status; // 0=草稿/下架，1=上架/发布

    @Column(name = "original_course_id")
    private Long originalCourseId; // 溯源：克隆自哪个公共模板

    @Transient // 这个注解表示该字段不存入数据库，只用来接收前端传来的克隆ID
    private Long cloneFromId;

    @Column(name = "invite_code", unique = true, length = 8)
    private String inviteCode;

    @Column(name = "cover_url")
    private String coverUrl;



    // ==========================================
    // =       下面是所有的 Getters 和 Setters     =
    // ==========================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCourseIntro() { return courseIntro; }
    public void setCourseIntro(String courseIntro) { this.courseIntro = courseIntro; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }

    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Long getOriginalCourseId() { return originalCourseId; }
    public void setOriginalCourseId(Long originalCourseId) { this.originalCourseId = originalCourseId; }

    public Long getCloneFromId() { return cloneFromId; }
    public void setCloneFromId(Long cloneFromId) { this.cloneFromId = cloneFromId; }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
}