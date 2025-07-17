package com.vojtechruzicka.xjsexporter.model;

import io.micrometer.common.util.StringUtils;

public record PersonMetadata(String id, String firstName, String lastName, String nickName) {

    public String getFullName() {
        if(StringUtils.isBlank(nickName)) {
            return firstName + " " + lastName;
        }

        return firstName + " " + lastName + " (" + nickName + ")";
    }
}
