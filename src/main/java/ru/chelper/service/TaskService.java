package ru.chelper.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.dto.TaskDto;
import ru.chelper.entity.Task;
import ru.chelper.entity.TestCase;
import ru.chelper.entity.Topic;
import ru.chelper.repository.TaskRepository;
import ru.chelper.repository.TopicRepository;
import ru.chelper.repository.TestCaseRepository;

import java.util.List;
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
