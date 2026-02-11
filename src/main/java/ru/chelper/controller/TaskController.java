package ru.chelper.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.chelper.dto.SubmitRequest;
import ru.chelper.dto.SubmitResultDto;
import ru.chelper.dto.TaskDto;
import ru.chelper.security.UserPrincipal;
import ru.chelper.service.AttemptService;
import ru.chelper.service.TaskService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService taskService;
    private final AttemptService attemptService;

    public TaskController(TaskService taskService, AttemptService attemptService) {
        this.taskService = taskService;
        this.attemptService = attemptService;
    }

    @GetMapping("/topics/{topicId}/tasks")
    public List<TaskDto> listByTopic(@PathVariable Long topicId) {
        return taskService.getByTopicId(topicId);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskDto> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(taskService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ResponseEntity<?> submit(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long taskId,
                                    @Valid @RequestBody SubmitRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Требуется авторизация"));
        }
        if (!request.getTaskId().equals(taskId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Неверный taskId"));
        }
        try {
            SubmitResultDto result = attemptService.submit(principal.getId(), taskId, request.getCode());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/attempts/my")
    public List<ru.chelper.dto.AttemptDto> myAttempts(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        return attemptService.getMyAttempts(principal.getId());
    }

    @GetMapping("/attempts/my/task/{taskId}")
    public List<ru.chelper.dto.AttemptDto> myAttemptsForTask(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable Long taskId) {
        if (principal == null) {
            return List.of();
        }
        return attemptService.getMyAttemptsForTask(principal.getId(), taskId);
    }

    @GetMapping("/attempts/{id}")
    public ResponseEntity<?> getAttempt(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(attemptService.getAttempt(id, principal.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
