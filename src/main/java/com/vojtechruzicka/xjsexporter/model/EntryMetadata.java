package com.vojtechruzicka.xjsexporter.model;

import java.time.LocalDateTime;
import java.util.List;

public record EntryMetadata(
        String id,
        String title,
        String location,
        LocalDateTime dateCreated,
        List<String> attachmentIds,
        List<String> categoryIds,
        List<String> personIds
) {

    public EntryMetadata {
        if(attachmentIds == null) {
            attachmentIds = List.of();
        }
        if(categoryIds == null) {
            categoryIds = List.of();
        }
        if(personIds == null) {
            personIds = List.of();
        }
    }
}
