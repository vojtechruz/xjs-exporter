package com.vojtechruzicka.xjsexporter;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class HtmlGenerator {

    private final TemplateEngine templateEngine;

    public HtmlGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String generateHtml(Entry entry) {
        Context context = new Context();
        context.setVariable("title", entry.getTitle());
        context.setVariable("body", entry.getBodyHtml());

        return templateEngine.process("entry", context);
    }

    public static void main(String[] args) {

        TemplateEngine templateEngine1 = new ExporterConfiguration().templateEngine();


        HtmlGenerator generator = new HtmlGenerator(templateEngine1);
        Entry entry = new Entry();
        entry.setBodyHtml("<p>Hello world!</p>");
        entry.setTitle("My title");
        System.out.println(generator.generateHtml(entry));
    }
}
