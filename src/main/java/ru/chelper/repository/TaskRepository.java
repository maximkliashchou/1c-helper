package ru.chelper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chelper.entity.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByTopicIdOrderBySortOrderAsc(Long topicId);
}
