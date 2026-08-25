package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiGradingService {

    @Value("${ai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * 返回 Map: { score: Integer或null, feedback: String, detail: Map或null }
     * score 为 null 表示 AI 不可用，转人工批改
     */
    public Map<String, Object> grade(String code, String language, String objective,
                                     String dependencies, MultipartFile[] images) {
        try {
            // ---------- 组装多模态 content ----------
            List<Object> content = new ArrayList<>();

            // 1. 截图（最多取3张，防止请求体过大）
            int imgCount = 0;
            if (images != null) {
                for (MultipartFile f : images) {
                    String type = f.getContentType();
                    if (type != null && type.startsWith("image/") && imgCount < 10) {
                        String b64 = Base64.getEncoder().encodeToString(f.getBytes());
                        Map<String, Object> img = new HashMap<>();
                        img.put("type", "image_url");
                        Map<String, Object> url = new HashMap<>();
                        url.put("url", "data:" + type + ";base64," + b64);
                        img.put("image_url", url);
                        content.add(img);
                        imgCount++;
                    }
                }
            }

            // 2. 文字部分（Tufte 评分细则）
            String hasImageNote = imgCount > 0
                    ? "学生提交了 " + imgCount + " 张作品截图（见上方图片），请结合截图的实际渲染效果评判配色、图表垃圾、比例失真等视觉项。"
                    : "学生未提交截图，视觉相关项请依据代码中的绘图参数推断，并在评语中注明\"基于代码推断\"。";

            String prompt = """
你是数据可视化课程的自动判卷系统，依据 Edward Tufte 的可视化设计原则批改，必须严格按细则打分，禁止随意给分：

【评分细则（总分100）】
1. 数据墨水比 Data-Ink Ratio（25分）：非数据性墨水（背景色块、冗余网格线、3D效果、重复边框）是否被最小化；每处冗余装饰扣3-5分。
2. 图表垃圾 Chartjunk（20分）：是否存在无信息量的渐变、阴影、纹理填充、装饰性图标；有则按数量扣分。
3. 图形完整性 Graphical Integrity（25分）：坐标轴是否从合理基线开始（柱状图必须从0）；比例是否失真；是否有必要的标题、坐标轴标签、单位、图例；数据是否被误导性截断。
4. 数据密度与表达效率（20分）：单位面积承载的信息量是否充分；图表类型是否契合数据特征；能否用直接标注替代图例、用小多组图替代多个孤立图表。
5. 代码可运行性与质量（10分）：结构与命名是否清晰，逻辑是否可运行。

【实验目标】%s
【语言】%s
【学生使用的依赖】%s
【截图情况】%s
【学生代码】%s                

必须仅返回JSON，不要任何多余文字，格式：
{"score": 85, "detail": {"dataInk": 20, "chartjunk": 16, "integrity": 22, "density": 17, "quality": 8}, "feedback": "分维度说明各项得分与扣分原因，并给出具体可执行的改进建议"}
""".formatted(
                    objective == null ? "未提供" : objective,
                    language == null ? "未知" : language,
                    (dependencies == null || dependencies.isBlank()) ? "无" : dependencies,
                    hasImageNote,
                    code == null ? "" : code
            );

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            content.add(textPart);

            // ---------- 组装请求体 ----------
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", content);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "qwen-vl-max");
            body.put("messages", List.of(message));
            body.put("temperature", 0.1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("AI接口异常 " + resp.statusCode() + ": " + resp.body());
                return fallback();
            }

            // ---------- 解析返回 ----------
            Map<?, ?> root = mapper.readValue(resp.body(), Map.class);
            List<?> choices = (List<?>) root.get("choices");
            Map<?, ?> msg = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String text = String.valueOf(msg.get("content"))
                    .replace("```json", "").replace("```", "").trim();

            Map<String, Object> result = mapper.readValue(text, Map.class);
            return result;

        } catch (Exception e) {
            System.err.println("AI判卷失败，转人工: " + e.getMessage());
            return fallback();
        }
    }

    private Map<String, Object> fallback() {
        Map<String, Object> m = new HashMap<>();
        m.put("score", null);
        m.put("feedback", "【系统提示】AI 服务暂时不可用，已自动记录作品，请等待老师人工批改。");
        m.put("detail", null);
        return m;
    }
}