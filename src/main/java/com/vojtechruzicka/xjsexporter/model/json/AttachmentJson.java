package com.vojtechruzicka.xjsexporter.model.json;

/**
 * JSON representation of an attachment for the intermediate format.
 */
public record AttachmentJson(
        String id,
        String absoluteSourcePath,
        String name,
        String relativeLocation
) {
}