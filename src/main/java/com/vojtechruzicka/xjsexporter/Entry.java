package com.vojtechruzicka.xjsexporter;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Entry {
    private String filePath;
    private LocalDate date;
    private String content;
    private String title;
}
