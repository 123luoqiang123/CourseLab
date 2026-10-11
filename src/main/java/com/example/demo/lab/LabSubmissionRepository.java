package com.example.demo.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabSubmissionRepository extends JpaRepository<LabSubmission, Integer> {
    // 魔法：自动根据用户ID查询，并且按照提交时间倒序排列（最新的在最上面）
    List<LabSubmission> findByUserIdOrderBySubmitTimeDesc(Long userId);
}