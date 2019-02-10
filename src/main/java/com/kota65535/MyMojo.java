package com.kota65535;


import com.google.common.io.Resources;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Goal which touches a timestamp file.
 */
@Mojo(name = "touch", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class MyMojo extends AbstractMojo {

  private static final String PACKAGE_SUMMARY_NAME = "package-summary.html";

  private static final String PACKAGE_FRAME_NAME = "package-frame.html";

  @Parameter(property = "javadocDir", required = true)
  private File javadocDir;

  @Parameter(property = "groovydocDir", required = true)
  private File groovydocDir;

  @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
  private File outputDir;

  private Template packageSummaryItemTemplate;

  private Template packageFrameItemTemplate;

  public MyMojo() {
    try {
      packageSummaryItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageSummaryItem.html.mustache").toURI()))));
      packageFrameItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageFrameItem.html.mustache").toURI()))));
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to initialize", e);
    }
  }

  public void execute() throws MojoExecutionException {
    File f = outputDir;

    // Initialize outputDir
    if (f.exists()) {
      try {
        FileUtils.deleteDirectory(f);

      } catch (IOException e) {
        throw new MojoExecutionException("Failed to delete outputDir", e);
      }
    }
    f.mkdirs();

    // Copy all javadoc files
    try {
      FileUtils.copyDirectoryStructure(javadocDir, outputDir);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy javadoc", e);
    }

    // Copy groovydoc file if not exists
    try {
      Files.walkFileTree(groovydocDir.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          // If file name is not like a class name, do nothing.
          if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
            return super.visitFile(file, attrs);
          }

          // If this file does not exist in javadoc, copy it
          File destFile = new File(
              file.toString().replace(groovydocDir.toString(), outputDir.toString()));
          if (!destFile.exists()) {
            if (!destFile.getParentFile().exists()) {
              destFile.getParentFile().mkdirs();
            }
            FileUtils.copyFile(file.toFile(), destFile);
            getLog().info(String.format("copied %s -> %s", file.toString(), destFile));

            File packageSummaryHtml = new File(destFile.getParent(), PACKAGE_SUMMARY_NAME);
            if (packageSummaryHtml.exists()) {
              updatePackageSummary(packageSummaryHtml, file.toFile());
            }

            File packageFrameHtml = new File(destFile.getParent(), PACKAGE_FRAME_NAME);
            if (packageFrameHtml.exists()) {
              updatePackageFrame(packageFrameHtml, file.toFile());
            }

          }
          return super.visitFile(file, attrs);
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy groovdoc", e);
    }
  }

  private void updatePackageSummary(File packageSummary, File target) throws IOException {
    Document packageSummaryDoc = Jsoup.parse(packageSummary, StandardCharsets.UTF_8.name());

    // Get last row element of the table
    Element lastRow = packageSummaryDoc.select("table[class=typeSummary]")
        .select("tbody").get(1)
        .select("tr").last();

    // Determine the class of the new table item
    String lastRowClass = lastRow.attr("class");
    String rowClass = lastRowClass.equals("rowColor") ? "altColor" : "rowColor";

    // Create table item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", String.format("./%s", target.getName()));
    context.put("qualifiedClassName", FilenameUtils.getBaseName(target.toString()));
    context.put("className", FilenameUtils.getBaseName(target.toString()));
    context.put("rowClass", rowClass);
    String rendered = packageSummaryItemTemplate.execute(context);

    // Add item to the table
    // TODO: sort elements
    lastRow.after(rendered);

    FileUtils.fileWrite(packageSummary, packageSummaryDoc.outerHtml());

    getLog().info(String.format("updated %s", packageSummary));
  }

  private void updatePackageFrame(File packageFrame, File target) throws IOException {
    Document packageFrameDoc = Jsoup.parse(packageFrame, StandardCharsets.UTF_8.name());
    Document targetDoc = Jsoup.parse(target, StandardCharsets.UTF_8.name());

    // Get class type of target
    String type = targetDoc.select("h2").text();
    String sectionTitle = Arrays.stream(ClassType.values())
        .filter(t -> type.contains(t.getName()))
        .map(ClassType::getSectionTitle)
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException(String.format("Unknown class type %s", type)));

    Element indexContainer = packageFrameDoc.select("div[class=indexContainer").first();
    Element targetSection = indexContainer.select(String.format("ul[title=%s]", sectionTitle))
        .first();

    // Create section of the class type if not exists
    if (targetSection == null) {
      targetSection = indexContainer
          .append(String.format("<h2 title=\"%s\">%s</h2>", sectionTitle, sectionTitle));
    }

    // Create list item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", String.format("./%s", target.getName()));
    context.put("qualifiedClassName", FilenameUtils.getBaseName(target.toString()));
    context.put("className", FilenameUtils.getBaseName(target.toString()));
    String rendered = packageFrameItemTemplate.execute(context);

    // Add item to the section
    // TODO: sort elements
    targetSection.append(rendered);

    FileUtils.fileWrite(packageFrame, packageFrameDoc.outerHtml());

    getLog().info(String.format("updated %s", packageFrame));
  }
}
