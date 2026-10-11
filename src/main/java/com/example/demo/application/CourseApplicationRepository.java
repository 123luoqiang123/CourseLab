package com.example.demo.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {
    // 防重复：该学生对该课程是否已有指定状态的申请
    boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, Integer status);
    // 教师端：拉取某课程指定状态的申请列表
    List<CourseApplication> findByCourseIdAndStatus(Long courseId, Integer status);

    CourseApplication findFirstByUserIdAndCourseIdAndStatus(Long userId, Long courseId, Integer status);
    void deleteByCourseId(Long courseId);
}