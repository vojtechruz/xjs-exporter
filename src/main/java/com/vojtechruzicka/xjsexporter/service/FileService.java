package com.vojtechruzicka.xjsexporter.service;

import com.github.slugify.Slugify;
import com.vojtechruzicka.xjsexporter.AttachmentMetadata;
import com.vojtechruzicka.xjsexporter.model.Attachment;
import com.vojtechruzicka.xjsexporter.model.Entry;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileService {

    private Slugify filenameSanitizer;

    @PostConstruct
    public void init() {
        filenameSanitizer = Slugify.builder()
                .lowerCase(true)
                .underscoreSeparator(true)
                .build();
    }


    public String getEntryFileName(Entry entry) {
        return getEntryFileName(entry.created(), entry.title());
    }

    public String getEntryFileName(java.time.LocalDateTime created, String titleRaw) {
        String title = "untitled";
        if (StringUtils.isNotBlank(titleRaw)) {
            title = titleRaw.substring(0, Math.min(titleRaw.length(), 100));
        }
        // Keep a timestamp at the beginning with date and time
        String timestamp = created != null
                ? created.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        String name = String.join("_", timestamp, title);
        // Sanitize to a safe filename (lowercase, underscores, strip disallowed chars)
        return filenameSanitizer.slugify(name);
    }

    public Attachment getAttachmentFromMetadata(AttachmentMetadata attachmentMetadata) {
        String absolutePath = attachmentMetadata.absoluteSourcePath();
        Integer size = getFileSize(absolutePath);

        return new com.vojtechruzicka.xjsexporter.model.Attachment(
                absolutePath,
                attachmentMetadata.name(),
                attachmentMetadata.relativeLocation(),
                getExtension(absolutePath),
                getMimeType(absolutePath),
                size,
                getFileSizeFormatted(size)
        );
    }

    private String getFileSizeFormatted(Integer size) {
        if (size == null) {
            return null;
        }

        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String getMimeType(String absolutePath) {
        try {
            Path path = Paths.get(absolutePath);
            if (!Files.exists(path)) {
                return null;
            }

            String mimeType = Files.probeContentType(path);
            if (mimeType != null) {
                return mimeType;
            }

            // Fallback for common file types if probeContentType fails
            String extension = getExtension(absolutePath);
            if (extension != null) {
                return switch (extension.toLowerCase()) {
                    case "pdf" -> "application/pdf";
                    case "doc" -> "application/msword";
                    case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "xls" -> "application/vnd.ms-excel";
                    case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    case "ppt" -> "application/vnd.ms-powerpoint";
                    case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                    case "txt" -> "text/plain";
                    case "html", "htm" -> "text/html";
                    case "css" -> "text/css";
                    case "js" -> "application/javascript";
                    case "json" -> "application/json";
                    case "xml" -> "application/xml";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "gif" -> "image/gif";
                    case "bmp" -> "image/bmp";
                    case "svg" -> "image/svg+xml";
                    case "mp3" -> "audio/mpeg";
                    case "wav" -> "audio/wav";
                    case "mp4" -> "video/mp4";
                    case "avi" -> "video/avi";
                    case "mov" -> "video/quicktime";
                    case "zip" -> "application/zip";
                    case "rar" -> "application/x-rar-compressed";
                    case "7z" -> "application/x-7z-compressed";
                    default -> "application/octet-stream";
                };
            }

            return "application/octet-stream";

        } catch (IOException e) {
            // Log the error if you have a logger
            return null;
        }
    }

    private Integer getFileSize(String absolutePath) {
        try {
            Path path = Paths.get(absolutePath);
            if (!Files.exists(path)) {
                return null;
            }

            long fileSize = Files.size(path);

            // Convert to Integer, but handle large files
            if (fileSize > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }

            return (int) fileSize;

        } catch (IOException e) {
            // Log the error if you have a logger
            return null;
        }
    }

    private String getExtension(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return null;
        }

        // Extract filename from path
        Path path = Paths.get(absolutePath);
        String fileName = path.getFileName().toString();

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }

        return null;
    }

}
