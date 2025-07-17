package com.vojtechruzicka.xjsexporter;

import com.vojtechruzicka.xjsexporter.config.ExporterConfiguration;
import com.vojtechruzicka.xjsexporter.model.*;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jsoup.Jsoup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @ShellMethod(value = "Extracts journal entries from XJS format", key = "extract")
    public String extract(
            @ShellOption(defaultValue = "C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\", 
                    help = "Source directory containing XJS journal entries") String sourcePath,
            @ShellOption(defaultValue = "C:\\projects\\xjs-exporter\\OUT\\", 
                    help = "Target directory for generated HTML files") String targetPath) {

        // TODO extract as json also

        // Ensure paths end with separator
        final String finalSourcePath = sourcePath.endsWith(File.separator) ? sourcePath : sourcePath + File.separator;
        final String finalTargetPath = targetPath.endsWith(File.separator) ? targetPath : targetPath + File.separator;

        Metadata metadata;

        try {
            metadata = metadataExtractor.extractMetadata(finalSourcePath);
        } catch (IOException e) {
            return MessageFormat.format("Failed to extract metadata: {0}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder();

        List<Entry> entries = metadata.entries().values().stream().sorted(Comparator.comparing(EntryMetadata::dateCreated).reversed()).map(entryMetadata -> {
            String id = entryMetadata.id();
            String title = entryMetadata.title();
            String location = entryMetadata.location();
            LocalDateTime dateCreated = entryMetadata.dateCreated();
            List<Attachment> attachments = entryMetadata.attachmentIds().stream().map(attachmentId -> getAttachment(metadata.attachments().get(attachmentId))).toList();
            List<String> categories =  entryMetadata.categoryIds().stream().map(categoryId -> metadata.categories().get(categoryId).title()).toList();
            List<String> persons = entryMetadata.personIds().stream().map(personId -> metadata.people().get(personId).getFullName()).toList();

            String htmlBody = getHtmlBody(entryMetadata);
            String html = htmlGenerator.generateEntryPage(entryMetadata.title(), dateCreated, htmlBody, categories, persons, attachments);

            sb.append(html);

            return new Entry(id, title, dateCreated, html, persons, categories, attachments, location);
        }).toList();

        // Generate main index page
        String mainPage = htmlGenerator.generateMainPage(metadata, entries);

        // Create target directory if it doesn't exist
        try {
            Files.createDirectories(Path.of(finalTargetPath));
        } catch (IOException e) {
            terminal.writer().println("Could not create target directory: " + finalTargetPath + ", Error: " + e);
            return "Failed to create target directory: " + e.getMessage();
        }

        // Write individual entry pages
        entries.forEach(entry -> {
            try {
                Files.write(Path.of(finalTargetPath + entry.created().toLocalDate().toString() + "_" + entry.id() + ".html"), 
                           entry.html().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                terminal.writer().println("Could not write entry file: " + entry.id() + ", Error: " + e);
            }
        });

        // Copy attachment files
        metadata.attachments().values().forEach(attachmentMetadata -> {
            try {
                Path target = Path.of(finalTargetPath + "Attachments" + File.separator + attachmentMetadata.name());
                Files.createDirectories(target.getParent());
                Files.copy(Path.of(attachmentMetadata.absoluteSourcePath()), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                terminal.writer().println("Could not copy attachment file: " + attachmentMetadata.absoluteSourcePath() + ", Error: " + e);
            }
        });

        // Write main index page
        try {
            Files.write(Path.of(finalTargetPath + "index.html"), mainPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            terminal.writer().println("Could not write main index file, Error: " + e);
        }

        // Generate year-based pages
        Map<Integer, List<Entry>> entriesByYear = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.created().getYear()));
        
        entriesByYear.forEach((year, yearEntries) -> {
            String yearPage = htmlGenerator.generateMainPage(metadata, yearEntries, "year", String.valueOf(year));
            try {
                Files.write(Path.of(finalTargetPath + year + ".html"), yearPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                terminal.writer().println("Could not write year file: " + year + ", Error: " + e);
            }
        });

        // Generate person-based pages
        List<String> allPersons = metadata.people().values().stream()
                .map(PersonMetadata::getFullName)
                .distinct()
                .toList();
        
        allPersons.forEach(person -> {
            List<Entry> personEntries = entries.stream()
                    .filter(entry -> entry.persons().contains(person))
                    .toList();
            
            if (!personEntries.isEmpty()) {
                String personPage = htmlGenerator.generateMainPage(metadata, personEntries, "person", person);
                try {
                    String fileName = "person_" + person.replace(' ', '_') + ".html";
                    Files.write(Path.of(finalTargetPath + fileName), personPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    terminal.writer().println("Could not write person file: " + person + ", Error: " + e);
                }
            }
        });

        // Generate category-based pages
        List<String> allCategories = metadata.categories().values().stream()
                .map(CategoryMetadata::title)
                .distinct()
                .toList();
        
        allCategories.forEach(category -> {
            List<Entry> categoryEntries = entries.stream()
                    .filter(entry -> entry.categories().contains(category))
                    .toList();
            
            if (!categoryEntries.isEmpty()) {
                String categoryPage = htmlGenerator.generateMainPage(metadata, categoryEntries, "category", category);
                try {
                    String fileName = "category_" + category.replace(' ', '_') + ".html";
                    Files.write(Path.of(finalTargetPath + fileName), categoryPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    terminal.writer().println("Could not write category file: " + category + ", Error: " + e);
                }
            }
        });
        
        // Generate list pages
        try {
            // Persons list page
            String personsListPage = htmlGenerator.generatePersonsListPage(metadata, entries);
            Files.write(Path.of(finalTargetPath + "persons_list.html"), personsListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Categories list page
            String categoriesListPage = htmlGenerator.generateCategoriesListPage(metadata, entries);
            Files.write(Path.of(finalTargetPath + "categories_list.html"), categoriesListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Years list page
            String yearsListPage = htmlGenerator.generateYearsListPage(entries, metadata);
            Files.write(Path.of(finalTargetPath + "years_list.html"), yearsListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            terminal.writer().println("Could not write list pages, Error: " + e);
        }

        // Write all entries to a single file (for backup/debugging)
        try {
            Files.write(Path.of(finalTargetPath + "all.txt"), sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            terminal.writer().println("Could not write all.txt file, Error: " + e);
        }

        return "Extract finished, " + entries.size() + " entries generated";
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

        // Use default paths
        new Extractor(
            new MetadataExtractor(), 
            new HtmlGenerator(new ExporterConfiguration().defaultTemplatingEngine()), 
            terminal
        ).extract(
            "C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\",
            "C:\\projects\\xjs-exporter\\OUT\\"
        );
    }
}