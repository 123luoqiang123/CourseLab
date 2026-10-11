package com.example.demo.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    // 增加这一行，Spring Data JPA 会自动帮你写好 SQL
    Teacher findByUsernameAndPassword(String username, String password);
    boolean existsByUsername(String username);
}