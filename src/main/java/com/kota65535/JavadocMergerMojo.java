package com.kota65535;


import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal which merges Javadoc and Groovydoc.
 */
@Mojo(name = "merge", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class JavadocMergerMojo extends AbstractMojo {

  @Parameter(property = "javadocDir", required = true)
  private File javadocDir;

  @Parameter(property = "groovydocDir", required = true)
  private File groovydocDir;

  @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
  private File outputDir;

  private JavadocUpdater javadocUpdater;


  public void execute() throws MojoExecutionException {
    javadocUpdater = new JavadocUpdater(getLog(), outputDir);

    // Initialize outputDir
    if (outputDir.exists()) {
      try {
        FileUtils.deleteDirectory(outputDir);

      } catch (IOException e) {
        throw new MojoExecutionException("Failed to delete outputDir", e);
      }
    }
    outputDir.mkdirs();

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

          // Do nothing if javadoc already exists
          File destFile = new File(
              file.toString().replace(groovydocDir.toString(), outputDir.toString()));
          if (destFile.exists()) {
            return super.visitFile(file, attrs);
          }

          // Copy only groovydoc file (with class name)
          if (!Character.isUpperCase(file.getFileName().toString().charAt(0))) {
            return super.visitFile(file, attrs);
          }

          // Copy groovydoc
          if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
          }
          FileUtils.copyFile(file.toFile(), destFile);
          getLog().info(String.format("copied %s -> %s", file.toString(), destFile));

          // Update javadoc
          javadocUpdater.update(destFile);

          return super.visitFile(file, attrs);
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy groovdoc", e);
    }
  }
}
