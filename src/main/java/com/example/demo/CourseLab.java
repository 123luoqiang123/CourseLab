package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(name = "course_lab")
public class CourseLab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private String title;
    private String description;
    private String labUrl;

    // ★★★ 新增：实验状态（0=下线，1=已部署），默认是0（下线状态） ★★★
    @Column(name = "status", columnDefinition = "INT DEFAULT 0")
    private Integer status = 0;

    @OneToMany(mappedBy = "courseLab", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private java.util.List<CourseLabFile> files;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(columnDefinition = "TEXT")
    private String example;
    private String packages;

    @Column(name = "showcase_mode")
    private Integer showcaseMode;

    // --- Getter 和 Setter 方法 ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLabUrl() { return labUrl; }
    public void setLabUrl(String labUrl) { this.labUrl = labUrl; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public java.util.List<CourseLabFile> getFiles() { return files; }
    public void setFiles(java.util.List<CourseLabFile> files) { this.files = files; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }

    public String getPackages() { return packages; }
    public void setPackages(String packages) { this.packages = packages; }

    public Integer getShowcaseMode() { return showcaseMode; }
    public void setShowcaseMode(Integer showcaseMode) { this.showcaseMode = showcaseMode; }


}