package com.example.demo.course;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    // ★ 新增：用于查重加课码是否存在
    boolean existsByInviteCode(String inviteCode);

    // ★ 新增：用于学生输入加课码时查询课程基本信息
    Optional<Course> findByInviteCode(String inviteCode);
}