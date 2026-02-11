package ru.chelper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chelper.entity.Attempt;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Attempt> findByTaskIdAndUserIdOrderByCreatedAtDesc(Long taskId, Long userId);

    boolean existsByTaskIdAndUserIdAndPassedTrue(Long taskId, Long userId);
}
