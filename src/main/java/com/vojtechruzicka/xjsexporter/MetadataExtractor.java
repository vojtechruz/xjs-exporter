package com.vojtechruzicka.xjsexporter;

import com.vojtechruzicka.xjsexporter.model.EntryMetadata;
import com.vojtechruzicka.xjsexporter.model.Metadata;
import com.vojtechruzicka.xjsexporter.model.PersonMetadata;
import io.micrometer.common.util.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetadataExtractor {

    public Metadata extractMetadata(String filePath) throws IOException {

        File inputFile = new File(filePath + "journal.xjn");
        Document doc = Jsoup.parse(inputFile, "UTF-8", "", Parser.xmlParser());

        Map<String, EntryMetadata> entries = getEntries(filePath, doc);
        Map<String, PersonMetadata> persons = getPersons(doc);
        Map<String, CategoryMetadata> categories = getCategories(doc);
        Map<String, AttachmentMetadata> attachments = getAttachments(filePath,doc);

        return new Metadata(persons, categories, attachments, entries);
    }

    private Map<String, EntryMetadata> getEntries(String filePath, Document doc) {

        Map<String, EntryMetadata> entries = new HashMap<>();

        doc.select("entries > entry").forEach(entryElement -> {
            String id = entryElement.attr("id");
            String dateCreated = entryElement.attr("date-created");
            Element titleElement = entryElement.selectFirst("title");
            String title = titleElement != null ? titleElement.text() : null;
            LocalDateTime dateTime = StringUtils.isNotBlank(dateCreated) ? LocalDateTime.parse(dateCreated) : null;
            String location = entryElement.select("content > value").text();
            String absoluteLocationPath = filePath != null ? filePath.substring(0, filePath.lastIndexOf(File.separator)) + File.separator + location : null;

            List<String> attachmentIds = entryElement.select("attachment-ids > id").eachText();
            List<String> categoryIds = entryElement.select("category-ids > id").eachText();
            List<String> personIds = entryElement.select("person-ids > id").eachText();

            EntryMetadata entry = new EntryMetadata(id, title, absoluteLocationPath, dateTime, attachmentIds, categoryIds, personIds);

            entries.put(id, entry);
        });

        return entries;
    }

    private Map<String, AttachmentMetadata> getAttachments(String filePath, Document doc) {

        Map<String, AttachmentMetadata> attachments = new HashMap<>();

        doc.select("attachments > attachment").forEach(attachmentElement -> {
            String id = attachmentElement.attr("id");
            Element locationElement = attachmentElement.selectFirst("location");

            String relativeLocation = locationElement != null ? locationElement.text() : null;
            String attachementName = relativeLocation;
            String absoluteSourcePath = filePath != null ? filePath.substring(0, filePath.lastIndexOf(File.separator)) + File.separator + "Attachments" + File.separator + attachementName : null;

            AttachmentMetadata attachment = new AttachmentMetadata(id, absoluteSourcePath, attachementName,relativeLocation);
            attachments.put(id, attachment);
        });

        return attachments;
    }

    private Map<String, PersonMetadata> getPersons(Document doc) {

        Map<String, PersonMetadata> persons = new HashMap<>();

        doc.select("people > person").forEach(personElement -> {
            String id = personElement.attr("id");
            Element firstNameElement = personElement.selectFirst("first-name");
            Element lastNameElement = personElement.selectFirst("last-name");
            Element nicknameElement = personElement.selectFirst("nick-name");

            String firstName = firstNameElement != null ? firstNameElement.text() : null;
            String lastName = lastNameElement != null ? lastNameElement.text() : null;
            String nickname = nicknameElement != null ? nicknameElement.text() : null;

            PersonMetadata personMetadata = new PersonMetadata(id, firstName, lastName, nickname);
            persons.put(id, personMetadata);
        });

        return persons;
    }

    private Map<String, CategoryMetadata> getCategories(Document doc) {

        Map<String, CategoryMetadata> categories = new HashMap<>();

        doc.select("categories > category").forEach(classElement -> {
            String id = classElement.attr("id");
            Element titleElement = classElement.selectFirst("title");

            String title = titleElement != null ? titleElement.text() : null;

            CategoryMetadata category = new CategoryMetadata(id, title);
            categories.put(id, category);
        });

        return categories;
    }
    
    

    public static void main(String[] args) throws IOException {
        MetadataExtractor extractor = new MetadataExtractor();
        Metadata metadata = extractor.extractMetadata("C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\");
        System.out.println(metadata);
    }

}
