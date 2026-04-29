package ru.chelper.dto;


import java.util.List;

public class TestPreviewResponse {
    private int testsCount;
    private TestCaseDto firstTest;
    private List<TestCaseDto> tests;

    public int getTestsCount() {
        return testsCount;
    }

    public void setTestsCount(int testsCount) {
        this.testsCount = testsCount;
    }

    public TestCaseDto getFirstTest() {
        return firstTest;
    }

    public void setFirstTest(TestCaseDto firstTest) {
        this.firstTest = firstTest;
    }

    public List<TestCaseDto> getTests() {
        return tests;
    }

    public void setTests(List<TestCaseDto> tests) {
        this.tests = tests;
    }
}
