package com.vojtechruzicka.xjsexporter;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

@ShellComponent
public class Extractor {

    @ShellMethod(value = "Extracts something", key = "extract")
    public String extract() {

        try (Stream<Path> paths = Files.walk(Path.of("C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\Entries"))) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        System.out.println(path);
                        LocalDate dateFromPath = getDateFromPath(path.toFile().getAbsolutePath());
                        System.out.println(dateFromPath);
                    });
        } catch (IOException e) {
            return "Failed to extract :"+(e.getMessage());
        }

        return "extracted";
    }

    public static void main(String[] args) {
        new Extractor().extract();
    }

    private LocalDate getDateFromPath(String filePath) {
        try {
            String[] parts = filePath.split("\\\\");
            String year = parts[parts.length - 4];
            String month = parts[parts.length - 3];
            String day = parts[parts.length - 2];

            String dateString = year + "-" + month + "-" + day;

            LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);

            System.out.println("Parsed Date: " + date);
            return date;

        } catch (ArrayIndexOutOfBoundsException | DateTimeParseException e) {
            System.err.println("Failed to parse date from file path: " + e.getMessage());
            throw e;
        }

    }
}
