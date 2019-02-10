package com.kota65535;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class LinkUpdater {

  private Log log;

  private File outputDir;

  public LinkUpdater(Log log, File outputDir) {
    this.log = log;
    this.outputDir = outputDir;
  }


  public void update() throws IOException {

    Map<String, String> map = new HashMap<>();

    Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Copy only groovydoc file (with class name)
        if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
          return super.visitFile(file, attrs);
        }

        String qualifiedName = getQualifiedName(file.toFile());
        String htmlLink = qualifiedName.replace(".", "/") + ".html";
        String className = getClassName(file.toFile());
        map.put(qualifiedName, htmlLink);
        map.put(className, htmlLink);
        return super.visitFile(file, attrs);
      }
    });

    Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Copy only groovydoc file (with class name)
        if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
          return super.visitFile(file, attrs);
        }

        log.info(String.format("updating link %s", file.toString()));

        Document document = Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name());

        Element div = document.select("div.contentContainer").first();
        if (div == null) {
          return super.visitFile(file, attrs);
        }

        String html = div.html();
        for (Map.Entry<String, String> e: map.entrySet()) {
          String relRoot = file.getParent().relativize(outputDir.toPath()).toString()
              .replace(File.separator, "/");
          String link = relRoot + "/" + e.getValue();
          html = html.replace(e.getKey(), String.format("<a href=\"%s\">%s</a>", link, e.getKey()));
        }
        div.html(html);
        Files.write(file, document.outerHtml().getBytes(StandardCharsets.UTF_8));

        log.info(String.format("updated link %s", file.toString()));

        return super.visitFile(file, attrs);
      }
    });
  }

  private String getQualifiedName(File target) {
    return FilenameUtils.removeExtension(target.toString())
        .replace(outputDir.toString(), "")
        .replace(File.separator, ".")
        .substring(1);
  }

  private String getClassName(File target) {
    return FilenameUtils.getBaseName(target.toString());
  }
}

