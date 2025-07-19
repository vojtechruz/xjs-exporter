package com.vojtechruzicka.xjsexporter;

import com.github.slugify.Slugify;
import com.vojtechruzicka.xjsexporter.model.Entry;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import com.vojtechruzicka.xjsexporter.model.json.JsonIntermediateStorage;
import com.vojtechruzicka.xjsexporter.model.json.JsonIntermediateStorage.MetadataAndEntries;
import com.vojtechruzicka.xjsexporter.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command for generating HTML output from intermediate JSON files.
 */
@Slf4j
@ShellComponent
public class Generator {

    private final HtmlGenerator htmlGenerator;
    private final JsonIntermediateStorage jsonStorage;
    private final Terminal terminal;
    private final FileService fileService;

    public Generator(HtmlGenerator htmlGenerator, JsonIntermediateStorage jsonStorage, Terminal terminal, FileService fileService) {
        this.htmlGenerator = htmlGenerator;
        this.jsonStorage = jsonStorage;
        this.terminal = terminal;
        this.fileService = fileService;
    }

    @ShellMethod(value = "Generates HTML output from intermediate JSON files", key = "generate")
    public String generate(
            @ShellOption(defaultValue = "C:\\projects\\xjs-exporter\\intermediate-data\\", 
                    help = "Source directory containing intermediate JSON files") String intermediatePath,
            @ShellOption(defaultValue = "C:\\projects\\xjs-exporter\\OUT\\", 
                    help = "Target directory for generated HTML files") String targetPath) {

        // Ensure paths end with separator
        final String finalIntermediatePath = intermediatePath.endsWith(File.separator) ? intermediatePath : intermediatePath + File.separator;
        final String finalTargetPath = targetPath.endsWith(File.separator) ? targetPath : targetPath + File.separator;

        // Load data from intermediate storage
        MetadataAndEntries data;
        try {
            data = jsonStorage.loadAll(finalIntermediatePath);
        } catch (IOException e) {
            return MessageFormat.format("Failed to load data from intermediate storage: {0}", e.getMessage());
        }

        Metadata metadata = data.metadata();
        List<Entry> entries = data.entries();

        // Sort entries by date (newest first)
        entries = entries.stream()
                .sorted(Comparator.comparing(Entry::created).reversed())
                .collect(Collectors.toList());

        // Create target directory and subdirectories if they don't exist
        try {
            Files.createDirectories(Path.of(finalTargetPath));
            Files.createDirectories(Path.of(finalTargetPath + "persons"));
            Files.createDirectories(Path.of(finalTargetPath + "categories"));
            Files.createDirectories(Path.of(finalTargetPath + "entries"));
            Files.createDirectories(Path.of(finalTargetPath + "years"));
            Files.createDirectories(Path.of(finalTargetPath + "lists"));
        } catch (IOException e) {
            terminal.writer().println("Could not create target directory: " + finalTargetPath + ", Error: " + e);
            return "Failed to create target directory: " + e.getMessage();
        }

        // Create final references for use in lambdas
        final List<Entry> finalEntries = entries;
        final Metadata finalMetadata = metadata;
        
        // Generate main index page
        String mainPage = htmlGenerator.generateMainPage(finalMetadata, finalEntries);

        // Write individual entry pages
        finalEntries.forEach(entry -> {
            try {
                String html = htmlGenerator.generateEntryPage(
                        finalMetadata, 
                        entry.title(), 
                        entry.created(), 
                        entry.html(), 
                        entry.categories(), 
                        entry.persons(), 
                        entry.attachments()
                );
                
                Files.write(
                    Path.of(finalTargetPath + "entries" + File.separator + fileService.getEntryFileName(entry) + ".html"),
                    html.getBytes(), 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.WRITE, 
                    StandardOpenOption.TRUNCATE_EXISTING
                );
            } catch (IOException e) {
                terminal.writer().println("Could not write entry file: " + entry.id() + ", Error: " + e);
            }
        });

        // Copy attachment files
        metadata.attachments().values().forEach(attachmentMetadata -> {
            try {
                Path target = Path.of(finalTargetPath + "attachments" + File.separator + attachmentMetadata.name());
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
                Files.write(Path.of(finalTargetPath + "years" + File.separator + year + ".html"), yearPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                terminal.writer().println("Could not write year file: " + year + ", Error: " + e);
            }
        });

        // Generate person-based pages
        List<String> allPersons = finalEntries.stream()
                .flatMap(entry -> entry.persons().stream())
                .distinct()
                .sorted()
                .toList();
        
        allPersons.forEach(person -> {
            List<Entry> personEntries = finalEntries.stream()
                    .filter(entry -> entry.persons().contains(person))
                    .collect(Collectors.toList());
            
            if (!personEntries.isEmpty()) {
                String personPage = htmlGenerator.generateMainPage(finalMetadata, personEntries, "person", person);
                try {
                    String fileName = "person_" + person.replace(' ', '_') + ".html";
                    Files.write(Path.of(finalTargetPath + "persons" + File.separator + fileName), personPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    terminal.writer().println("Could not write person file: " + person + ", Error: " + e);
                }
            }
        });

        // Generate category-based pages
        List<String> allCategories = finalEntries.stream()
                .flatMap(entry -> entry.categories().stream())
                .distinct()
                .sorted()
                .toList();
        
        allCategories.forEach(category -> {
            List<Entry> categoryEntries = finalEntries.stream()
                    .filter(entry -> entry.categories().contains(category))
                    .collect(Collectors.toList());
            
            if (!categoryEntries.isEmpty()) {
                String categoryPage = htmlGenerator.generateMainPage(finalMetadata, categoryEntries, "category", category);
                try {
                    String fileName = "category_" + category.replace(' ', '_') + ".html";
                    Files.write(Path.of(finalTargetPath + "categories" + File.separator + fileName), categoryPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    terminal.writer().println("Could not write category file: " + category + ", Error: " + e);
                }
            }
        });
        
        // Generate list pages
        try {
            String listsDir = "lists" + File.separator;
            // Persons list page
            String personsListPage = htmlGenerator.generatePersonsListPage(finalMetadata, finalEntries);
            Files.write(Path.of(finalTargetPath + listsDir + "persons_list.html"), personsListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Categories list page
            String categoriesListPage = htmlGenerator.generateCategoriesListPage(finalMetadata, finalEntries);
            Files.write(Path.of(finalTargetPath + listsDir + "categories_list.html"), categoriesListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // Years list page
            String yearsListPage = htmlGenerator.generateYearsListPage(finalEntries, finalMetadata);
            Files.write(Path.of(finalTargetPath + listsDir + "years_list.html"), yearsListPage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            terminal.writer().println("Could not write list pages, Error: " + e);
        }

        return "Generation finished, " + entries.size() + " entries generated to " + finalTargetPath;
    }
}