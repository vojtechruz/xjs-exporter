package com.vojtechruzicka.xjsexporter;

public record PersonMetadata(String id, String firstName, String lastName, String nickName) {

    public String getFullName() {
        return firstName + " " + lastName + " (" + nickName + ")";
    }
}
