package com.kota65535.resolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;


/**
 * Update javadoc links failing to point groovy class.
 */
public abstract class LinkResolverBase {

  protected Log log;

  protected File outputDir;

  public LinkResolverBase(Log log, File outputDir) {
    this.log = log;
    this.outputDir = outputDir;
  }


  abstract public void update() throws IOException;


  protected List<Integer> getIndicesOf(String target, Set<String> candidates) {
    List<Integer> indices = new ArrayList<>();
    for (String cnd : candidates) {
      List<Integer> idx = getIndicesOf(target, cnd);
      idx.forEach(i -> {
        indices.add(i);
        indices.add(i + cnd.length());
      });
    }
    return indices;
  }

  protected List<Integer> getIndicesOf(String target, String substr) {
    List<Integer> indices = new ArrayList<>();
    int i = -1;
    do {
      i = target.indexOf(substr, i + 1);
      if (i >= 0) {
        indices.add(i);
      }
    } while (i >= 0);
    return indices;
  }

  protected String toSimpleClassName(String fullClassName) {
    String[] tokens = fullClassName.split("\\.");
    return tokens[tokens.length - 1];
  }
}
