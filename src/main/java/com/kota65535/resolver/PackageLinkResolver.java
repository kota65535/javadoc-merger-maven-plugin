package com.kota65535.resolver;

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
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;


/**
 * Update Javadoc -> Groovydoc links and vice versa.
 */
public class PackageLinkResolver extends LinkResolverBase {

  private Map<String, String> fullClassNameToLink = new HashMap<>();
  private Map<String, String> classNameToLink = new HashMap<>();
  private Set<String> fullClassNames;
  private Set<String> classNames;

  public PackageLinkResolver(Log log, File outputDir) {
    super(log, outputDir);
  }


  public void update() throws IOException {

    prepare();

    // Update javadoc links
    Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Update only class javadoc
        if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
          return super.visitFile(file, attrs);
        }

        log.info(String.format("updating link %s", file.toString()));

        Document document = Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name());
        String prefix = file.getParent().relativize(outputDir.toPath()).toString()
            .replace(File.separator, "/") + "/";
        replaceTextNodes(document.select("body dd"), prefix);
        replaceTextNodes(document.select("body pre"), prefix);
        replaceTextNodes(document.select("body code"), prefix);
        replaceTextNodes(document.select("body code strong"), prefix);
        replaceTextNodes(document.select("body h4"), prefix);
        Files.write(file, document.outerHtml().getBytes(StandardCharsets.UTF_8));

        log.info(String.format("updated link %s", file.toString()));

        return super.visitFile(file, attrs);
      }
    });
  }


  private void prepare() throws IOException {
    Document document = Jsoup.parse(
        new File(outputDir, "allclasses-noframe.html"), StandardCharsets.UTF_8.name());
    fullClassNameToLink = document.select("li a").stream()
        .collect(Collectors.toMap(
            a -> a.attr("title").replace("class in ", "") + "." + a.text().replace(".", "$"),
            a -> a.attr("href")));
    classNameToLink = new HashMap<>();
    fullClassNameToLink.forEach((k, v) -> {
      String key = toSimpleClassName(k);
      if (classNameToLink.containsKey(key)) {
        log.warn(String.format("duplicated simple class name '%s'.", key));
      } else {
        classNameToLink.put(toSimpleClassName(key), v);
      }
    });
    fullClassNames = fullClassNameToLink.keySet();
    classNames = classNameToLink.keySet();
  }

  private void replaceTextNodes(Elements elements, String linkPrefix) {
    if (elements != null) {
      elements.forEach(e -> replaceTextNode(e, linkPrefix));
    }
  }

  private void replaceTextNode(Element element, String linkPrefix) {
    // Clone the element without its children to prevent ConcurrentModificationException
    Element newElement = element.clone().empty();

    for (int i = 0; i < element.childNodeSize(); ++i) {
      // If child node is text, convert class name texts to linked texts.
      if (element.childNode(i) instanceof TextNode) {
        TextNode tn = (TextNode) element.childNode(i);
        splitText(tn.getWholeText(), linkPrefix).forEach(newElement::appendChild);
      } else {
        newElement.appendChild(element.childNode(i).clone());
      }
    }
    // Replace original element with the cloned element
    element.replaceWith(newElement);
  }

  private List<Node> splitText(String str, String linkPrefix) {
    List<Integer> indices = new ArrayList<>();
    // Create indices that split the string with class names

    indices.addAll(getIndicesOf(str, fullClassNames));
    if (indices.size() == 0) {
      indices.addAll(getIndicesOf(str, classNames));
    }

    // Get split strings
    List<String> tokens = new ArrayList<>();
    indices.add(0);
    indices.add(str.length());
    indices.sort(Comparator.naturalOrder());
    for (int i = 0; i < indices.size() - 1; ++i) {
      tokens.add(str.substring(indices.get(i), indices.get(i + 1)));
    }

    // Wrap a class name text with anchor to enable link
    List<Node> nodes = new ArrayList<>();
    tokens.forEach(s -> {
      if (fullClassNames.contains(s)) {
        nodes.add(new Element(Tag.valueOf("a"), "")
            .attr("href", linkPrefix + (fullClassNameToLink.get(s)))
            .text(toSimpleClassName(s)));
      } else if (classNames.contains(s)) {
        nodes.add(new Element(Tag.valueOf("a"), "")
            .attr("href", linkPrefix + (classNameToLink.get(s)))
            .text(s));
      } else {
        nodes.add(new TextNode(s, ""));
      }
    });

    return nodes;
  }
}
