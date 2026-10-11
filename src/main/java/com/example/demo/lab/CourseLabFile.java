package com.example.demo.lab;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "course_lab_file")
public class CourseLabFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String fileUrl;
    private String fileType; // 核心暗号：OBJECTIVE, EXAMPLE, DATASET, TEMPLATE

    // 多对一：多个文件属于一个实验
    @ManyToOne
    @JoinColumn(name = "lab_id")
    @JsonBackReference // 极其关键：防止转 JSON 时无限循环报错
    private CourseLab courseLab;

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public CourseLab getCourseLab() { return courseLab; }
    public void setCourseLab(CourseLab courseLab) { this.courseLab = courseLab; }
}