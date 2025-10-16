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
    private static final String ATTACHMENTS_DIR = "attachments";
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
        Path attachmentsDir = baseDir.resolve(ATTACHMENTS_DIR);

        Files.createDirectories(entriesDir);
        Files.createDirectories(metadataDir);
        Files.createDirectories(attachmentsDir);
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
        Path attachmentsDir = baseDir.resolve(ATTACHMENTS_DIR);

        // Ensure directories exist
        Files.createDirectories(metadataDir);
        Files.createDirectories(attachmentsDir);

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

        // Copy attachment files into intermediate storage
        metadata.attachments().values().forEach(att -> {
            try {
                if (att.absoluteSourcePath() != null && !att.absoluteSourcePath().isEmpty()) {
                    Path source = Path.of(att.absoluteSourcePath());
                    if (Files.exists(source)) {
                        Path target = attachmentsDir.resolve(att.name());
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.warn("Attachment source file does not exist: {}", source);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to copy attachment '{}' to intermediate storage: {}", att.name(), e.getMessage());
            }
        });
    }

    /**
     * Saves an entry to a JSON file.
     *
     * @param basePath      The base path for the intermediate data
     * @param entryMetadata The entry metadata to save
     * @param htmlBody      The HTML body of the entry
     * @throws IOException If an I/O error occurs
     */
    public void saveEntry(String basePath, Metadata metadata, EntryMetadata entryMetadata, String htmlBody) throws IOException {
        Path baseDir = Path.of(basePath);
        Path entriesDir = baseDir.resolve(ENTRIES_DIR);

        // Use Markdown with YAML frontmatter for entries
        String filename = String.format("%s_%s.md",
                entryMetadata.dateCreated().toLocalDate().format(DateTimeFormatter.ISO_DATE),
                entryMetadata.title());

        // Resolve person names and category titles from IDs for Markdown front matter
        List<String> personNames = new ArrayList<>();
        for (String pid : entryMetadata.personIds()) {
            PersonMetadata pm = metadata.people().get(pid);
            if (pm != null) {
                personNames.add(pm.getFullName());
            }
        }
        List<String> categoryTitles = new ArrayList<>();
        for (String cid : entryMetadata.categoryIds()) {
            CategoryMetadata cm = metadata.categories().get(cid);
            if (cm != null) {
                categoryTitles.add(cm.title());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("id: ").append(escapeYaml(entryMetadata.id())).append("\n");
        sb.append("title: ").append(escapeYaml(entryMetadata.title())).append("\n");
        sb.append("dateCreated: ").append(entryMetadata.dateCreated().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n");
        if (entryMetadata.location() != null) {
            sb.append("location: ").append(escapeYaml(entryMetadata.location())).append("\n");
        }
        // Store human-readable names instead of IDs in Markdown
        sb.append("persons: ").append(formatYamlList(personNames)).append("\n");
        sb.append("categories: ").append(formatYamlList(categoryTitles)).append("\n");
        // New preferred: attachment names (filenames) for easier HTML generation
        List<String> attachmentNames = new ArrayList<>();
        for (String aid : entryMetadata.attachmentIds()) {
            AttachmentMetadata am = metadata.attachments().get(aid);
            if (am != null) {
                attachmentNames.add(am.name());
            }
        }
        sb.append("attachments: ").append(formatYamlList(attachmentNames)).append("\n");
        // Legacy fallback retained for backward compatibility
        sb.append("attachmentIds: ").append(formatYamlList(entryMetadata.attachmentIds())).append("\n");
        sb.append("source: ").append(escapeYaml(SOURCE_SYSTEM)).append("\n");
        sb.append("extractedAt: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n");
        sb.append("---\n\n");
        if (htmlBody != null) {
            sb.append(htmlBody);
            if (!htmlBody.endsWith("\n")) sb.append("\n");
        }

        Files.writeString(entriesDir.resolve(filename), sb.toString());
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
        Map<String, AttachmentMetadata> attachmentByName = new HashMap<>();
        for (AttachmentJson attachment : attachments) {
            AttachmentMetadata am = new AttachmentMetadata(
                    attachment.id(),
                    attachment.absoluteSourcePath(),
                    attachment.name(),
                    attachment.relativeLocation()
            );
            attachmentMap.put(attachment.id(), am);
            if (attachment.name() != null && !attachment.name().isEmpty()) {
                attachmentByName.put(attachment.name(), am);
            }
        }

        // Load entries
        List<Entry> entries = new ArrayList<>();
        Map<String, EntryMetadata> entryMetadataMap = new HashMap<>();

        File[] entryFiles = entriesDir.toFile().listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".md"));
        if (entryFiles != null) {
            int successCount = 0;
            int errorCount = 0;
            
            for (File entryFile : entryFiles) {
                try {
                    String name = entryFile.getName().toLowerCase();
                    String id;
                    String title;
                    LocalDateTime created;
                    String location;
                    List<String> personIds;
                    List<String> categoryIds;
                    List<String> attachmentIds;
                    String bodyHtml;
                    // Optional direct names parsed from Markdown (preferred)
                    List<String> personNamesDirect = new ArrayList<>();
                    List<String> categoryTitlesDirect = new ArrayList<>();
                    List<String> attachmentNamesDirect = new ArrayList<>();

                    if (name.endsWith(".json")) {
                        EntryJson entryJson = objectMapper.readValue(entryFile, EntryJson.class);
                        // Validate
                        if (entryJson.id() == null || entryJson.id().isEmpty() || entryJson.dateCreated() == null) {
                            log.warn("Skipping invalid entry JSON in file: {}", entryFile.getName());
                            errorCount++;
                            continue;
                        }
                        id = entryJson.id();
                        title = entryJson.title();
                        created = entryJson.dateCreated();
                        location = entryJson.location();
                        personIds = entryJson.personIds();
                        categoryIds = entryJson.categoryIds();
                        attachmentIds = entryJson.attachmentIds();
                        bodyHtml = entryJson.htmlBody();
                    } else { // .md
                        MdEntry md = parseMarkdownEntry(entryFile.toPath());
                        if (md == null || md.id == null || md.id.isEmpty() || md.dateCreated == null) {
                            log.warn("Skipping invalid entry Markdown in file: {}", entryFile.getName());
                            errorCount++;
                            continue;
                        }
                        id = md.id;
                        title = md.title;
                        created = md.dateCreated;
                        location = md.location;
                        personIds = md.personIds;
                        categoryIds = md.categoryIds;
                        attachmentIds = md.attachmentIds;
                        personNamesDirect = md.personNames != null ? md.personNames : new ArrayList<>();
                        categoryTitlesDirect = md.categoryTitles != null ? md.categoryTitles : new ArrayList<>();
                        attachmentNamesDirect = md.attachmentNames != null ? md.attachmentNames : new ArrayList<>();
                        bodyHtml = md.body; // stored body as HTML inside markdown body
                    }

                    // Create EntryMetadata
                    EntryMetadata entryMetadata = new EntryMetadata(
                            id,
                            title,
                            location,
                            created,
                            attachmentIds,
                            categoryIds,
                            personIds
                    );
                    entryMetadataMap.put(id, entryMetadata);

                    // Create Entry
                    List<String> persons = new ArrayList<>();
                    if (personNamesDirect != null && !personNamesDirect.isEmpty()) {
                        persons.addAll(personNamesDirect);
                    } else {
                        for (String personId : personIds) {
                            PersonMetadata personMetadata = personMap.get(personId);
                            if (personMetadata != null) {
                                persons.add(personMetadata.getFullName());
                            } else {
                                log.warn("Unknown person ID '{}' in entry: {}", personId, id);
                            }
                        }
                    }

                    List<String> categoryTitles = new ArrayList<>();
                    if (categoryTitlesDirect != null && !categoryTitlesDirect.isEmpty()) {
                        categoryTitles.addAll(categoryTitlesDirect);
                    } else {
                        for (String categoryId : categoryIds) {
                            CategoryMetadata categoryMetadata = categoryMap.get(categoryId);
                            if (categoryMetadata != null) {
                                categoryTitles.add(categoryMetadata.title());
                            } else {
                                log.warn("Unknown category ID '{}' in entry: {}", categoryId, id);
                            }
                        }
                    }

                    List<com.vojtechruzicka.xjsexporter.model.Attachment> entryAttachments = new ArrayList<>();
                    if (attachmentNamesDirect != null && !attachmentNamesDirect.isEmpty()) {
                        for (String fname : attachmentNamesDirect) {
                            AttachmentMetadata attachmentMetadata = attachmentByName.get(fname);
                            if (attachmentMetadata != null) {
                                entryAttachments.add(fileService.getAttachmentFromMetadata(attachmentMetadata));
                            } else {
                                log.warn("Unknown attachment filename '{}' in entry: {}", fname, id);
                            }
                        }
                    } else {
                        for (String attachmentId : attachmentIds) {
                            AttachmentMetadata attachmentMetadata = attachmentMap.get(attachmentId);
                            if (attachmentMetadata != null) {
                                entryAttachments.add(fileService.getAttachmentFromMetadata(attachmentMetadata));
                            } else {
                                log.warn("Unknown attachment ID '{}' in entry: {}", attachmentId, id);
                            }
                        }
                    }

                    Entry entry = new Entry(
                            id,
                            title,
                            created,
                            bodyHtml,
                            persons,
                            categoryTitles,
                            entryAttachments,
                            location
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

    /* Helper methods for Markdown/YAML handling */
    private String escapeYaml(String value) {
        if (value == null) return "";
        // Replace backslashes first for Windows paths, then escape quotes
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        // Wrap in quotes to be safe
        return '"' + escaped + '"';
    }

    private String formatYamlList(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        String joined = items.stream().map(this::escapeYaml).collect(Collectors.joining(", "));
        return "[" + joined + "]";
    }

    private MdEntry parseMarkdownEntry(Path path) throws IOException {
        String content = Files.readString(path);
        String fmStart = "---\n";
        if (!content.startsWith(fmStart)) {
            return null;
        }
        int second = content.indexOf("\n---", fmStart.length() - 1);
        if (second < 0) {
            return null;
        }
        int fmEnd = second + 4; // position after ---
        String front = content.substring(fmStart.length(), second).trim();
        String body = content.substring(fmEnd).trim();

        MdEntry md = new MdEntry();
        md.body = body;
        for (String line : front.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim();
            String rawVal = line.substring(colon + 1).trim();
            if (rawVal.startsWith("[")) {
                // bracketed list
                String inner = rawVal.substring(1, rawVal.endsWith("]") ? rawVal.length() - 1 : rawVal.length());
                List<String> list = new ArrayList<>();
                for (String part : inner.split(",")) {
                    String v = part.trim();
                    if (v.startsWith("\"") && v.endsWith("\"")) {
                        v = v.substring(1, v.length() - 1);
                    }
                    v = v.replace("\\\"", "\"").replace("\\\\", "\\");
                    if (!v.isEmpty()) list.add(v);
                }
                switch (key) {
                    case "personIds" -> md.personIds = list; // legacy
                    case "categoryIds" -> md.categoryIds = list; // legacy
                    case "persons" -> md.personNames = list; // new preferred
                    case "categories" -> md.categoryTitles = list; // new preferred
                    case "attachmentIds" -> md.attachmentIds = list;
                    case "attachments" -> md.attachmentNames = list; // new preferred
                    default -> {}
                }
            } else {
                String v = rawVal;
                if (v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                v = v.replace("\\\"", "\"").replace("\\\\", "\\");
                switch (key) {
                    case "id" -> md.id = v;
                    case "title" -> md.title = v;
                    case "location" -> md.location = v;
                    case "dateCreated" -> {
                        try { md.dateCreated = LocalDateTime.parse(v); } catch (Exception ignored) {}
                    }
                    default -> {}
                }
            }
        }
        return md;
    }

    private static class MdEntry {
        String id;
        String title;
        LocalDateTime dateCreated;
        String location;
        // Legacy ID lists for backward compatibility
        List<String> personIds = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        // New name/title lists preferred going forward
        List<String> personNames = new ArrayList<>();
        List<String> categoryTitles = new ArrayList<>();
        List<String> attachmentNames = new ArrayList<>();
        List<String> attachmentIds = new ArrayList<>();
        String body;
    }

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
            if (files.noneMatch(path -> path.toString().endsWith(".json") || path.toString().endsWith(".md"))) {
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