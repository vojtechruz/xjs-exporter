package com.vojtechruzicka.xjsexporter;

import com.vojtechruzicka.xjsexporter.config.ExporterConfiguration;
import com.vojtechruzicka.xjsexporter.model.Attachment;
import com.vojtechruzicka.xjsexporter.model.Entry;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import com.vojtechruzicka.xjsexporter.model.PersonMetadata;
import com.vojtechruzicka.xjsexporter.service.FileService;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HtmlGenerator {

    public static final String BASE_PATH_SUBDIRECTORY = "../";
    private final TemplateEngine templateEngine;
    private final Collator czechCollator = Collator.getInstance(Locale.of("cs", "CZ"));
    private final FileService fileService;

    public HtmlGenerator(TemplateEngine templateEngine, FileService fileService) {
        this.templateEngine = templateEngine;
        czechCollator.setStrength(Collator.PRIMARY);
        this.fileService = fileService;
    }

    public static void main(String[] args) {

        TemplateEngine templateEngine = new ExporterConfiguration().defaultTemplatingEngine();
        FileService fileService = new FileService();
        fileService.init();
        HtmlGenerator generator = new HtmlGenerator(templateEngine, fileService);

        String html = generator.generateEntryPage(null, "My Title", LocalDateTime.now(),"<h1>Hello world</h1>", List.of("CAT1", "CAT2"), List.of("PERSON1", "PERSON2", "PERSON3"), List.of(new Attachment("C:\\temp\\file.txt","ATACHMENT1","temp\\file.txt", "TXT", "application/text", 1, "1 MB")));
        System.out.println(html);
    }

    private String getCssContent() {
        try (var resource = getClass().getResourceAsStream("/static/css/styles.css")) {
            if (resource != null) {
                return new String(resource.readAllBytes());
            } else {
                return "/* CSS file not found */";
            }
        } catch (IOException e) {
            return "/* CSS file not found: " + e.getMessage() + " */";
        }
    }
    
    private String getJavaScriptContent() {
        try (var resource = getClass().getResourceAsStream("/static/js/navigation.js")) {
            if (resource != null) {
                return new String(resource.readAllBytes());
            } else {
                return "/* JavaScript file not found */";
            }
        } catch (IOException e) {
            return "/* JavaScript file not found: " + e.getMessage() + " */";
        }
    }

    /**
     * Sets up common context variables used across multiple templates
     */
    private void setupCommonContext(Context context, Metadata metadata, List<Entry> allEntries, 
                                   String pageType, String currentItem, String pageTitle) {
        // Get all available categories, persons, and years for navigation
        List<String> allCategories = metadata.categories().values().stream()
                .map(CategoryMetadata::title)
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
                
        List<String> allPersons = metadata.people().values().stream()
                .map(PersonMetadata::getFullName)
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
                
        List<String> allYears = allEntries.stream()
                .map(e -> String.valueOf(e.created().getYear()))
                .distinct()
                .sorted()
                .toList().reversed();
        
        // Calculate counts for each item
        Map<String, Integer> counts = new HashMap<>();
        
        // Count entries for each person
        for (String person : allPersons) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.persons().contains(person))
                    .count();
            counts.put(person, count);
        }
        
        // Count entries for each category
        for (String category : allCategories) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.categories().contains(category))
                    .count();
            counts.put(category, count);
        }
        
        // Count entries for each year
        for (String year : allYears) {
            int count = (int) allEntries.stream()
                    .filter(e -> String.valueOf(e.created().getYear()).equals(year))
                    .count();
            counts.put(year, count);
        }
        
        // Add CSS content
        context.setVariable("cssContent", getCssContent());
        
        // Add JavaScript content
        context.setVariable("jsContent", getJavaScriptContent());
        
        // Add navigation variables
        context.setVariable("persons", allPersons);
        context.setVariable("categories", allCategories);
        context.setVariable("years", allYears);
        context.setVariable("pageType", pageType);
        context.setVariable("currentItem", currentItem);
        context.setVariable("pageTitle", pageTitle);
        context.setVariable("counts", counts);
        
        // Set filtered lists to all lists by default (for list pages)
        context.setVariable("filteredPersons", allPersons);
        context.setVariable("filteredCategories", allCategories);
        context.setVariable("filteredYears", allYears);
    }

    public String generateEntryPage(Metadata metadata, String title, LocalDateTime created, String htmlBody, List<String> categories, List<String> persons, List<Attachment> attachments) {
        // Create a single entry for navigation
        Entry currentEntry = new Entry(
            "current", // id
            title,
            created,
            "", // html
            persons,
            categories,
            attachments,
            null // location
        );
        
        // Create a list with just this entry for navigation
        List<Entry> singleEntryList = List.of(currentEntry);
        
        Context context = new Context();
        
        // Setup common context variables for navigation
        setupCommonContext(context, metadata, singleEntryList, "entry", title, title);
        
        // Set specific variables for the entry page
        context.setVariable("title", title);
        context.setVariable("body", htmlBody);
        context.setVariable("categories", categories);
        context.setVariable("persons", persons);
        context.setVariable("attachments", attachments);
        context.setVariable("dateCreated", created.toLocalDate());
        context.setVariable("basePath", BASE_PATH_SUBDIRECTORY);

        return templateEngine.process("entry", context);
    }

    public String generateMainPage(Metadata metadata, List<Entry> entries) {
        return generateMainPage(metadata, entries, "main", null);
    }

    public String generateMainPage(Metadata metadata, List<Entry> entries, String pageType, String currentItem) {
        // Create filtered lists based on the current entries
        List<String> filteredCategories = entries.stream()
                .flatMap(e -> e.categories().stream())
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
        
        List<String> filteredPersons = entries.stream()
                .flatMap(e -> e.persons().stream())
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
        
        List<String> filteredYears = entries.stream()
                .map(e -> String.valueOf(e.created().getYear()))
                .distinct()
                .sorted()
                .toList().reversed();

        // Set up the context
        Context context = new Context();
        
        // Set page title based on page type
        String pageTitle = "Journal Entries";
        if (pageType.equals("person") && currentItem != null) {
            pageTitle = "Entries for Person: " + currentItem;
        } else if (pageType.equals("category") && currentItem != null) {
            pageTitle = "Entries for Category: " + currentItem;
        } else if (pageType.equals("year") && currentItem != null) {
            pageTitle = "Entries for Year: " + currentItem;
        }
        
        // Setup common context variables
        setupCommonContext(context, metadata, entries, pageType, currentItem, pageTitle);

        List<Entry> entriesWithFileName = entries.stream().map(e -> new Entry(e, fileService.getEntryFileName(e))).toList();

        // Add journal entries
        context.setVariable("journalEntries", entriesWithFileName);
        
        // Override filtered lists with actual filtered data
        context.setVariable("filteredPersons", filteredPersons);
        context.setVariable("filteredCategories", filteredCategories);
        context.setVariable("filteredYears", filteredYears);

        if(currentItem != null) {
            context.setVariable("basePath", BASE_PATH_SUBDIRECTORY);
        } else {
            context.setVariable("basePath", "");
        }

        return templateEngine.process("journal_entries_display", context);
    }
    
    /**
     * Generic method to generate any type of list page (persons, categories, years)
     * @param listType The type of list to generate ("persons", "categories", or "years")
     * @param metadata The metadata object
     * @param allEntries The list of all entries
     * @return The generated HTML
     */
    public String generateListPage(String listType, Metadata metadata, List<Entry> allEntries) {
        Context context = new Context();
        
        // Variables to be set based on list type
        List<String> items;
        Map<String, Integer> counts = new HashMap<>();
        String pageType = listType + "_list";
        String pageTitle = capitalizeFirstLetter(listType) + " List";
        String listTitle = "All " + capitalizeFirstLetter(listType);
        String itemType;
        if (listType.endsWith("ies")) {
            itemType = listType.substring(0, listType.length() - 3) + "y";
        } else if (listType.endsWith("s")) {
            itemType = listType.substring(0, listType.length() - 1);
        } else {
            itemType = listType;
        }

        // Get items based on list type
        items = switch (listType) {
            case "persons" -> metadata.people().values().stream()
                    .map(PersonMetadata::getFullName)
                    .distinct()
                    .sorted(czechCollator::compare)
                    .toList();
            case "categories" -> metadata.categories().values().stream()
                    .map(CategoryMetadata::title)
                    .distinct()
                    .sorted(czechCollator::compare)
                    .toList();
            case "years" -> allEntries.stream()
                    .map(e -> String.valueOf(e.created().getYear()))
                    .distinct()
                    .sorted()
                    .toList();
            default -> throw new IllegalArgumentException("Invalid list type: " + listType);
        };
        
        // Calculate counts for all entity types, not just the current list type
        
        // Get all persons, categories, and years
        List<String> allPersons = metadata.people().values().stream()
                .map(PersonMetadata::getFullName)
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
                
        List<String> allCategories = metadata.categories().values().stream()
                .map(CategoryMetadata::title)
                .distinct()
                .sorted(czechCollator::compare)
                .toList();
                
        List<String> allYears = allEntries.stream()
                .map(e -> String.valueOf(e.created().getYear()))
                .distinct()
                .sorted()
                .toList();
        
        // Count entries for each person
        for (String person : allPersons) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.persons().contains(person))
                    .count();
            counts.put(person, count);
        }
        
        // Count entries for each category
        for (String category : allCategories) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.categories().contains(category))
                    .count();
            counts.put(category, count);
        }
        
        // Count entries for each year
        for (String year : allYears) {
            int count = (int) allEntries.stream()
                    .filter(e -> String.valueOf(e.created().getYear()).equals(year))
                    .count();
            counts.put(year, count);
        }
        
        // Setup common context variables
        setupCommonContext(context, metadata, allEntries, pageType, null, pageTitle);
        
        // Add specific variables for the generic template
        context.setVariable("items", items);
        context.setVariable("counts", counts);
        context.setVariable("itemType", itemType);
        context.setVariable("listTitle", listTitle);
        context.setVariable("basePath", BASE_PATH_SUBDIRECTORY);
        
        return templateEngine.process("generic_list", context);
    }
    
    /**
     * Helper method to capitalize the first letter of a string
     */
    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
    
    // Maintain backward compatibility with existing code
    public String generatePersonsListPage(Metadata metadata, List<Entry> allEntries) {
        return generateListPage("persons", metadata, allEntries);
    }
    
    public String generateCategoriesListPage(Metadata metadata, List<Entry> allEntries) {
        return generateListPage("categories", metadata, allEntries);
    }
    
    public String generateYearsListPage(List<Entry> allEntries, Metadata metadata) {
        return generateListPage("years", metadata, allEntries);
    }
}
