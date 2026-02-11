package ru.chelper.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.chelper.entity.Task;
import ru.chelper.entity.TestCase;
import ru.chelper.entity.Topic;
import ru.chelper.entity.User;
import ru.chelper.repository.TestCaseRepository;
import ru.chelper.repository.TaskRepository;
import ru.chelper.repository.TopicRepository;
import ru.chelper.repository.UserRepository;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public ApplicationRunner init(UserRepository userRepo, TopicRepository topicRepo,
                                  TaskRepository taskRepo, TestCaseRepository testCaseRepo,
                                  PasswordEncoder encoder) {
        return args -> {
            if (userRepo.findByUsernameIgnoreCase("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@localhost");
                admin.setPasswordHash(encoder.encode("admin"));
                admin.getRoles().add(User.Role.ADMIN);
                admin.getRoles().add(User.Role.USER);
                userRepo.save(admin);
            }
            if (topicRepo.count() == 0) {
                Topic t = new Topic();
                t.setTitle("Введение в язык 1С");
                t.setDescription("Базовые конструкции и типы данных");
                t.setContent("<p>Язык 1С:Предприятие — встроенный язык платформы для написания прикладных решений.</p>" +
                        "<p>Основные элементы:</p><ul><li>Переменные и типы данных</li><li>Условия и циклы</li><li>Процедуры и функции</li></ul>" +
                        "<pre><code>Перем А; // объявление переменной\nА = 1 + 2;</code></pre>");
                t.setSortOrder(0);
                t = topicRepo.save(t);

                // Демо-задача с 4 тестами — код запускается через OneScript, вывод сравнивается с ожидаемым
                Task task = new Task();
                task.setTopic(t);
                task.setTitle("Первая программа");
                task.setCondition(
                        "Напишите программу на языке 1С, которая выводит одну строку:\n\nПривет, мир!\n\n" +
                        "Используйте процедуру Сообщить(). Пример:\n\nСообщить(\"Привет, мир!\");\n\n" +
                        "Код выполняется в среде OneScript (oscript.io). Вывод в консоль сравнивается с ожидаемым результатом."
                );
                task.setSortOrder(0);
                task = taskRepo.save(task);

                addTestCases(testCaseRepo, task, "Привет, мир!", 4);

                // Тема 2: Условия и циклы
                Topic t2 = new Topic();
                t2.setTitle("Условия и циклы");
                t2.setDescription("Если-Тогда, циклы Для и Пока");
                t2.setContent("<p>В языке 1С для ветвления используется конструкция <b>Если ... Тогда ... Иначе ... КонецЕсли</b>.</p>" +
                        "<p>Циклы: <b>Для ... По ... Цикл ... КонецЦикла</b> и <b>Пока ... Цикл ... КонецЦикла</b>.</p>" +
                        "<pre><code>Если A > 0 Тогда\n    Сообщить(\"Положительное\");\nИначе\n    Сообщить(\"Не положительное\");\nКонецЕсли;</code></pre>");
                t2.setSortOrder(1);
                t2 = topicRepo.save(t2);

                Task task2 = new Task();
                task2.setTopic(t2);
                task2.setTitle("Вывод числа");
                task2.setCondition("Напишите программу, которая выводит число 42.\n\nИспользуйте Сообщить(42); или Сообщить(\"42\");");
                task2.setSortOrder(0);
                task2 = taskRepo.save(task2);
                addTestCases(testCaseRepo, task2, "42", 4);

                Task task2b = new Task();
                task2b.setTopic(t2);
                task2b.setTitle("Строка результата");
                task2b.setCondition("Выведите одну строку (ровно):\n\nГотово.\n\nИспользуйте Сообщить(\"Готово.\");");
                task2b.setSortOrder(1);
                task2b = taskRepo.save(task2b);
                addTestCases(testCaseRepo, task2b, "Готово.", 4);

                // Тема 3: Процедуры и функции
                Topic t3 = new Topic();
                t3.setTitle("Процедуры и функции");
                t3.setDescription("Объявление и вызов процедур, возврат значения");
                t3.setContent("<p><b>Процедура</b> — подпрограмма без возвращаемого значения. <b>Функция</b> — возвращает значение.</p>" +
                        "<pre><code>Процедура ПоказатьПривет()\n    Сообщить(\"Привет\");\nКонецПроцедуры\n\nПоказатьПривет();</code></pre>");
                t3.setSortOrder(2);
                t3 = topicRepo.save(t3);

                Task task3 = new Task();
                task3.setTopic(t3);
                task3.setTitle("Вывод приветствия");
                task3.setCondition("Напишите программу, которая выводит строку: Добрый день!\n\nСообщить(\"Добрый день!\");");
                task3.setSortOrder(0);
                task3 = taskRepo.save(task3);
                addTestCases(testCaseRepo, task3, "Добрый день!", 4);
            }
        };
    }

    private static void addTestCases(TestCaseRepository testCaseRepo, Task task, String expectedOutput, int count) {
        for (int i = 0; i < count; i++) {
            TestCase tc = new TestCase();
            tc.setTask(task);
            tc.setInput("");
            tc.setExpectedOutput(expectedOutput);
            testCaseRepo.save(tc);
        }
    }
}
