package com.kota65535;


import java.io.File;
import java.io.IOException;
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

    if (!f.exists()) {
      f.mkdirs();
    }

    // copy all javadoc files
    try {
      FileUtils.copyDirectory(javadocDir, outputDir);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy javadoc", e);
    }
  }
}
