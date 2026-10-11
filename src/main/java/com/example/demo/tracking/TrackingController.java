package com.example.demo.tracking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class TrackingController {

    @PersistenceContext
    private EntityManager em;

    // 已部署实验：course_lab.status = '1'
    private long countDeployedLabs(Long courseId) {
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM course_lab WHERE course_id = :cid AND status = '1'")
                .setParameter("cid", courseId).getSingleResult()).longValue();
    }

    // 名单来源：course_application 中该课程已通过的申请（'1'，兼容早期中文'已通过'）
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryStudents(Long courseId) {
        long totalLabs = countDeployedLabs(courseId);
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT ca.user_id, ca.real_name, ca.student_number, " +
                                " (SELECT COUNT(DISTINCT s.lab_id) FROM lab_submission s " +
                                "   JOIN course_lab l ON l.id = s.lab_id " +
                                "  WHERE s.user_id = ca.user_id AND l.course_id = :cid AND l.status = '1' " +
                                "    AND s.score IS NOT NULL AND s.score >= 60), " +
                                " (SELECT MAX(s2.submit_time) FROM lab_submission s2 " +
                                "   JOIN course_lab l2 ON l2.id = s2.lab_id " +
                                "  WHERE s2.user_id = ca.user_id AND l2.course_id = :cid) " +
                                "FROM course_application ca " +
                                "WHERE ca.course_id = :cid AND ca.status IN ('1', '已通过') " +
                                "ORDER BY ca.student_number")
                .setParameter("cid", courseId).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", r[0]);
            m.put("name", r[1]);          // real_name → 前端的 name
            m.put("studentNo", r[2]);     // student_number → 前端的 studentNo
            m.put("completedLabs", r[3] == null ? 0L : ((Number) r[3]).longValue());
            m.put("totalLabs", totalLabs);
            m.put("lastActiveTime", r[4]);
            result.add(m);
        }
        return result;
    }

    // 1. 学生列表
    @GetMapping("/courses/{courseId}/tracking/students")
    public List<Map<String, Object>> getTrackingStudents(@PathVariable Long courseId) {
        return queryStudents(courseId);
    }

    // 2. 单个学生档案：每个已部署实验的得分情况
    @SuppressWarnings("unchecked")
    @GetMapping("/courses/{courseId}/tracking/students/{userId}/profile")
    public List<Map<String, Object>> getStudentProfile(@PathVariable Long courseId, @PathVariable Long userId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT l.title, MAX(s.score), MAX(s.submit_time) " +
                                "FROM course_lab l " +
                                "LEFT JOIN lab_submission s ON s.lab_id = l.id AND s.user_id = :uid " +
                                "WHERE l.course_id = :cid AND l.status = '1' " +
                                "GROUP BY l.id, l.title ORDER BY l.id")
                .setParameter("cid", courseId).setParameter("uid", userId).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("labTitle", r[0]);
            m.put("score", r[1]);
            m.put("submitTime", r[2]);
            result.add(m);
        }
        return result;
    }

    // 3. 图表统计
    @SuppressWarnings("unchecked")
    @GetMapping("/courses/{courseId}/tracking/stats")
    public Map<String, Object> getTrackingStats(@PathVariable Long courseId) {
        List<Map<String, Object>> students = queryStudents(courseId);
        long totalLabs = countDeployedLabs(courseId);

        int totalStudents = students.size();
        double rateSum = 0;
        int active7d = 0;
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        for (Map<String, Object> s : students) {
            long done = (long) s.get("completedLabs");
            if (totalLabs > 0) rateSum += done * 100.0 / totalLabs;
            Object last = s.get("lastActiveTime");
            if (last instanceof java.util.Date && ((java.util.Date) last).getTime() >= sevenDaysAgo) {
                active7d++;
            }
        }
        int avgRate = totalStudents == 0 ? 0 : (int) Math.round(rateSum / totalStudents);

        // 各已部署实验的完成人数（仅统计本课程已通过审核的学生）
        List<Object[]> labRows = em.createNativeQuery(
                        "SELECT l.title, COUNT(DISTINCT s.user_id) " +
                                "FROM course_lab l " +
                                "LEFT JOIN lab_submission s ON s.lab_id = l.id " +
                                "  AND s.score IS NOT NULL AND s.score >= 60 " +
                                "  AND s.user_id IN (SELECT ca.user_id FROM course_application ca " +
                                "                     WHERE ca.course_id = :cid AND ca.status IN ('1', '已通过')) " +
                                "WHERE l.course_id = :cid AND l.status = '1' " +
                                "GROUP BY l.id, l.title ORDER BY l.id")
                .setParameter("cid", courseId).getResultList();
        List<Map<String, Object>> labStats = new ArrayList<>();
        for (Object[] r : labRows) {
            Map<String, Object> m = new HashMap<>();
            m.put("labTitle", r[0]);
            m.put("completedCount", ((Number) r[1]).longValue());
            labStats.add(m);
        }

        // 近30日每天活跃人数（当天有提交行为的去重人数）
        List<Object[]> trendRows = em.createNativeQuery(
                        "SELECT DATE(s.submit_time), COUNT(DISTINCT s.user_id) " +
                                "FROM lab_submission s JOIN course_lab l ON l.id = s.lab_id " +
                                "WHERE l.course_id = :cid AND s.submit_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                                "GROUP BY DATE(s.submit_time)")
                .setParameter("cid", courseId).getResultList();
        Map<String, Long> byDay = new HashMap<>();
        for (Object[] r : trendRows) {
            byDay.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter keyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter showFmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> m = new HashMap<>();
            m.put("date", d.format(showFmt));
            m.put("count", byDay.getOrDefault(d.format(keyFmt), 0L));
            trend.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("avgCompletionRate", avgRate);
        result.put("activeCount7d", active7d);
        result.put("labStats", labStats);
        result.put("activityTrend", trend);
        return result;
    }

    // 4. 逐出班级：删选课关系 + 删学籍(申请)记录
    @DeleteMapping("/courses/{courseId}/students/{userId}")
    @Transactional
    public ResponseEntity<String> removeStudent(@PathVariable Long courseId, @PathVariable Long userId) {
        em.createNativeQuery("DELETE FROM user_learning_course WHERE course_id = :cid AND user_id = :uid")
                .setParameter("cid", courseId).setParameter("uid", userId).executeUpdate();
        em.createNativeQuery("DELETE FROM course_application WHERE course_id = :cid AND user_id = :uid")
                .setParameter("cid", courseId).setParameter("uid", userId).executeUpdate();
        return ResponseEntity.ok("已将该学生移出课程");
    }
}