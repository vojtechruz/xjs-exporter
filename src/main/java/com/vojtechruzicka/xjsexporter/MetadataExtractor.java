package com.vojtechruzicka.xjsexporter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class MetadataExtractor {

    public Metadata extractMetadata(String filePath) throws IOException {

        File inputFile = new File(filePath);
        Document doc = Jsoup.parse(inputFile, "UTF-8", "", Parser.xmlParser());

        Map<String, Person> persons = getPersons(doc);
        Map<String, Category> categories = getCategories(doc);
        Map<String, Attachment> attachments = getAttachments(doc);

        return new Metadata(persons, categories, attachments);
    }

    private Map<String, Attachment> getAttachments(Document doc) {

        Map<String, Attachment> attachments = new HashMap<>();

        doc.select("attachments > attachment").forEach(attachmentElement -> {
            String id = attachmentElement.attr("id");
            Element locationElement = attachmentElement.selectFirst("location");

            String location = locationElement != null ? locationElement.text() : null;

            Attachment attachment = new Attachment(id, location);
            attachments.put(id, attachment);
        });

        return attachments;
    }

    private Map<String, Person> getPersons(Document doc) {

        Map<String, Person> persons = new HashMap<>();

        doc.select("people > person").forEach(personElement -> {
            String id = personElement.attr("id");
            Element firstNameElement = personElement.selectFirst("first-name");
            Element lastNameElement = personElement.selectFirst("last-name");
            Element nicknameElement = personElement.selectFirst("nick-name");

            String firstName = firstNameElement != null ? firstNameElement.text() : null;
            String lastName = lastNameElement != null ? lastNameElement.text() : null;
            String nickname = nicknameElement != null ? nicknameElement.text() : null;

            Person person = new Person(id, firstName, lastName, nickname);
            persons.put(id, person);
        });

        return persons;
    }

    private Map<String, Category> getCategories(Document doc) {

        Map<String, Category> categories = new HashMap<>();

        doc.select("categories > category").forEach(classElement -> {
            String id = classElement.attr("id");
            Element titleElement = classElement.selectFirst("title");

            String title = titleElement != null ? titleElement.text() : null;

            Category category = new Category(id, title);
            categories.put(id, category);
        });

        return categories;
    }
    
    

    public static void main(String[] args) throws IOException {
        MetadataExtractor extractor = new MetadataExtractor();
        extractor.extractMetadata("C:\\Users\\vojte\\Dropbox\\_Archiv\\Denik\\XJS\\Deník\\journal.xjn");
    }

}
