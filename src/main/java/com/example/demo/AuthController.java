package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController

public class AuthController {

    @Autowired
    private TeacherRepository teacherRepository;

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Teacher teacher = teacherRepository.findByUsernameAndPassword(request.getUsername(), request.getPassword());

        if (teacher != null) {
            return ResponseEntity.ok(teacher); // 密码正确，返回老师的全部信息
        } else {
            return ResponseEntity.status(401).body("账号或密码错误"); // 密码错误，返回 401 状态码
        }
    }
}