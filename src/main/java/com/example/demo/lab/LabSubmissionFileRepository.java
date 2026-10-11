package com.example.demo.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabSubmissionFileRepository extends JpaRepository<LabSubmissionFile, Integer> {
    // 以后用来根据 submission_id 查出该次提交的所有附件
    List<LabSubmissionFile> findBySubmissionId(Integer submissionId);
}