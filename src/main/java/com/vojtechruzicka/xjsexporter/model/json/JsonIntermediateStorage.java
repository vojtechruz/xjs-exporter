package com.vojtechruzicka.xjsexporter.model.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vojtechruzicka.xjsexporter.AttachmentMetadata;
import com.vojtechruzicka.xjsexporter.CategoryMetadata;
import com.vojtechruzicka.xjsexporter.model.Entry;
import com.vojtechruzicka.xjsexporter.model.EntryMetadata;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import com.vojtechruzicka.xjsexporter.model.PersonMetadata;
import com.vojtechruzicka.xjsexporter.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for handling the JSON intermediate storage format.
 * Responsible for serializing/deserializing data to/from JSON files
 * and managing the directory structure.
 */
@Service
@Slf4j
public class JsonIntermediateStorage {

    private static final String ENTRIES_DIR = "entries";
    private static final String METADATA_DIR = "metadata";
    private static final String PEOPLE_FILE = "people.json";
    private static final String CATEGORIES_FILE = "categories.json";
    private static final String ATTACHMENTS_FILE = "attachments.json";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String SOURCE_SYSTEM = "legacy-xjs-system";
    private static final String EXTRACTOR_VERSION = "1.0.0";

    private final ObjectMapper objectMapper;
    private final FileService fileService;

    public JsonIntermediateStorage(FileService fileService) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.fileService = fileService;
    }

    /**
     * Creates the directory structure for the intermediate data.
     *
     * @param basePath The base path for the intermediate data
     * @throws IOException If an I/O error occurs
     */
    public void createDirectoryStructure(String basePath) throws IOException {
        Path baseDir = Path.of(basePath);
        Path entriesDir = baseDir.resolve(ENTRIES_DIR);
        Path metadataDir = baseDir.resolve(METADATA_DIR);

        Files.createDirectories(entriesDir);
        Files.createDirectories(metadataDir);
    }

    /**
     * Saves the metadata to JSON files.
     *
     * @param basePath The base path for the intermediate data
     * @param metadata The metadata to save
     * @throws IOException If an I/O error occurs
     */
    public void saveMetadata(String basePath, Metadata metadata) throws IOException {
        Path baseDir = Path.of(basePath);
        Path metadataDir = baseDir.resolve(METADATA_DIR);

        // Save people.json
        List<PersonJson> people = metadata.people().values().stream()
                .map(this::convertToPersonJson)
                .collect(Collectors.toList());
        objectMapper.writeValue(metadataDir.resolve(PEOPLE_FILE).toFile(), people);

        // Save categories.json
        List<CategoryJson> categories = metadata.categories().values().stream()
                .map(this::convertToCategoryJson)
                .collect(Collectors.toList());
        objectMapper.writeValue(metadataDir.resolve(CATEGORIES_FILE).toFile(), categories);

        // Save attachments.json
        List<AttachmentJson> attachments = metadata.attachments().values().stream()
                .map(this::convertToAttachmentJson)
                .collect(Collectors.toList());
        objectMapper.writeValue(metadataDir.resolve(ATTACHMENTS_FILE).toFile(), attachments);
    }

    /**
     * Saves an entry to a JSON file.
     *
     * @param basePath      The base path for the intermediate data
     * @param entryMetadata The entry metadata to save
     * @param htmlBody      The HTML body of the entry
     * @throws IOException If an I/O error occurs
     */
    public void saveEntry(String basePath, EntryMetadata entryMetadata, String htmlBody) throws IOException {
        Path baseDir = Path.of(basePath);
        Path entriesDir = baseDir.resolve(ENTRIES_DIR);

        String filename = String.format("%s_%s.json",
                entryMetadata.dateCreated().toLocalDate().format(DateTimeFormatter.ISO_DATE),
                entryMetadata.id());

        EntryJson entryJson = new EntryJson(
                entryMetadata.id(),
                entryMetadata.title(),
                entryMetadata.dateCreated(),
                entryMetadata.location(),
                htmlBody,
                entryMetadata.personIds(),
                entryMetadata.categoryIds(),
                entryMetadata.attachmentIds(),
                SOURCE_SYSTEM,
                LocalDateTime.now()
        );

        objectMapper.writeValue(entriesDir.resolve(filename).toFile(), entryJson);
    }

    /**
     * Saves the manifest file.
     *
     * @param basePath        The base path for the intermediate data
     * @param metadata        The metadata
     * @param sourceDirectory The source directory
     * @throws IOException If an I/O error occurs
     */
    public void saveManifest(String basePath, Metadata metadata, String sourceDirectory) throws IOException {
        Path baseDir = Path.of(basePath);

        ManifestJson manifest = new ManifestJson(
                LocalDateTime.now(),
                SOURCE_SYSTEM,
                sourceDirectory,
                metadata.entries().size(),
                metadata.people().size(),
                metadata.categories().size(),
                metadata.attachments().size(),
                EXTRACTOR_VERSION
        );

        objectMapper.writeValue(baseDir.resolve(MANIFEST_FILE).toFile(), manifest);
    }

    /**
     * Loads all data from the intermediate storage.
     *
     * @param basePath The base path for the intermediate data
     * @return The metadata and entries
     * @throws IOException If an I/O error occurs
     */
    public MetadataAndEntries loadAll(String basePath) throws IOException {
        Path baseDir = Path.of(basePath);
        Path entriesDir = baseDir.resolve(ENTRIES_DIR);
        Path metadataDir = baseDir.resolve(METADATA_DIR);

        // Validate directory structure
        validateDirectoryStructure(baseDir, entriesDir, metadataDir);

        // Load manifest
        ManifestJson manifest;
        try {
            manifest = objectMapper.readValue(
                    baseDir.resolve(MANIFEST_FILE).toFile(),
                    ManifestJson.class
            );
            log.info("Loaded manifest: {} entries, {} people, {} categories, {} attachments",
                    manifest.entryCount(), manifest.personCount(), manifest.categoryCount(), manifest.attachmentCount());
        } catch (IOException e) {
            throw new IOException("Failed to load manifest.json: " + e.getMessage(), e);
        }

        // Load people
        List<PersonJson> people;
        try {
            people = objectMapper.readValue(
                    metadataDir.resolve(PEOPLE_FILE).toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PersonJson.class)
            );
            log.info("Loaded {} people from people.json", people.size());
        } catch (IOException e) {
            throw new IOException("Failed to load people.json: " + e.getMessage(), e);
        }

        // Load categories
        List<CategoryJson> categories;
        try {
            categories = objectMapper.readValue(
                    metadataDir.resolve(CATEGORIES_FILE).toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryJson.class)
            );
            log.info("Loaded {} categories from categories.json", categories.size());
        } catch (IOException e) {
            throw new IOException("Failed to load categories.json: " + e.getMessage(), e);
        }

        // Load attachments
        List<AttachmentJson> attachments;
        try {
            attachments = objectMapper.readValue(
                    metadataDir.resolve(ATTACHMENTS_FILE).toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AttachmentJson.class)
            );
            log.info("Loaded {} attachments from attachments.json", attachments.size());
        } catch (IOException e) {
            throw new IOException("Failed to load attachments.json: " + e.getMessage(), e);
        }

        // Convert to metadata
        Map<String, PersonMetadata> personMap = new HashMap<>();
        for (PersonJson person : people) {
            personMap.put(person.id(), new PersonMetadata(
                    person.id(),
                    person.firstName(),
                    person.lastName(),
                    person.nickName()
            ));
        }

        Map<String, CategoryMetadata> categoryMap = new HashMap<>();
        for (CategoryJson category : categories) {
            categoryMap.put(category.id(), new CategoryMetadata(
                    category.id(),
                    category.title()
            ));
        }

        Map<String, AttachmentMetadata> attachmentMap = new HashMap<>();
        for (AttachmentJson attachment : attachments) {
            attachmentMap.put(attachment.id(), new AttachmentMetadata(
                    attachment.id(),
                    attachment.absoluteSourcePath(),
                    attachment.name(),
                    attachment.relativeLocation()
            ));
        }

        // Load entries
        List<Entry> entries = new ArrayList<>();
        Map<String, EntryMetadata> entryMetadataMap = new HashMap<>();

        File[] entryFiles = entriesDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (entryFiles != null) {
            int successCount = 0;
            int errorCount = 0;
            
            for (File entryFile : entryFiles) {
                try {
                    EntryJson entryJson = objectMapper.readValue(entryFile, EntryJson.class);
                    
                    // Validate entry JSON
                    if (entryJson.id() == null || entryJson.id().isEmpty()) {
                        log.warn("Skipping entry with missing ID in file: {}", entryFile.getName());
                        errorCount++;
                        continue;
                    }
                    
                    if (entryJson.dateCreated() == null) {
                        log.warn("Skipping entry with missing date in file: {}", entryFile.getName());
                        errorCount++;
                        continue;
                    }
                    
                    // Create EntryMetadata
                    EntryMetadata entryMetadata = new EntryMetadata(
                            entryJson.id(),
                            entryJson.title(),
                            entryJson.location(),
                            entryJson.dateCreated(),
                            entryJson.attachmentIds(),
                            entryJson.categoryIds(),
                            entryJson.personIds()
                    );
                    entryMetadataMap.put(entryJson.id(), entryMetadata);
                    
                    // Create Entry
                    List<String> persons = new ArrayList<>();
                    for (String personId : entryJson.personIds()) {
                        PersonMetadata personMetadata = personMap.get(personId);
                        if (personMetadata != null) {
                            persons.add(personMetadata.getFullName());
                        } else {
                            log.warn("Unknown person ID '{}' in entry: {}", personId, entryJson.id());
                        }
                    }
                    
                    List<String> categoryTitles = new ArrayList<>();
                    for (String categoryId : entryJson.categoryIds()) {
                        CategoryMetadata categoryMetadata = categoryMap.get(categoryId);
                        if (categoryMetadata != null) {
                            categoryTitles.add(categoryMetadata.title());
                        } else {
                            log.warn("Unknown category ID '{}' in entry: {}", categoryId, entryJson.id());
                        }
                    }
                    
                    List<com.vojtechruzicka.xjsexporter.model.Attachment> entryAttachments = new ArrayList<>();
                    for (String attachmentId : entryJson.attachmentIds()) {
                        AttachmentMetadata attachmentMetadata = attachmentMap.get(attachmentId);
                        if (attachmentMetadata != null) {
                            entryAttachments.add(fileService.getAttachmentFromMetadata(attachmentMetadata));
                        } else {
                            log.warn("Unknown attachment ID '{}' in entry: {}", attachmentId, entryJson.id());
                        }
                    }
                    
                    Entry entry = new Entry(
                            entryJson.id(),
                            entryJson.title(),
                            entryJson.dateCreated(),
                            entryJson.htmlBody(),
                            persons,
                            categoryTitles,
                            entryAttachments,
                            entryJson.location()
                    );
                    
                    entries.add(entry);
                    successCount++;
                } catch (Exception e) {
                    log.error("Error loading entry from file {}: {}", entryFile.getName(), e.getMessage());
                    errorCount++;
                }
            }
            
            log.info("Loaded {} entries successfully, {} entries with errors", successCount, errorCount);
            
            if (entries.isEmpty()) {
                throw new IOException("No entries could be loaded successfully from " + entriesDir);
            }
        }

        Metadata reconstructedMetadata = new Metadata(personMap, categoryMap, attachmentMap, entryMetadataMap);
        return new MetadataAndEntries(reconstructedMetadata, entries);
    }

    /**
     * Converts a PersonMetadata to a PersonJson.
     *
     * @param personMetadata The PersonMetadata to convert
     * @return The PersonJson
     */
    private PersonJson convertToPersonJson(PersonMetadata personMetadata) {
        return new PersonJson(
                personMetadata.id(),
                personMetadata.firstName(),
                personMetadata.lastName(),
                personMetadata.nickName(),
                personMetadata.getFullName()
        );
    }

    /**
     * Converts a CategoryMetadata to a CategoryJson.
     *
     * @param categoryMetadata The CategoryMetadata to convert
     * @return The CategoryJson
     */
    private CategoryJson convertToCategoryJson(CategoryMetadata categoryMetadata) {
        return new CategoryJson(
                categoryMetadata.id(),
                categoryMetadata.title()
        );
    }

    /**
     * Converts an AttachmentMetadata to an AttachmentJson.
     *
     * @param attachmentMetadata The AttachmentMetadata to convert
     * @return The AttachmentJson
     */
    private AttachmentJson convertToAttachmentJson(AttachmentMetadata attachmentMetadata) {
        return new AttachmentJson(
                attachmentMetadata.id(),
                attachmentMetadata.absoluteSourcePath(),
                attachmentMetadata.name(),
                attachmentMetadata.relativeLocation()
        );
    }

    /**
     * Validates that the required directory structure and files exist.
     *
     * @param baseDir    The base directory
     * @param entriesDir The entries directory
     * @param metadataDir The metadata directory
     * @throws IOException If the directory structure is invalid
     */
    private void validateDirectoryStructure(Path baseDir, Path entriesDir, Path metadataDir) throws IOException {
        // Check if base directory exists
        if (!Files.exists(baseDir)) {
            throw new IOException("Intermediate data directory does not exist: " + baseDir);
        }
        
        // Check if entries directory exists
        if (!Files.exists(entriesDir)) {
            throw new IOException("Entries directory does not exist: " + entriesDir);
        }
        
        // Check if metadata directory exists
        if (!Files.exists(metadataDir)) {
            throw new IOException("Metadata directory does not exist: " + metadataDir);
        }
        
        // Check if manifest file exists
        if (!Files.exists(baseDir.resolve(MANIFEST_FILE))) {
            throw new IOException("Manifest file does not exist: " + baseDir.resolve(MANIFEST_FILE));
        }
        
        // Check if people file exists
        if (!Files.exists(metadataDir.resolve(PEOPLE_FILE))) {
            throw new IOException("People file does not exist: " + metadataDir.resolve(PEOPLE_FILE));
        }
        
        // Check if categories file exists
        if (!Files.exists(metadataDir.resolve(CATEGORIES_FILE))) {
            throw new IOException("Categories file does not exist: " + metadataDir.resolve(CATEGORIES_FILE));
        }
        
        // Check if attachments file exists
        if (!Files.exists(metadataDir.resolve(ATTACHMENTS_FILE))) {
            throw new IOException("Attachments file does not exist: " + metadataDir.resolve(ATTACHMENTS_FILE));
        }
        
        // Check if entries directory has any files
        try (var files = Files.list(entriesDir)) {
            if (files.noneMatch(path -> path.toString().endsWith(".json"))) {
                throw new IOException("No entry files found in: " + entriesDir);
            }
        }
    }

    /**
     * Container class for metadata and entries.
     */
    public record MetadataAndEntries(Metadata metadata, List<Entry> entries) {
    }
}