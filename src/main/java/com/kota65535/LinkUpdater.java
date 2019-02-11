package com.kota65535;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;


/**
 * Update javadoc links failing to point groovy class.
 */
public class LinkUpdater {

  private Log log;

  private File outputDir;

  private Map<String, String> map = new HashMap<>();

  public LinkUpdater(Log log, File outputDir) {
    this.log = log;
    this.outputDir = outputDir;
  }


  public void update() throws IOException {

    // Create map with a class name as key, and a link text as value
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


    // Update javadoc links
    Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Copy only groovydoc file (with class name)
        if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
          return super.visitFile(file, attrs);
        }

        log.info(String.format("updating link %s", file.toString()));

        Document document = Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name());
        String prefix = file.getParent().relativize(outputDir.toPath()).toString()
            .replace(File.separator, "/") + "/";
        replaceTextNodes(document.select("div.description dd"), map, prefix);
        replaceTextNodes(document.select("div.description pre"), map, prefix);
        replaceTextNodes(document.select("li.blockList code"), map, prefix);
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

  private void replaceTextNodes(Elements elements, Map<String, String> textToLink, String linkPrefix) {
    if (elements != null) {
      elements.forEach(e -> replaceTextNode(e, textToLink, linkPrefix));
    }
  }

  private void replaceTextNode(Element element, Map<String, String> textToLink, String linkPrefix) {
    // Clone the element without its children to prevent ConcurrentModificationException
    Element newElement = element.clone().empty();

    for (int i = 0 ; i < element.childNodeSize() ; ++i) {
      // If child node is text, convert class name texts to linked texts.
      if (element.childNode(i) instanceof TextNode) {
        TextNode tn = (TextNode) element.childNode(i);
        splitText(tn.getWholeText(), textToLink, linkPrefix).forEach(newElement::appendChild);
      } else {
        newElement.appendChild(element.childNode(i).clone());
      }
    }
    // Replace original element with the cloned element
    element.replaceWith(newElement);
  }

  private List<Node> splitText(String str, Map<String, String> textToLink, String linkPrefix) {
    List<Integer> indices = new ArrayList<>();
    List<String> classNames = new ArrayList<>();
    // Create indices that split the string with class names
    for (Map.Entry<String, String> e: textToLink.entrySet()) {
      int idx = str.indexOf(e.getKey());
      if (idx >= 0) {
        indices.add(idx);
        indices.add(idx + e.getKey().length());
        classNames.add(str.substring(idx, idx + e.getKey().length()));
      }
    }

    // Get split strings
    List<String> tokens = new ArrayList<>();
    indices.add(0);
    indices.add(str.length());
    indices.sort(Comparator.naturalOrder());
    for (int i = 0 ; i < indices.size() - 1 ; ++i) {
      tokens.add(str.substring(indices.get(i), indices.get(i+1)));
    }

    // Wrap a class name text with anchor to enable link
    List<Node> nodes = new ArrayList<>();
    tokens.forEach(s -> {
      if (classNames.contains(s)) {
        nodes.add(new Element(Tag.valueOf("a"), "")
            .attr("href", linkPrefix + textToLink.get(s))
            .text(s));
      } else {
        nodes.add(new TextNode(s, ""));
      }
    });

    return nodes;
  }
}
