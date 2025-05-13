package com.example.bdsqltester.dtos;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Assignment {
    public long id;
    public String name;
    public String instructions;
    public String answerKey;

    public Assignment(long id, String name, String instructions, String answerKey) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.answerKey = answerKey;
    }

    public Assignment(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.name = rs.getString("name");
        this.instructions = rs.getString("instructions");
        this.answerKey = rs.getString("answer_key");
    }

    @Override
    public String toString() {
        return name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public void setAnswerKey(String answerKey) {
        this.answerKey = answerKey;
    }
}
