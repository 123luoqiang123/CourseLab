package com.example.demo.lab;

import jakarta.persistence.*;

@Entity
@Table(name = "lab_submission_file")
public class LabSubmissionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long submissionId; // 关联的提交记录ID
    private String fileName;   // 文件原名
    private String filePath;   // 服务器上的物理路径

    // --- 下面全是极其重要的 Getter 和 Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}