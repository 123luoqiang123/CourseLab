package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseLabRepository extends JpaRepository<CourseLab, Long> {
    // 这是一个极其强大的魔法方法，只要按规范命名，Spring Boot 会自动帮你写 SQL 语句！
    List<CourseLab> findByCourseId(Long courseId);
    void deleteByCourseId(Long courseId);
}