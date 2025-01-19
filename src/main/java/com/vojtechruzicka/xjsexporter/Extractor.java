package com.vojtechruzicka.xjsexporter;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@ShellComponent
public class Extractor {

    @ShellMethod(value = "Extracts something", key = "extract")
    public String extract() {

        // TODO extract as json also

        List<Entry> entries = new ArrayList<>();
        StringBuilder allContent = new StringBuilder();

        try (Stream<Path> paths = Files.walk(Path.of("C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\Entries"))) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Entry entry = new Entry();
                        entries.add(entry);

                        entry.setFilePath(path.toAbsolutePath().toString());

                        try {
                            String content = Files.readString(path);
                            entry.setFullContentHtml(content);

                            LocalDate dateFromPath = getDateFromPath(entry.getFilePath());
                            entry.setDate(dateFromPath);

                            Document doc = Jsoup.parse(content);

                            Element head = doc.selectFirst("head");
                            if(head != null) {
                                Elements title = head.getElementsByTag("title");
                                entry.setTitle(title.text());
                            } else {
                                entry.setTitle("[No title]");
                                // TODO handle no title found
                            }

                            String text = doc.body().text();
                            allContent.append(text).append("\n");

                            if(StringUtils.isBlank(text)) {
                                log.warn("Text is blank for file: {}", path);
                            }

                            String htmlBody = doc.body().html();
                            entry.setBodyHtml(htmlBody);

                        } catch (IOException e) {
                            log.error("Failed to read file: {}, Error: {}", path, e.getMessage(), e);
                        }

                    });
        } catch (IOException e) {
            log.error("Extract failed:{}", e.getMessage());
            return MessageFormat.format("Failed to extract : {0}", e.getMessage());
        }

        log.info("Extracted {} entries", entries.size());
        log.info(allContent.toString());
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

            return LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);

        } catch (ArrayIndexOutOfBoundsException | DateTimeParseException e) {
            log.error("Failed to parse date from file path: {}", e.getMessage(), e);
            throw e;
        }

    }
}
