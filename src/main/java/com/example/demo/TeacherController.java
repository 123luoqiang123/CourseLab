package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    @Autowired
    private TeacherRepository teacherRepository;

    // 获取个人资料（不返回密码）
    @GetMapping("/{id}")
    public ResponseEntity<?> getTeacherProfile(@PathVariable Long id) {
        Teacher t = teacherRepository.findById(id).orElse(null);
        if (t == null) return ResponseEntity.badRequest().body("用户不存在");
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("username", t.getUsername());
        map.put("name", t.getName());
        map.put("title", t.getTitle());
        map.put("avatar", t.getAvatar());
        map.put("bio", t.getBio());
        return ResponseEntity.ok(map);
    }

    // 更新个人资料（只更新传了的字段）
    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Teacher t = teacherRepository.findById(id).orElse(null);
        if (t == null) return ResponseEntity.badRequest().body("用户不存在");
        if (payload.containsKey("name")) t.setName(payload.get("name"));
        if (payload.containsKey("title")) t.setTitle(payload.get("title"));
        if (payload.containsKey("avatar")) t.setAvatar(payload.get("avatar"));
        if (payload.containsKey("bio")) t.setBio(payload.get("bio"));
        teacherRepository.save(t);
        return ResponseEntity.ok("保存成功");
    }
    // 注册新账号
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String name = payload.get("name");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("账号和密码不能为空");
        }
        if (teacherRepository.existsByUsername(username.trim())) {
            return ResponseEntity.badRequest().body("该账号已被注册");
        }

        Teacher t = new Teacher();
        t.setUsername(username.trim());
        t.setPassword(password);
        t.setName((name == null || name.trim().isEmpty()) ? username.trim() : name.trim());
        t.setTitle("新晋教师");
        teacherRepository.save(t);

        return ResponseEntity.ok("注册成功");
    }
}