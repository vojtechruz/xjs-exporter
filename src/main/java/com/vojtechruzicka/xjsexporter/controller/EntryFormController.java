package com.vojtechruzicka.xjsexporter.controller;

import com.vojtechruzicka.xjsexporter.AttachmentMetadata;
import com.vojtechruzicka.xjsexporter.CategoryMetadata;
import com.vojtechruzicka.xjsexporter.HtmlGenerator;
import com.vojtechruzicka.xjsexporter.model.EntryMetadata;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import com.vojtechruzicka.xjsexporter.model.PersonMetadata;
import com.vojtechruzicka.xjsexporter.model.json.JsonIntermediateStorage;
import com.vojtechruzicka.xjsexporter.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling the entry form for adding plaintext content with metadata.
 */
@Controller
@RequestMapping("/entry-form")
@Slf4j
public class EntryFormController {

    private final JsonIntermediateStorage jsonStorage;
    private final FileService fileService;
    private final HtmlGenerator htmlGenerator;

    @Value("${intermediate.data.path:C:\\projects\\xjs-exporter\\intermediate-data\\}")
    private String intermediatePath;

    public EntryFormController(JsonIntermediateStorage jsonStorage, FileService fileService, HtmlGenerator htmlGenerator) {
        this.jsonStorage = jsonStorage;
        this.fileService = fileService;
        this.htmlGenerator = htmlGenerator;
    }

    /**
     * Form data class to hold the form input
     */
    public static class EntryFormData {
        private String title;
        private String content;
        private String location;
        private LocalDateTime dateCreated;
        private String persons; // Comma-separated list of persons
        private String categories; // Comma-separated list of categories

        // Getters and setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public LocalDateTime getDateCreated() {
            return dateCreated;
        }

        public void setDateCreated(LocalDateTime dateCreated) {
            this.dateCreated = dateCreated;
        }

        public String getPersons() {
            return persons;
        }

        public void setPersons(String persons) {
            this.persons = persons;
        }

        public String getCategories() {
            return categories;
        }

        public void setCategories(String categories) {
            this.categories = categories;
        }
    }


    /**
     * Process the form submission
     */
    @PostMapping
    public String processEntryForm(@ModelAttribute("entryForm") EntryFormData formData, 
                                  RedirectAttributes redirectAttributes) {
        try {
            // Load existing metadata
            JsonIntermediateStorage.MetadataAndEntries data = jsonStorage.loadAll(intermediatePath);
            Metadata metadata = data.metadata();
            
            // Generate a unique ID for the entry
            String entryId = UUID.randomUUID().toString();
            
            // Process persons
            List<String> personIds = new ArrayList<>();
            if (formData.getPersons() != null && !formData.getPersons().isEmpty()) {
                String[] personNames = formData.getPersons().split(",");
                for (String personName : personNames) {
                    String trimmedName = personName.trim();
                    if (!trimmedName.isEmpty()) {
                        // Find or create person
                        String personId = findOrCreatePerson(metadata.people(), trimmedName);
                        personIds.add(personId);
                    }
                }
            }
            
            // Process categories
            List<String> categoryIds = new ArrayList<>();
            if (formData.getCategories() != null && !formData.getCategories().isEmpty()) {
                String[] categoryNames = formData.getCategories().split(",");
                for (String categoryName : categoryNames) {
                    String trimmedName = categoryName.trim();
                    if (!trimmedName.isEmpty()) {
                        // Find or create category
                        String categoryId = findOrCreateCategory(metadata.categories(), trimmedName);
                        categoryIds.add(categoryId);
                    }
                }
            }
            
            // Ensure dateCreated is set if missing (static form may not supply it)
            if (formData.getDateCreated() == null) {
                formData.setDateCreated(LocalDateTime.now());
            }

            // Create entry metadata
            EntryMetadata entryMetadata = new EntryMetadata(
                    entryId,
                    formData.getTitle(),
                    formData.getLocation(),
                    formData.getDateCreated(),
                    List.of(), // No attachments for now
                    categoryIds,
                    personIds
            );
            
            // Convert plaintext to HTML
            String htmlContent = convertToHtml(formData.getContent());
            
            // Save entry to JSON
            jsonStorage.saveEntry(intermediatePath, entryMetadata, htmlContent);
            
            // Save updated metadata
            jsonStorage.saveMetadata(intermediatePath, metadata);
            
            // Add success message
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Entry saved successfully! You can now generate HTML using the 'generate' command.");
            
            return "redirect:/entry-form.html";
            
        } catch (IOException e) {
            log.error("Error saving entry: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error saving entry: " + e.getMessage());
            return "redirect:/entry-form.html";
        }
    }
    
    /**
     * Find a person by name or create a new one
     */
    private String findOrCreatePerson(Map<String, PersonMetadata> people, String fullName) {
        // First check if the person already exists
        for (PersonMetadata person : people.values()) {
            if (person.getFullName().equalsIgnoreCase(fullName)) {
                return person.id();
            }
        }
        
        // Create a new person
        String personId = UUID.randomUUID().toString();
        
        // Simple name parsing - assumes first name and last name
        String firstName = fullName;
        String lastName = "";
        String nickName = "";
        
        if (fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            firstName = parts[0];
            lastName = parts[1];
        }
        
        PersonMetadata newPerson = new PersonMetadata(personId, firstName, lastName, nickName);
        people.put(personId, newPerson);
        
        return personId;
    }
    
    /**
     * Find a category by name or create a new one
     */
    private String findOrCreateCategory(Map<String, CategoryMetadata> categories, String categoryName) {
        // First check if the category already exists
        for (CategoryMetadata category : categories.values()) {
            if (category.title().equalsIgnoreCase(categoryName)) {
                return category.id();
            }
        }
        
        // Create a new category
        String categoryId = UUID.randomUUID().toString();
        CategoryMetadata newCategory = new CategoryMetadata(categoryId, categoryName);
        categories.put(categoryId, newCategory);
        
        return categoryId;
    }
    
    /**
     * Convert plaintext to HTML
     */
    private String convertToHtml(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        
        // Replace newlines with <br> tags
        String html = plaintext.replace("\n", "<br>\n");
        
        // Wrap in a paragraph
        return "<p>" + html + "</p>";
    }
}