package com.vojtechruzicka.xjsexporter.model;

import java.time.LocalDateTime;
import java.util.List;

public record Entry(String id, String title, LocalDateTime created, String html, List<String> persons, List<String> categories, List<Attachment> attachments,
                    String location) {
}
