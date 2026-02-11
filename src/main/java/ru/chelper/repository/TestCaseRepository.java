package ru.chelper.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chelper.entity.TestCase;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByTaskIdOrderByIdAsc(Long taskId);
}
