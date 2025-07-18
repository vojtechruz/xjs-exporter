package com.vojtechruzicka.xjsexporter.model.json;

/**
 * JSON representation of a person for the intermediate format.
 */
public record PersonJson(
        String id,
        String firstName,
        String lastName,
        String nickName,
        String fullName
) {
}