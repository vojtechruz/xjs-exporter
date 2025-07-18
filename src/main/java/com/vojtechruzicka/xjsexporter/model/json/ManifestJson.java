package com.vojtechruzicka.xjsexporter.model.json;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * JSON representation of the manifest file for the intermediate format.
 * Contains metadata about the extraction process.
 */
public record ManifestJson(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        LocalDateTime extractedAt,
        String sourceSystem,
        String sourcePath,
        int entryCount,
        int personCount,
        int categoryCount,
        int attachmentCount,
        String extractorVersion
) {
}