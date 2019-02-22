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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugin.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;


/**
 * Update links to the core API document of Java and Groovy classes.
 */
public class JavaLinkResolver extends LinkResolverBase {

  private static final String JAVA_BASE_URL_FORMAT = "https://docs.oracle.com/javase/%s/docs/api/";
  private static final String JAVA_BASE_URL_FORMAT_FROM_11 = "https://docs.oracle.com/en/java/javase/%s/docs/api/";
  private static final String GROOVY_BASE_URL_FORMAT = "http://docs.groovy-lang.org/%s/html/api/";

  private final String javaBaseUrl;
  private final String groovyBaseUrl;

  private Map<String, String> fullClassNameToLink;
  private Set<String> fullClassNames;

  public JavaLinkResolver(Log log, File outputDir, String javaVersion, String groovyVersion) {
    super(log, outputDir);
    if (Integer.parseInt(javaVersion) >= 11) {
      this.javaBaseUrl = String.format(JAVA_BASE_URL_FORMAT_FROM_11, javaVersion);
    } else {
      this.javaBaseUrl = String.format(JAVA_BASE_URL_FORMAT, javaVersion);
    }
    this.groovyBaseUrl = String.format(GROOVY_BASE_URL_FORMAT, groovyVersion);
  }


  public void update() throws IOException {

    prepare();

    // Update javadoc links
    Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

        log.info(String.format("updating link %s", file.toString()));

        Document document = Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name());
        replaceTextNodes(document.select("body dd"));
        replaceTextNodes(document.select("body pre"));
        replaceTextNodes(document.select("body code"));
        replaceTextNodes(document.select("body code strong"));
        replaceTextNodes(document.select("body h4"));
        Files.write(file, document.outerHtml().getBytes(StandardCharsets.UTF_8));

        log.info(String.format("updated link %s", file.toString()));

        return super.visitFile(file, attrs);
      }
    });
  }


  private void prepare() {
    Set<Class<?>> javaClasses = new Reflections(
        "java.", new SubTypesScanner(false)).getSubTypesOf(Object.class);
    Map<String, String> javaClassNameToLink = javaClasses.stream()
        .collect(Collectors.toMap(
            Class::getName, c -> javaBaseUrl + c.getName().replace(".", "/") + ".html"));

    Set<Class<?>> groovyClasses = new Reflections(
        "groovy.", new SubTypesScanner(false)).getSubTypesOf(Object.class);
    Map<String, String> groovyClassNameToLink = groovyClasses.stream()
        .collect(Collectors.toMap(
            Class::getName, c -> groovyBaseUrl + c.getName().replace(".", "/") + ".html"));

    fullClassNameToLink = Stream.of(javaClassNameToLink, groovyClassNameToLink)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    fullClassNames = fullClassNameToLink.keySet();
  }

  private void replaceTextNodes(Elements elements) {
    if (elements != null) {
      elements.forEach(e -> replaceTextNode(e));
    }
  }

  private void replaceTextNode(Element element) {
    // Clone the element without its children to prevent ConcurrentModificationException
    Element newElement = element.clone().empty();

    for (int i = 0; i < element.childNodeSize(); ++i) {
      // If child node is text, convert class name texts to linked texts.
      if (element.childNode(i) instanceof TextNode) {
        TextNode tn = (TextNode) element.childNode(i);
        splitText(tn.getWholeText()).forEach(newElement::appendChild);
      } else {
        newElement.appendChild(element.childNode(i).clone());
      }
    }
    // Replace original element with the cloned element
    element.replaceWith(newElement);
  }

  private List<Node> splitText(String str) {
    List<Integer> indices = new ArrayList<>();
    // Create indices that split the string with class names

    indices.addAll(getIndicesOf(str, fullClassNames));

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
            .attr("href", fullClassNameToLink.get(s))
            .text(toSimpleClassName(s)));
      } else {
        nodes.add(new TextNode(s, ""));
      }
    });

    return nodes;
  }
}
