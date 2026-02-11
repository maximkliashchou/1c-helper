package ru.chelper.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.dto.AttemptDto;
import ru.chelper.dto.SubmitResultDto;
import ru.chelper.entity.Attempt;
import ru.chelper.entity.Task;
import ru.chelper.entity.User;
import ru.chelper.repository.AttemptRepository;
import ru.chelper.repository.TaskRepository;
import ru.chelper.repository.UserRepository;
import ru.chelper.security.UserPrincipal;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttemptService {

    private static final int MIN_TESTS_PER_TASK = 4;

    private final AttemptRepository attemptRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CodeExecutionService codeExecutionService;
    private final TaskService taskService;

    public AttemptService(AttemptRepository attemptRepository,
                          TaskRepository taskRepository,
                          UserRepository userRepository,
                          CodeExecutionService codeExecutionService,
                          TaskService taskService) {
        this.attemptRepository = attemptRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.codeExecutionService = codeExecutionService;
        this.taskService = taskService;
    }

    @Transactional
    public SubmitResultDto submit(Long userId, Long taskId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        if (attemptRepository.existsByTaskIdAndUserIdAndPassedTrue(taskId, userId)) {
            throw new IllegalArgumentException("Задача уже сдана. Повторная отправка недоступна.");
        }
        int testCount = taskService.getTestCaseCount(taskId);
        if (testCount < MIN_TESTS_PER_TASK) {
            throw new IllegalArgumentException("У задачи должно быть минимум " + MIN_TESTS_PER_TASK + " теста. Сейчас: " + testCount);
        }
        CodeExecutionService.RunResult result = codeExecutionService.runTests(task, code);
        Attempt attempt = new Attempt();
        attempt.setUser(user);
        attempt.setTask(task);
        attempt.setCode(code);
        attempt.setPassed(result.isAllPassed());
        attempt.setPassedTests(result.getPassedCount());
        attempt.setTotalTests(result.getTotalCount());
        attempt.setMessage(result.getMessage());
        attempt = attemptRepository.save(attempt);
        SubmitResultDto dto = new SubmitResultDto();
        dto.setAttemptId(attempt.getId());
        dto.setPassed(attempt.getPassed());
        dto.setPassedTests(attempt.getPassedTests());
        dto.setTotalTests(attempt.getTotalTests());
        dto.setMessage(attempt.getMessage());
        dto.setCreatedAt(attempt.getCreatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AttemptDto> getMyAttempts(Long userId) {
        return attemptRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttemptDto> getMyAttemptsForTask(Long userId, Long taskId) {
        return attemptRepository.findByTaskIdAndUserIdOrderByCreatedAtDesc(taskId, userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AttemptDto getAttempt(Long attemptId, Long userId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));
        if (!attempt.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Доступ запрещён");
        }
        return toDto(attempt);
    }

    private AttemptDto toDto(Attempt a) {
        AttemptDto dto = new AttemptDto();
        dto.setId(a.getId());
        dto.setTaskId(a.getTask().getId());
        dto.setTaskTitle(a.getTask().getTitle());
        dto.setTopicId(a.getTask().getTopic().getId());
        dto.setTopicTitle(a.getTask().getTopic().getTitle());
        dto.setCode(a.getCode());
        dto.setPassed(a.getPassed());
        dto.setPassedTests(a.getPassedTests());
        dto.setTotalTests(a.getTotalTests());
        dto.setMessage(a.getMessage());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
