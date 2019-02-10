package com.kota65535;


import java.io.File;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.junit.Test;

public class JavadocMergerMojoTest extends AbstractMojoTestCase {

  private static final String BUILD_DIR = "target/test-classes/project-to-test/target";

  /**
   * @see junit.framework.TestCase#setUp()
   */
  protected void setUp() throws Exception {
    // required for mojo lookups to work
    super.setUp();
  }


  /**
   * @throws Exception if any
   */
  @Test
  public void testSomething()
      throws Exception {
    File pom = getTestFile("src/test/resources/project-to-test/pom.xml");
    assertNotNull(pom);
    assertTrue(pom.exists());

    JavadocMergerMojo myMojo = (JavadocMergerMojo) lookupMojo("merge", pom);

    File javaDocDir = getTestFile(BUILD_DIR, "apidocs");
    File groovydocDir = getTestFile(BUILD_DIR, "gapidocs");
    File outputDir = getTestFile(BUILD_DIR, "mergedDocs");

    setVariableValueToObject(myMojo, "javadocDir", javaDocDir);
    setVariableValueToObject(myMojo, "groovydocDir", groovydocDir);
    setVariableValueToObject(myMojo, "outputDir", outputDir);

    assertNotNull(myMojo);

    myMojo.execute();

    assertNotNull(outputDir);
    assertTrue(outputDir.exists());
  }

  /**
   * Do not need the MojoRule.
   */
  @WithoutMojo
  @Test
  public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
    assertTrue(true);
  }

}

