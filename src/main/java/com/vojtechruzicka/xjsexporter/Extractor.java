package com.vojtechruzicka.xjsexporter;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jsoup.Jsoup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@ShellComponent
public class Extractor {

    private final MetadataExtractor metadataExtractor;
    private final HtmlGenerator htmlGenerator;
    private final Terminal terminal;

    public Extractor(MetadataExtractor metadataExtractor, HtmlGenerator htmlGenerator, Terminal terminal) {
        this.metadataExtractor = metadataExtractor;
        this.htmlGenerator = htmlGenerator;
        this.terminal = terminal;
    }

    @ShellMethod(value = "Extracts something", key = "extract")
    public String extract() {

        // TODO extract as json also

        String path = "C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\";

        Metadata metadata;

        try {
            metadata = metadataExtractor.extractMetadata(path);
        } catch (IOException e) {
            return MessageFormat.format("Failed to extract metadata: {0}", e.getMessage());
        }

        List<Entry> entries = metadata.entries().values().stream().map(entryMetadata -> {
            String id = entryMetadata.id();
            String title = entryMetadata.title();
            LocalDateTime dateCreated = entryMetadata.dateCreated();
            List<Attachment> attachments = entryMetadata.attachmentIds().stream().map(attachmentId -> getAttachment(metadata.attachments().get(attachmentId))).toList();
            List<String> categories =  entryMetadata.categoryIds().stream().map(categoryId -> metadata.categories().get(categoryId).title()).toList();
            List<String> persons = entryMetadata.personIds().stream().map(personId -> metadata.people().get(personId).getFullName()).toList();

            String htmlBody = getHtmlBody(entryMetadata);
            String html = htmlGenerator.generateHtml(entryMetadata.title(), htmlBody, categories,persons, attachments);
            return new Entry(id, title, dateCreated, html, persons, categories, attachments);
        }).toList();

        return "";
    }

    private String getHtmlBody(EntryMetadata entryMetadata) {
        if(StringUtils.isBlank(entryMetadata.location())) {
            return null;
        }

        Path path = Path.of(entryMetadata.location());

        if(Files.exists(path)) {
            try {
                return Jsoup.parse(path).body().html();
            } catch (IOException e) {
                terminal.writer().println("Could not read file: " + path + ", Error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            terminal.writer().println("File not found: " + path);
            return null;
        }
    }

    private Attachment getAttachment(AttachmentMetadata attachmentMetadata) {
        return new Attachment(attachmentMetadata.absoluteSourcePath(), attachmentMetadata.name(), attachmentMetadata.relativeLocation());
    }

    public static void main(String[] args) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("help".getBytes());

        // Capture all output written to the terminal
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Create a custom terminal with the simulated streams
        Terminal terminal = TerminalBuilder.builder()
                .streams(inputStream, outputStream) // Custom input and output streams
                .system(false)                      // Do not use the system terminal
                .build();

        new Extractor(new MetadataExtractor(), new HtmlGenerator(new ExporterConfiguration().templateEngine()), terminal).extract();
    }

}
