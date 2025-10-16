package com.vojtechruzicka.xjsexporter;

import com.vojtechruzicka.xjsexporter.config.ExporterConfiguration;
import com.vojtechruzicka.xjsexporter.model.*;
import com.vojtechruzicka.xjsexporter.model.json.JsonIntermediateStorage;
import com.vojtechruzicka.xjsexporter.service.FileService;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jsoup.Jsoup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;

@Slf4j
@ShellComponent
public class Extractor {

    private final MetadataExtractor metadataExtractor;
    private final Terminal terminal;
    private final JsonIntermediateStorage jsonStorage;

    public Extractor(MetadataExtractor metadataExtractor, Terminal terminal, JsonIntermediateStorage jsonStorage) {
        this.metadataExtractor = metadataExtractor;
        this.terminal = terminal;
        this.jsonStorage = jsonStorage;
    }

    @ShellMethod(value = "Extracts journal entries from XJS format and saves as JSON", key = "extract")
    public String extract(
            @ShellOption(defaultValue = "C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\", 
                    help = "Source directory containing XJS journal entries") String sourcePath,
            @ShellOption(defaultValue = "C:\\projects\\xjs-exporter\\intermediate-data\\", 
                    help = "Target directory for intermediate JSON files") String intermediatePath) {

        // Ensure paths end with separator
        final String finalSourcePath = sourcePath.endsWith(File.separator) ? sourcePath : sourcePath + File.separator;
        final String finalIntermediatePath = intermediatePath.endsWith(File.separator) ? intermediatePath : intermediatePath + File.separator;

        Metadata metadata;

        try {
            metadata = metadataExtractor.extractMetadata(finalSourcePath);
        } catch (IOException e) {
            return MessageFormat.format("Failed to extract metadata: {0}", e.getMessage());
        }

        // Create intermediate data directory structure
        try {
            jsonStorage.createDirectoryStructure(finalIntermediatePath);
        } catch (IOException e) {
            terminal.writer().println("Could not create intermediate data directory structure: " + finalIntermediatePath + ", Error: " + e);
            return "Failed to create intermediate data directory structure: " + e.getMessage();
        }

        // Save metadata to JSON files
        try {
            jsonStorage.saveMetadata(finalIntermediatePath, metadata);
        } catch (IOException e) {
            terminal.writer().println("Could not save metadata to JSON files: " + e);
            return "Failed to save metadata to JSON files: " + e.getMessage();
        }

        // Save entries to JSON files
        int entryCount = 0;
        for (EntryMetadata entryMetadata : metadata.entries().values()) {
            try {
                String htmlBody = getHtmlBody(entryMetadata);
                jsonStorage.saveEntry(finalIntermediatePath, metadata, entryMetadata, htmlBody);
                entryCount++;
            } catch (IOException e) {
                terminal.writer().println("Could not save entry to JSON file: " + entryMetadata.id() + ", Error: " + e);
            }
        }

        // Save manifest file
        try {
            jsonStorage.saveManifest(finalIntermediatePath, metadata, finalSourcePath);
        } catch (IOException e) {
            terminal.writer().println("Could not save manifest file: " + e);
            return "Failed to save manifest file: " + e.getMessage();
        }

        return "Extract finished, " + entryCount + " entries extracted to " + finalIntermediatePath;
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

    public static void main(String[] args) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("help".getBytes());
        FileService fileService = new FileService();
        fileService.init();

        // Capture all output written to the terminal
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Create a custom terminal with the simulated streams
        Terminal terminal = TerminalBuilder.builder()
                .streams(inputStream, outputStream) // Custom input and output streams
                .system(false)                      // Do not use the system terminal
                .build();

        // Create components
        MetadataExtractor metadataExtractor = new MetadataExtractor();
        HtmlGenerator htmlGenerator = new HtmlGenerator(new ExporterConfiguration().defaultTemplatingEngine(), fileService);
        JsonIntermediateStorage jsonStorage = new JsonIntermediateStorage(fileService);
        
        // Define paths
        String sourcePath = "C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\";
        String intermediatePath = "C:\\projects\\xjs-exporter\\intermediate-data\\";
        String targetPath = "C:\\projects\\xjs-exporter\\OUT\\";
        
        // Extract data to JSON
        Extractor extractor = new Extractor(metadataExtractor, terminal, jsonStorage);
        String extractResult = extractor.extract(sourcePath, intermediatePath);
        System.out.println(extractResult);
        
        // Generate HTML from JSON
        Generator generator = new Generator(htmlGenerator, jsonStorage, terminal, fileService);
        String generateResult = generator.generate(intermediatePath, targetPath);
        System.out.println(generateResult);


        try {
            File htmlFile = new File(targetPath+"index.html");

            // Check if Desktop is supported
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                // Check if browse action is supported
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(htmlFile.toURI());
                } else {
                    System.err.println("Browse action not supported");
                }
            } else {
                System.err.println("Desktop not supported");
            }
        } catch (IOException e) {
            System.err.println("Error opening file: " + e.getMessage());
        }

    }
}