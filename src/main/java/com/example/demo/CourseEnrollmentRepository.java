package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    // 检查是否已经加入过，防止重复加入
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    void deleteByUserIdAndCourseId(Long userId, Long courseId);
    // 查询某个用户学了哪些课
    List<CourseEnrollment> findByUserId(Long userId);
    void deleteByCourseId(Long courseId);
}

    // ⭐ 加上这一行，允许根据用户ID和课程ID删除记录

