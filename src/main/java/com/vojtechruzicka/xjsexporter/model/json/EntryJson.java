package com.vojtechruzicka.xjsexporter.model.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON representation of a journal entry for the intermediate format.
 */
public record EntryJson(
        String id,
        String title,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime dateCreated,
        String location,
        @JsonProperty(index = Integer.MAX_VALUE) // Ensure this is the last field in JSON for better readability
        String htmlBody,
        List<String> personIds,
        List<String> categoryIds,
        List<String> attachmentIds,
        String source,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime extractedAt
) {
    /**
     * Constructor with validation to ensure no null lists.
     */
    public EntryJson {
        if (personIds == null) {
            personIds = List.of();
        }
        if (categoryIds == null) {
            categoryIds = List.of();
        }
        if (attachmentIds == null) {
            attachmentIds = List.of();
        }
    }
}