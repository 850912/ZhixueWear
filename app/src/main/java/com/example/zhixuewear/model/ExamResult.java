package com.example.zhixuewear.model;

import java.util.List;

public class ExamResult {
    public final Exam exam;
    public final List<ScoreItem> scores;

    public ExamResult(Exam exam, List<ScoreItem> scores) {
        this.exam = exam;
        this.scores = scores;
    }
}
