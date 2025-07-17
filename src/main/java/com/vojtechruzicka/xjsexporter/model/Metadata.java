package com.vojtechruzicka.xjsexporter.model;

import com.vojtechruzicka.xjsexporter.AttachmentMetadata;
import com.vojtechruzicka.xjsexporter.CategoryMetadata;

import java.util.Map;

public record Metadata(Map<String, PersonMetadata> people, Map<String, CategoryMetadata> categories,
                       Map<String, AttachmentMetadata> attachments, Map<String, EntryMetadata> entries) {
}
