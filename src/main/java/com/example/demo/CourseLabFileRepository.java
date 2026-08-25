package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface CourseLabFileRepository extends JpaRepository<CourseLabFile, Long> {

    // ★★★ 绕过 JPA 关系绑定，直接用底层原生 SQL 强杀！ ★★★
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM course_lab_file WHERE id = ?1", nativeQuery = true)
    void forceDeleteById(Long id);

}