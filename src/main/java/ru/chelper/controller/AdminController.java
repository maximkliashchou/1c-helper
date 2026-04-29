package ru.chelper.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.chelper.dto.TestCaseDto;
import ru.chelper.dto.TestPreviewResponse;
import ru.chelper.dto.TopicDto;
import ru.chelper.dto.TaskDto;
import ru.chelper.service.TaskService;
import ru.chelper.service.TopicService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TopicService topicService;
    private final TaskService taskService;

    public AdminController(TopicService topicService, TaskService taskService) {
        this.topicService = topicService;
        this.taskService = taskService;
    }

    // Topics
    @PostMapping("/topics")
    public ResponseEntity<?> createTopic(@Valid @RequestBody TopicDto dto) {
        try {
            return ResponseEntity.ok(topicService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/topics/{id}")
    public ResponseEntity<?> updateTopic(@PathVariable Long id, @RequestBody TopicDto dto) {
        try {
            return ResponseEntity.ok(topicService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<?> deleteTopic(@PathVariable Long id) {
        try {
            topicService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Tasks
    @PostMapping("/topics/{topicId}/tasks")
    public ResponseEntity<?> createTask(@PathVariable Long topicId, @Valid @RequestBody TaskDto dto) {
        try {
            return ResponseEntity.ok(taskService.create(topicId, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestBody TaskDto dto) {
        try {
            return ResponseEntity.ok(taskService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        try {
            taskService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/{taskId}/tests")
    public ResponseEntity<?> getTests(
            @PathVariable Long taskId
    ) {

        try {

            return ResponseEntity.ok(
                    taskService.getTestsByTaskId(taskId)
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks/{taskId}/tests")
    public ResponseEntity<?> addTest(@PathVariable Long taskId,
                                     @RequestBody Map<String, String> body) {
        String input = body.get("input");
        String expectedOutput = body.get("expectedOutput");
        if (expectedOutput == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "expectedOutput обязателен"));
        }
        try {
            taskService.addTestCase(taskId, input != null ? input : "", expectedOutput);
            return ResponseEntity.ok(Map.of("message", "Тест добавлен"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/tasks/{taskId}/tests/bulk")
    public ResponseEntity<?> addTestsBulk(
            @PathVariable Long taskId,
            @RequestBody List<TestCaseDto> tests
    ) {
        try {
            taskService.addTestCasesBulk(taskId, tests);
            return ResponseEntity.ok(Map.of("message", "Тесты добавлены"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks/tests/upload")
    public ResponseEntity<?> uploadTests(@RequestParam("file") MultipartFile file) {
        try {
            String text = new String(file.getBytes());

            List<TestCaseDto> tests = TaskService.parse(text);

            if (tests.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Нет валидных тестов"));
            }

            TestCaseDto first = tests.get(0);

            TestPreviewResponse res = new TestPreviewResponse();
            res.setTestsCount(tests.size());
            res.setFirstTest(first);
            res.setTests(tests);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
