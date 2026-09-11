package com.example.zhixuewear.model;

public class ScoreItem {
    public final String subject;
    public final double score;
    public final double fullScore;

    public ScoreItem(String subject, double score, double fullScore) {
        this.subject = subject == null ? "" : subject;
        this.score = score;
        this.fullScore = fullScore;
    }
}
