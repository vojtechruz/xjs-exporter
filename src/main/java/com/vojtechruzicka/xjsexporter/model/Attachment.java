package com.vojtechruzicka.xjsexporter.model;

public record Attachment(String absoluteSourcePath,
                         String name,
                         String relativeLocation,
                         String extension,
                         String mimeType,
                         Integer size,
                         String formattedSize) {
}
