package com.vojtechruzicka.xjsexporter;

import java.util.Map;

public record Metadata(Map<String, PersonMetadata> people, Map<String, CategoryMetadata> categories,
                       Map<String, AttachmentMetadata> attachments, Map<String, EntryMetadata> entries) {
}
