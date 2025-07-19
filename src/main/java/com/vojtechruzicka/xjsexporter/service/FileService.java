package com.vojtechruzicka.xjsexporter.service;

import com.github.slugify.Slugify;
import com.vojtechruzicka.xjsexporter.model.Entry;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

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

        String title = "";

        String entryTitle = entry.title();
        if(StringUtils.isNotBlank(entryTitle)) {
            title = entryTitle.substring(0, Math.min(entryTitle.length(), 100));
        }

        String name = String.join("_", entry.created().toLocalDate().toString(),  title , entry.id());

        return filenameSanitizer.slugify(name);
    }
}
