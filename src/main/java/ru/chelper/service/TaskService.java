package ru.chelper.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.dto.TaskDto;
import ru.chelper.dto.TestCaseDto;
import ru.chelper.entity.Task;
import ru.chelper.entity.TestCase;
import ru.chelper.entity.Topic;
import ru.chelper.repository.TaskRepository;
import ru.chelper.repository.TopicRepository;
import ru.chelper.repository.TestCaseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TopicRepository topicRepository;
    private final TestCaseRepository testCaseRepository;

    public TaskService(TaskRepository taskRepository,
                       TopicRepository topicRepository,
                       TestCaseRepository testCaseRepository) {
        this.taskRepository = taskRepository;
        this.topicRepository = topicRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getByTopicId(Long topicId) {
        return taskRepository.findByTopicIdOrderBySortOrderAsc(topicId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDto getById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        return toDto(task);
    }

    @Transactional
    public TaskDto create(Long topicId, TaskDto dto) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Тема не найдена"));
        Task task = new Task();
        task.setTopic(topic);
        task.setTitle(dto.getTitle());
        task.setCondition(dto.getCondition());
        task.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        task = taskRepository.save(task);
        return toDto(task);
    }

    @Transactional
    public TaskDto update(Long id, TaskDto dto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        if (dto.getTitle() != null) task.setTitle(dto.getTitle());
        if (dto.getCondition() != null) task.setCondition(dto.getCondition());
        if (dto.getSortOrder() != null) task.setSortOrder(dto.getSortOrder());
        task = taskRepository.save(task);
        return toDto(task);
    }

    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new IllegalArgumentException("Задача не найдена");
        }
        taskRepository.deleteById(id);
    }

    @Transactional
    public void addTestCase(Long taskId, String input, String expectedOutput) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));
        TestCase tc = new TestCase();
        tc.setTask(task);
        tc.setInput(input != null ? input : "");
        tc.setExpectedOutput(expectedOutput != null ? expectedOutput : "");
        testCaseRepository.save(tc);
    }

    public static List<TestCaseDto> parse(String text) {

        List<TestCaseDto> result = new ArrayList<>();

        String[] blocks = text.split("(?i)Ввод:");

        for (String block : blocks) {

            block = block.trim();
            if (block.isEmpty()) continue;

            String[] parts = block.split("(?i)Вывод:");

            if (parts.length != 2) continue;

            String input = parts[0].trim();
            String output = parts[1].trim();

            if (input.isEmpty() || output.isEmpty()) continue;

            TestCaseDto dto = new TestCaseDto();
            dto.setInput(input);
            dto.setExpectedOutput(output);

            result.add(dto);
        }

        return result;
    }

    @Transactional
    public void addTestCasesBulk(Long taskId, List<TestCaseDto> tests) {

        if (tests == null || tests.size() < 4) {
            throw new IllegalArgumentException("Минимум 4 теста");
        }

        for (TestCaseDto t : tests) {
            if (t.getInput() == null || t.getInput().isBlank()
                    || t.getExpectedOutput() == null || t.getExpectedOutput().isBlank()) {
                throw new IllegalArgumentException("Некорректный тест: пустой input или output");
            }
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Задача не найдена"));

        testCaseRepository.deleteByTaskId(taskId);

        List<TestCase> entities = tests.stream().map(t -> {
            TestCase tc = new TestCase();
            tc.setTask(task);
            tc.setInput(t.getInput().trim());
            tc.setExpectedOutput(t.getExpectedOutput().trim());
            return tc;
        }).toList();

        testCaseRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<TestCaseDto> getTestsByTaskId(Long taskId) {

        List<TestCase> tests =
                testCaseRepository
                        .findByTaskIdOrderByIdAsc(taskId);

        return tests.stream()
                .map(tc -> {

                    TestCaseDto dto = new TestCaseDto();

                    dto.setInput(tc.getInput());
                    dto.setExpectedOutput(tc.getExpectedOutput());

                    return dto;

                }).toList();
    }
    
    @Transactional(readOnly = true)
    public int getTestCaseCount(Long taskId) {
        return testCaseRepository.findByTaskIdOrderByIdAsc(taskId).size();
    }

    private TaskDto toDto(Task t) {
        TaskDto dto = new TaskDto();
        dto.setId(t.getId());
        dto.setTitle(t.getTitle());
        dto.setCondition(t.getCondition());
        dto.setTopicId(t.getTopic().getId());
        dto.setSortOrder(t.getSortOrder());
        return dto;
    }
}
