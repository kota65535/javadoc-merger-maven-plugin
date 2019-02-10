package com.kota65535;

import java.util.Arrays;
import java.util.NoSuchElementException;

public enum ClassType {
  CLASS("Class", "Classes"),
  INTERFACE("Interface", "Interfaces"),
  ANNOTATION_TYPE("Annotation Type", "Annotation Types"),
  TRAIT("Trait", "Traits"),
  ;

  private String name;
  private String sectionTitle;

  ClassType(String name, String sectionTitle) {
    this.name = name;
    this.sectionTitle = sectionTitle;
  }

  public String getName() {
    return name;
  }

  public String getSectionTitle() {
    return sectionTitle;
  }

  public static ClassType fromName(String name) {
    return Arrays.stream(values())
        .filter(e -> e.getName().equals(name))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException(String.format("Unknown class type %s", name)));
  }
}
