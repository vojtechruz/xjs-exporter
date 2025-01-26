package com.vojtechruzicka.xjsexporter;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

@Service
public class HtmlGenerator {

    private final TemplateEngine templateEngine;

    public HtmlGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public static void main(String[] args) {

        TemplateEngine templateEngine = new ExporterConfiguration().templateEngine();

        HtmlGenerator generator = new HtmlGenerator(templateEngine);

        String html = generator.generateHtml("My Title", "<h1>Hello world</h1>", List.of("CAT1", "CAT2"), List.of("PERSON1", "PERSON2", "PERSON3"), List.of(new Attachment("C:\\temp\\file.txt","ATACHMENT1","temp\\file.txt")));
        System.out.println(html);
    }

    public String generateHtml(String title, String htmlBody, List<String> categories, List<String> persons, List<Attachment> attachments) {
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("body", htmlBody);
        context.setVariable("categories", categories);
        context.setVariable("persons", persons);
        context.setVariable("attachments", attachments);

        return templateEngine.process("entry", context);
    }
}
