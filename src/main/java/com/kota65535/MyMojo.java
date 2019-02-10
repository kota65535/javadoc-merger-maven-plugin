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
 * Goal which touches a timestamp file.
 */
@Mojo(name = "touch", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class MyMojo extends AbstractMojo {

  @Parameter(property = "javadocDir", required = true)
  private File javadocDir;

  @Parameter(property = "groovydocDir", required = true)
  private File groovydocDir;

  @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
  private File outputDir;


  public void execute() throws MojoExecutionException {
    File f = outputDir;

    // Initialize outputDir
    if (f.exists()) {
      try {
        FileUtils.deleteDirectory(f);

      } catch(IOException e) {
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
          String strPath = file.toString().replace(groovydocDir.toString(), outputDir.toString());
          File copyDestFile = new File(strPath);
          if (!copyDestFile.exists()) {
            if (!copyDestFile.getParentFile().exists()) {
              copyDestFile.getParentFile().mkdirs();
            }
            FileUtils.copyFile(file.toFile(), copyDestFile);
            System.out.println(String.format("copied %s -> %s", file.toString(), copyDestFile));
          }
          return super.visitFile(file, attrs);
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy groovdoc", e);
    }
  }
}
