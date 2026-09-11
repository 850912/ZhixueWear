package com.example.zhixuewear.model;

public class Exam {
    public final String id;
    public final String name;
    public final String createTime;

    public Exam(String id, String name, String createTime) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.createTime = createTime == null ? "" : createTime;
    }
}
