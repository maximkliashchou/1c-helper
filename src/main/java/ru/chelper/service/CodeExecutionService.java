package ru.chelper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.chelper.entity.Task;
import ru.chelper.entity.TestCase;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Реальная проверка кода на языке 1С через OneScript (oscript.io).
 * Скрипт запускается как процесс; вход теста подаётся в stdin, вывод сравнивается с ожидаемым.
 * Для вывода в консоль в коде нужно использовать Сообщить().
 */
@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private static final String SCRIPT_FILENAME = "Main.bsl";
    private static final int DEFAULT_TIMEOUT_SEC = 10;

    @Value("${code-runner.timeout-seconds:" + DEFAULT_TIMEOUT_SEC + "}")
    private int timeoutSeconds;

    @Value("${code-runner.oscript-command:oscript}")
    private String oscriptCommand;

    @Value("${code-runner.work-dir:}")
    private String workDirPath;

    @Value("${code-runner.use-real-runner:true}")
    private boolean useRealRunner;

    public static class TestRunResult {
        private final boolean passed;
        private final String message;

        public TestRunResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class RunResult {
        private final boolean allPassed;
        private final int passedCount;
        private final int totalCount;
        private final String message;
        private final List<TestRunResult> testResults;

        public RunResult(boolean allPassed, int passedCount, int totalCount, String message,
                         List<TestRunResult> testResults) {
            this.allPassed = allPassed;
            this.passedCount = passedCount;
            this.totalCount = totalCount;
            this.message = message;
            this.testResults = testResults != null ? testResults : new ArrayList<>();
        }

        public boolean isAllPassed() {
            return allPassed;
        }

        public int getPassedCount() {
            return passedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public String getMessage() {
            return message;
        }

        public List<TestRunResult> getTestResults() {
            return testResults;
        }
    }

    public RunResult runTests(Task task, String code) {
        if (code == null || code.isBlank()) {
            return new RunResult(false, 0, 0, "Код не может быть пустым", new ArrayList<>());
        }
        List<TestCase> cases = task.getTestCases();
        if (cases == null || cases.isEmpty()) {
            return new RunResult(false, 0, 0, "Нет тестов для задачи", new ArrayList<>());
        }

        if (!useRealRunner) {
            return runTestsStub(task, code);
        }

        Path workDir = null;
        try {
            if (workDirPath != null && !workDirPath.isBlank()) {
                Path base = Paths.get(workDirPath);
                Files.createDirectories(base);
                workDir = Files.createTempDirectory(base, "1c-run-");
            } else {
                workDir = Files.createTempDirectory("1c-run-");
            }
            Path scriptPath = workDir.resolve(SCRIPT_FILENAME);
            Files.writeString(scriptPath, code, StandardCharsets.UTF_8);

            List<TestRunResult> results = new ArrayList<>();
            int passed = 0;
            for (TestCase tc : cases) {
                String input = tc.getInput() != null ? tc.getInput() : "";
                String expected = normalizeOutput(tc.getExpectedOutput());
                ProcessResult out = runProcess(workDir, scriptPath, input);
                String actual = normalizeOutput(out.stdout);
                // OneScript может выводить Сообщить() в stderr — тогда сравниваем и с stderr
                if (actual.isEmpty() && out.stderr != null && !out.stderr.isBlank()) {
                    actual = normalizeOutput(out.stderr);
                    log.debug("Использован stderr как вывод: [{}]", actual);
                }
                log.info("Тест: ожидалось=[{}], получено=[{}], exitCode={}, stderr=[{}]", expected, actual, out.exitCode, out.stderr != null ? out.stderr.trim() : "");
                boolean outputMatch = expected != null && expected.equals(actual);
                boolean ok = outputMatch && !out.error;
                if (out.error) {
                    String errMsg = out.stderr != null && !out.stderr.isBlank() ? out.stderr.trim() : "код возврата " + out.exitCode;
                    results.add(new TestRunResult(false, "Ошибка выполнения: " + errMsg));
                } else {
                    results.add(new TestRunResult(ok, ok ? "OK" : "Ожидалось: «" + expected + "», получено: «" + actual + "»"));
                    if (ok) passed++;
                }
            }
            boolean allPassed = passed == cases.size();
            String message = allPassed
                    ? "Все тесты пройдены."
                    : String.format("Пройдено %d из %d тестов.", passed, cases.size());
            return new RunResult(allPassed, passed, cases.size(), message, results);
        } catch (IOException e) {
            return new RunResult(false, 0, cases.size(), "Ошибка запуска: " + e.getMessage(), new ArrayList<>());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunResult(false, 0, cases.size(), "Проверка прервана по таймауту.", new ArrayList<>());
        } catch (TimeoutException e) {
            return new RunResult(false, 0, cases.size(), "Превышено время выполнения (таймаут " + timeoutSeconds + " с).", new ArrayList<>());
        } finally {
            if (workDir != null) {
                try {
                    deleteRecursively(workDir);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Заглушка: проверка по вхождению ожидаемой строки в код (если OneScript недоступен). */
    private RunResult runTestsStub(Task task, String code) {
        List<TestCase> cases = task.getTestCases();
        List<TestRunResult> results = new ArrayList<>();
        int passed = 0;
        for (TestCase tc : cases) {
            String expected = normalizeOutput(tc.getExpectedOutput());
            boolean ok = expected != null && code.contains(expected);
            results.add(new TestRunResult(ok, ok ? "OK" : "Ожидаемый вывод не совпал (режим-заглушка)"));
            if (ok) passed++;
        }
        boolean allPassed = passed == cases.size();
        String message = allPassed ? "Все тесты пройдены (режим-заглушка)." : String.format("Пройдено %d из %d тестов.", passed, cases.size());
        return new RunResult(allPassed, passed, cases.size(), message, results);
    }

    private ProcessResult runProcess(Path workDir, Path scriptPath, String stdinInput) throws IOException, InterruptedException, TimeoutException {
        // Рабочая директория процесса — не /tmp, чтобы избежать noexec (Permission denied)
        ProcessBuilder pb = new ProcessBuilder(oscriptCommand, scriptPath.toAbsolutePath().toString())
                .directory(workDir.toFile())
                .redirectErrorStream(false);
        Process p = pb.start();

        try (OutputStream out = p.getOutputStream()) {
            if (stdinInput != null && !stdinInput.isEmpty()) {
                out.write(stdinInput.getBytes(StandardCharsets.UTF_8));
            }
        }

        ByteArrayOutputStream stdoutBa = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBa = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream(); InputStream err = p.getErrorStream()) {
            Future<?> fOut = runCopy(in, stdoutBa);
            Future<?> fErr = runCopy(err, stderrBa);
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            fOut.cancel(true);
            fErr.cancel(true);
            if (!finished) {
                p.destroyForcibly();
                throw new TimeoutException();
            }
        }

        String stdout = stdoutBa.toString(StandardCharsets.UTF_8);
        String stderr = stderrBa.toString(StandardCharsets.UTF_8);
        return new ProcessResult(p.exitValue(), stdout, stderr, p.exitValue() != 0);
    }

    private Future<?> runCopy(InputStream in, OutputStream out) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "code-runner-copy");
            t.setDaemon(true);
            return t;
        });
        return exec.submit(() -> {
            try {
                in.transferTo(out);
            } catch (IOException ignored) {
            }
        });
    }

    private static String normalizeOutput(String s) {
        if (s == null) return "";
        return s.trim().replace("\r\n", "\n").replace("\r", "\n");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final boolean error;

        ProcessResult(int exitCode, String stdout, String stderr, boolean error) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.error = error;
        }
    }
}
