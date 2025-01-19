package com.vojtechruzicka.xjsexporter;

public record Metadata(java.util.Map<String, Person> people, java.util.Map<String, Category> tags,
                       java.util.Map<String, Attachment> attachments) {
}
