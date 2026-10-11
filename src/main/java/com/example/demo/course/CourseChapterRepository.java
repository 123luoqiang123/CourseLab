package com.example.demo.course;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, Long> {
    // 魔法：只要按规矩起名字，Java 会自动查出属于某个课程的所有章节，并按序号升序排列！
//    boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, Integer status);
    List<CourseChapter> findByCourseIdOrderBySortOrderAsc(Long courseId);
    void deleteByCourseId(Long courseId);
}