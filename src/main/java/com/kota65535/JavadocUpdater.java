package com.kota65535;

import com.google.common.io.Resources;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class JavadocUpdater {
  private static final String PACKAGE_SUMMARY = "package-summary.html";

  private static final String PACKAGE_FRAME = "package-frame.html";

  private static final String OVERVIEW_SUMMARY = "overview-summary.html";

  private static final String OVERVIEW_FRAME = "overview-frame.html";

  private static final String ALL_CLASSES_FRAME = "allclasses-frame.html";

  private static final String ALL_CLASSES_NOFRAME = "allclasses-noframe.html";

  private Template packageSummaryTemplate;

  private Template packageFrameTemplate;

  private Template packageSummaryItemTemplate;

  private Template packageFrameItemTemplate;

  private Template overviewSummaryItemTemplate;

  private Template overviewFrameItemTemplate;

  private Template allClassesItemTemplate;

  private Log log;

  private File outputDir;


  public JavadocUpdater(Log log) {
    this.log = log;
    try {
      packageSummaryTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageSummary.html.mustache").toURI()))));
      packageFrameTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageFrame.html.mustache").toURI()))));
      packageSummaryItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageSummaryItem.html.mustache").toURI()))));
      packageFrameItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("packageFrameItem.html.mustache").toURI()))));
      overviewSummaryItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("overviewSummaryItem.html.mustache").toURI()))));
      overviewFrameItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("overviewFrameItem.html.mustache").toURI()))));
      allClassesItemTemplate = Mustache.compiler().compile(
          new String(Files.readAllBytes(
              Paths.get(Resources.getResource("allClassesItem.html.mustache").toURI()))));
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to initialize", e);
    }
  }


  public void update(File destFile, File outputDir) throws IOException {
    this.outputDir = outputDir;

    boolean shouldCreatePackage = false;

    // Create package-summary if not exists
    File packageSummaryHtml = new File(destFile.getParent(), PACKAGE_SUMMARY);
    if (! packageSummaryHtml.exists()) {
      packageSummaryHtml = createPackageSummary(destFile);
      shouldCreatePackage = true;
    }
    // Update package-summary.html
    updatePackageSummary(packageSummaryHtml, destFile);


    // Create package-frame if not exists
    File packageFrameHtml = new File(destFile.getParent(), PACKAGE_FRAME);
    if (! packageFrameHtml.exists()) {
      packageFrameHtml = createPackageFrame(destFile);
      shouldCreatePackage = true;
    }
    // Update package-frame.html
    updatePackageFrame(packageFrameHtml, destFile);


    // Update overview if package is created
    if (shouldCreatePackage) {
      updateOverview(destFile);
    }


    // Update all classes list
    updateAllClasses(destFile);
  }


  private String getQualifiedName(File target) {
    return FilenameUtils.removeExtension(target.toString())
        .replace(outputDir.toString(), "")
        .replace(File.separator, ".")
        .substring(1);
  }


  private String getPackageName(File target) {
    return target.getParent()
        .replace(outputDir.toString(), "")
        .replace(File.separator, ".")
        .substring(1);
  }


  private File createPackageSummary(File target) throws IOException  {
    String packageName = getPackageName(target);

    String relRoot = target.getParentFile().toPath().relativize(outputDir.toPath()).toString()
        .replace(File.separator, "/");

    Map<String, String> context = new HashMap<>();
    context.put("packageName", packageName);
    context.put("rel", relRoot);
    String rendered = packageSummaryTemplate.execute(context);

    File packageSummary = new File(target.getParent(), PACKAGE_SUMMARY);
    FileUtils.fileWrite(packageSummary, rendered);

    log.info(String.format("created %s", packageSummary.toString()));

    return packageSummary;
  }


  private File createPackageFrame(File target) throws IOException  {
    String packageName = getPackageName(target);

    String relRoot = target.getParentFile().toPath().relativize(outputDir.toPath()).toString()
        .replace(File.separator, "/");

    Map<String, String> context = new HashMap<>();
    context.put("packageName", packageName);
    context.put("rel", relRoot);
    String rendered = packageFrameTemplate.execute(context);

    File packageFrame = new File(target.getParent(), PACKAGE_FRAME);
    FileUtils.fileWrite(packageFrame, rendered);

    log.info(String.format("created %s", packageFrame.toString()));
    return packageFrame;
  }


  private void updatePackageSummary(File packageSummary, File target) throws IOException {
    Document packageSummaryDoc = Jsoup.parse(packageSummary, StandardCharsets.UTF_8.name());

    // Get last row element of the table
    Element tableBody = packageSummaryDoc.select("table[class=typeSummary]")
        .select("tbody").get(1);

    // Create table item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", String.format("./%s", target.getName()));
    context.put("qualifiedClassName", FilenameUtils.getBaseName(target.toString()));
    context.put("className", FilenameUtils.getBaseName(target.toString()));
    context.put("rowClass", "rowColor");
    String rendered = packageSummaryItemTemplate.execute(context);

    // Add item to the table
    tableBody.append(rendered);

    // Sort table rows
    Elements trs = tableBody.select("tr");
    trs.sort(Comparator.comparing(o -> o.select("a").first().text()));
    IntStream.range(0, trs.size()).forEach(i ->
        trs.get(i).attr("class", i % 2 == 0 ? "altColor" : "rowColor"));

    tableBody.html(trs.outerHtml());

    FileUtils.fileWrite(packageSummary, packageSummaryDoc.outerHtml());

    log.info(String.format("updated %s", packageSummary));
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

    // Search section of the class type
    Element targetSectionTitle = indexContainer
        .select(String.format("h2[title=%s]", sectionTitle))
        .first();
    Element targetSectionList = indexContainer
        .select(String.format("ul[title=%s]", sectionTitle))
        .first();

    // Create section of the class type if not exists
    if (targetSectionTitle == null) {
      indexContainer.append(String.format("<h2 title=\"%1$s\">%1$s</h2><ul title=\"%1$s\"></ul>", sectionTitle));
      targetSectionList = indexContainer
          .select(String.format("ul[title=%s]", sectionTitle))
          .first();
    }

    // Create list item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", String.format("./%s", target.getName()));
    context.put("qualifiedClassName", FilenameUtils.getBaseName(target.toString()));
    context.put("className", FilenameUtils.getBaseName(target.toString()));
    String rendered = packageFrameItemTemplate.execute(context);

    // Add item to the section
    targetSectionList.append(rendered);

    // Sort list items
    Elements lis = targetSectionList.select("li");
    lis.sort(Comparator.comparing(o -> o.select("a").first().text()));

    targetSectionList.html(lis.outerHtml());

    FileUtils.fileWrite(packageFrame, packageFrameDoc.outerHtml());

    log.info(String.format("updated %s", packageFrame));
  }


  private void updateOverview(File target) throws IOException  {
    String packageName = getPackageName(target);
    updateOverviewFrame(packageName);
    updateOverviewSummary(packageName);
  }


  private void updateOverviewSummary(String packageName) throws IOException {
    File overviewFrame = new File(outputDir, OVERVIEW_SUMMARY);
    Document overviewFrameDoc = Jsoup.parse(overviewFrame, StandardCharsets.UTF_8.name());

    // Get last row element of the table
    Element tableBody = overviewFrameDoc.select("table[class=overviewSummary]")
        .select("tbody").get(1);

    // Determine the class of the new table item
    Element lastRow = tableBody.select("tr").last();
    String rowClass = "altColor";
    if (lastRow != null) {
      String lastRowClass = lastRow.attr("class");
      rowClass = lastRowClass.equals("rowColor") ? "altColor" : "rowColor";
    }

    String htmlLink = packageName.replace(".", "/") + "/package-summary.html";

    // Create table item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", htmlLink);
    context.put("packageName", packageName);
    context.put("rowClass", rowClass);
    String rendered = overviewSummaryItemTemplate.execute(context);

    tableBody.append(rendered);

    // Sort table rows
    Elements trs = tableBody.select("tr");
    trs.sort(Comparator.comparing(o -> o.select("a").first().text()));
    tableBody.html(trs.outerHtml());

    FileUtils.fileWrite(overviewFrame, overviewFrameDoc.outerHtml());

    log.info(String.format("updated %s", overviewFrame.toString()));
  }


  private void updateOverviewFrame(String packageName) throws IOException {
    File overviewFrame = new File(outputDir, OVERVIEW_FRAME);
    Document overviewFrameDoc = Jsoup.parse(overviewFrame, StandardCharsets.UTF_8.name());
    Element packageList = overviewFrameDoc.select("div[class=indexContainer] ul").first();

    String htmlLink = packageName.replace(".", "/") + "/package-frame.html";

    // Create table item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", htmlLink);
    context.put("packageName", packageName);
    String rendered = overviewFrameItemTemplate.execute(context);

    packageList.append(rendered);

    // Sort list items
    Elements lis = packageList.select("li");
    lis.sort(Comparator.comparing(o -> o.select("a").first().text()));

    packageList.html(lis.outerHtml());

    FileUtils.fileWrite(overviewFrame, overviewFrameDoc.outerHtml());

    log.info(String.format("updated %s", overviewFrame.toString()));
  }


  private void updateAllClasses(File target) throws IOException {
    String qualifiedName = getQualifiedName(target);
    updateAllClasses(qualifiedName, ALL_CLASSES_FRAME);
    updateAllClasses(qualifiedName, ALL_CLASSES_NOFRAME);
  }


  private void updateAllClasses(String qualifiedName, String fileName) throws IOException {
    File allClassesFrame = new File(outputDir, fileName);
    Document allClassesDoc = Jsoup.parse(allClassesFrame, StandardCharsets.UTF_8.name());
    Element classList = allClassesDoc.select("div[class=indexContainer] ul").first();

    String htmlLink = qualifiedName.replace(".", "/") + ".html";
    String className = qualifiedName.substring(qualifiedName.lastIndexOf(".") + 1);

    // Create table item
    Map<String, String> context = new HashMap<>();
    context.put("htmlLink", htmlLink);
    context.put("qualifiedName", qualifiedName);
    context.put("className", className);
    String rendered = allClassesItemTemplate.execute(context);

    classList.append(rendered);

    // Sort list items
    Elements lis = classList.select("li");
    lis.sort(Comparator.comparing(o -> o.select("a").first().text()));
    classList.html(lis.outerHtml());

    FileUtils.fileWrite(allClassesFrame, allClassesDoc.outerHtml());

    log.info(String.format("updated %s", allClassesFrame.toString()));
  }


}
