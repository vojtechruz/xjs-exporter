package com.vojtechruzicka.xjsexporter;

import com.vojtechruzicka.xjsexporter.config.ExporterConfiguration;
import com.vojtechruzicka.xjsexporter.model.Attachment;
import com.vojtechruzicka.xjsexporter.model.Entry;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HtmlGenerator {

    private final TemplateEngine templateEngine;

    public HtmlGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public static void main(String[] args) {

        TemplateEngine templateEngine = new ExporterConfiguration().defaultTemplatingEngine();

        HtmlGenerator generator = new HtmlGenerator(templateEngine);

        String html = generator.generateHtml("My Title", LocalDateTime.now(),"<h1>Hello world</h1>", List.of("CAT1", "CAT2"), List.of("PERSON1", "PERSON2", "PERSON3"), List.of(new Attachment("C:\\temp\\file.txt","ATACHMENT1","temp\\file.txt")));
        System.out.println(html);
    }

    private String getCssContent() {
        try (var resource = getClass().getResourceAsStream("/static/css/styles.css")) {
            if (resource != null) {
                String css = new String(resource.readAllBytes());

                return css;
            } else {
                return "/* CSS file not found */";
            }
        } catch (IOException e) {
            return "/* CSS file not found: " + e.getMessage() + " */";
        }


    }


    public String generateHtml(String title, LocalDateTime created, String htmlBody, List<String> categories, List<String> persons, List<Attachment> attachments) {
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("body", htmlBody);
        context.setVariable("categories", categories);
        context.setVariable("persons", persons);
        context.setVariable("attachments", attachments);
        context.setVariable("dateCreated", created);

        return templateEngine.process("entry", context);
    }

    public String generateMainPage(Metadata metadata, List<Entry> entries) {
        return generateMainPage(metadata, entries, "main", null);
    }

    public String generateMainPage(Metadata metadata, List<Entry> entries, String pageType, String currentItem) {
        // Get all available categories, persons, and years
        List<String> allCategories = metadata.categories().values().stream().map(c -> c.title()).distinct().toList();
        List<String> allPersons = metadata.people().values().stream().map(c -> c.getFullName()).distinct().toList();
        List<String> allYears = entries.stream().map(e -> String.valueOf(e.created().getYear())).distinct().toList();

        // Create filtered lists based on the current entries
        List<String> filteredCategories = entries.stream()
                .flatMap(e -> e.categories().stream())
                .distinct()
                .toList();
        
        List<String> filteredPersons = entries.stream()
                .flatMap(e -> e.persons().stream())
                .distinct()
                .toList();
        
        List<String> filteredYears = entries.stream()
                .map(e -> String.valueOf(e.created().getYear()))
                .distinct()
                .toList();

        // Set up the context
        Context context = new Context();
        context.setVariable("cssContent", getCssContent());

        context.setVariable("journalEntries", entries);
        context.setVariable("persons", allPersons);
        context.setVariable("categories", allCategories);
        context.setVariable("years", allYears);
        
        // Add filtered lists
        context.setVariable("filteredPersons", filteredPersons);
        context.setVariable("filteredCategories", filteredCategories);
        context.setVariable("filteredYears", filteredYears);
        
        // Add page type and current item
        context.setVariable("pageType", pageType);
        context.setVariable("currentItem", currentItem);
        
        // Set page title based on page type
        String pageTitle = "Journal Entries";
        if (pageType.equals("person") && currentItem != null) {
            pageTitle = "Entries for Person: " + currentItem;
        } else if (pageType.equals("category") && currentItem != null) {
            pageTitle = "Entries for Category: " + currentItem;
        } else if (pageType.equals("year") && currentItem != null) {
            pageTitle = "Entries for Year: " + currentItem;
        }
        context.setVariable("pageTitle", pageTitle);

        return templateEngine.process("journal_entries_display", context);
    }
    
    public String generatePersonsListPage(Metadata metadata, List<Entry> allEntries) {
        List<String> persons = metadata.people().values().stream()
                .map(p -> p.getFullName())
                .distinct()
                .sorted()
                .toList();
        
        // Count entries for each person
        Map<String, Integer> personCounts = new HashMap<>();
        for (String person : persons) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.persons().contains(person))
                    .count();
            personCounts.put(person, count);
        }
        
        // Get all available categories, persons, and years for navigation
        List<String> allCategories = metadata.categories().values().stream().map(c -> c.title()).distinct().toList();
        List<String> allPersons = metadata.people().values().stream().map(c -> c.getFullName()).distinct().toList();
        List<String> allYears = allEntries.stream().map(e -> String.valueOf(e.created().getYear())).distinct().toList();
        
        Context context = new Context();
        context.setVariable("cssContent", getCssContent());
        context.setVariable("persons", persons);
        context.setVariable("personCounts", personCounts);
        
        // Add navigation variables
        context.setVariable("categories", allCategories);
        context.setVariable("years", allYears);
        context.setVariable("pageType", "persons_list");
        context.setVariable("currentItem", null);
        context.setVariable("pageTitle", "Persons List");
        
        // Empty filtered lists since this is a list page
        context.setVariable("filteredPersons", List.of());
        context.setVariable("filteredCategories", List.of());
        context.setVariable("filteredYears", List.of());
        
        return templateEngine.process("persons_list", context);
    }
    
    public String generateCategoriesListPage(Metadata metadata, List<Entry> allEntries) {
        List<String> categories = metadata.categories().values().stream()
                .map(c -> c.title())
                .distinct()
                .sorted()
                .toList();
        
        // Count entries for each category
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (String category : categories) {
            int count = (int) allEntries.stream()
                    .filter(e -> e.categories().contains(category))
                    .count();
            categoryCounts.put(category, count);
        }
        
        // Get all available categories, persons, and years for navigation
        List<String> allCategories = metadata.categories().values().stream().map(c -> c.title()).distinct().toList();
        List<String> allPersons = metadata.people().values().stream().map(c -> c.getFullName()).distinct().toList();
        List<String> allYears = allEntries.stream().map(e -> String.valueOf(e.created().getYear())).distinct().toList();
        
        Context context = new Context();
        context.setVariable("cssContent", getCssContent());
        context.setVariable("categories", categories);
        context.setVariable("categoryCounts", categoryCounts);
        
        // Add navigation variables
        context.setVariable("persons", allPersons);
        context.setVariable("years", allYears);
        context.setVariable("pageType", "categories_list");
        context.setVariable("currentItem", null);
        context.setVariable("pageTitle", "Categories List");
        
        // Empty filtered lists since this is a list page
        context.setVariable("filteredPersons", List.of());
        context.setVariable("filteredCategories", List.of());
        context.setVariable("filteredYears", List.of());
        
        return templateEngine.process("categories_list", context);
    }
    
    public String generateYearsListPage(List<Entry> allEntries, Metadata metadata) {
        List<String> years = allEntries.stream()
                .map(e -> String.valueOf(e.created().getYear()))
                .distinct()
                .sorted()
                .toList();
        
        // Count entries for each year
        Map<String, Integer> yearCounts = new HashMap<>();
        for (String year : years) {
            int count = (int) allEntries.stream()
                    .filter(e -> String.valueOf(e.created().getYear()).equals(year))
                    .count();
            yearCounts.put(year, count);
        }
        
        // Get all available categories, persons, and years for navigation
        List<String> allCategories = metadata.categories().values().stream().map(c -> c.title()).distinct().toList();
        List<String> allPersons = metadata.people().values().stream().map(c -> c.getFullName()).distinct().toList();
        List<String> allYears = allEntries.stream().map(e -> String.valueOf(e.created().getYear())).distinct().toList();
        
        Context context = new Context();
        context.setVariable("cssContent", getCssContent());
        context.setVariable("years", years);
        context.setVariable("yearCounts", yearCounts);
        
        // Add navigation variables
        context.setVariable("persons", allPersons);
        context.setVariable("categories", allCategories);
        context.setVariable("pageType", "years_list");
        context.setVariable("currentItem", null);
        context.setVariable("pageTitle", "Years List");
        
        // Empty filtered lists since this is a list page
        context.setVariable("filteredPersons", List.of());
        context.setVariable("filteredCategories", List.of());
        context.setVariable("filteredYears", List.of());
        
        return templateEngine.process("years_list", context);
    }
}
