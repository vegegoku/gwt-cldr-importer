/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.gwtproject.tools.cldr;

import com.squareup.javapoet.AnnotationSpec;
import org.gwtproject.i18n.shared.GwtLocale;
import org.unicode.cldr.util.CLDRFile;

import javax.annotation.Generated;
import java.io.*;
import java.util.*;

import static java.util.Objects.isNull;

/**
 * Base class for CLDR processors that generate GWT i18n resources.
 */
public abstract class Processor {

  protected static final String I18N_PACKAGE_PATH = "src/main/java/org/gwtproject/i18n/";

  protected static <T> String join(String joiner, Iterable<T> objects) {
    StringBuilder buf = new StringBuilder();
    for (Object obj : objects) {
      if (buf.length() > 0) {
        buf.append(joiner);
      }
      buf.append(obj.toString());
    }
    return buf.toString();
  }

  protected static String localeSuffix(GwtLocale locale) {
    return (locale.isDefault() ? "" : "_") + locale.getAsString();
  }

  protected static String localeSuffix(GwtLocale locale, String defaultSplitter) {
    if(isNull(locale)){
      return "_";
    }
    return (locale.isDefault() ? defaultSplitter : "_") + locale.getAsString();
  }

  /**
   * @param value
   * @return value with all quotes escaped
   */
  protected static String quote(String value) {
    return value.replace("\"", "\\\"");
  }

  protected final InputFactory cldrFactory;

  protected final LocaleData localeData;

  protected final File outputDir;

  private boolean useOverride;

  /**
   * Initialize the shared portion of a Processor.
   *
   * @param outputDir output directory for created files
   * @param cldrFactory CLDR factory used to create new CLDRFile instances
   * @param localeData LocaleData instance to collect data from CLDR files
   */
  protected Processor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
    this.outputDir = outputDir;
    this.cldrFactory = cldrFactory;
    this.localeData = localeData;
    useOverride = true;
  }

  /**
   * Execute this processor.
   *
   * It will call loadData, cleanupData, writeOutputFiles, and then reset on its
   * localeData instance.
   *
   * @throws IOException
   */
  public final void run() throws IOException {
    try {
      loadData();
      cleanupData();
      writeOutputFiles();
    } finally {
      localeData.reset();
    }
  }

  /**
   * Override hook for subclasses to implement any cleanup needed, such as
   * removing values which duplicate those from ancestors.
   */
  protected void cleanupData() {
    // do nothing by default
  }

  protected PrintWriter createOutputFile(String suffix) throws IOException, FileNotFoundException {
    return createOutputFile(I18N_PACKAGE_PATH, suffix);
  }

  protected PrintWriter createOutputFile(String prefix, String suffix) throws IOException,
      FileNotFoundException {
    PrintWriter pw;
    File f = new File(outputDir, prefix + suffix);
    File parent = f.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    f.createNewFile();
    pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),
        "UTF-8")), false);
    return pw;
  }

  protected void generateIntMethod(PrintWriter pw, String category, GwtLocale locale, String key,
      String method) {
    String value = localeData.getEntry(category, locale, key);
    if (value != null) {
      pw.println();
      if (useOverride) {
        pw.println("  @Override");
      }
      pw.println("  public int " + method + "() {");
      pw.println("    return " + value + ";");
      pw.println("  }");
    }
  }

  protected void generateStringMethod(PrintWriter pw, String category, GwtLocale locale,
      String key, String method) {
    String value = localeData.getEntry(category, locale, key);
    generateStringValue(pw, method, value);
  }

  protected void generateStringValue(PrintWriter pw, String method, String value) {
    if (value != null) {
      pw.println();
      if (useOverride) {
        pw.println("  @Override");
      }
      pw.println("  public String " + method + "() {");
      pw.println("    return \"" + quote(value) + "\";");
      pw.println("  }");
    }
  }

  /**
   * @return true if generated methods should use @Override.
   */
  protected boolean getOverrides() {
    return useOverride;
  }

  /**
   * Load data needed by this processor.
   *
   * @throws IOException
   */
  protected abstract void loadData() throws IOException;

  protected void printHeader(PrintWriter pw) {
    printJavaHeader(pw);
  }

  protected void printJavaHeader(PrintWriter pw) {
    int year = 2012;
    pw.println("/*");
    pw.println(" * Copyright " + year + " Google Inc.");
    pw.println(" * ");
    pw.println(" * Licensed under the Apache License, Version 2.0 (the "
        + "\"License\"); you may not");
    pw.println(" * use this file except in compliance with the License. You "
        + "may obtain a copy of");
    pw.println(" * the License at");
    pw.println(" * ");
    pw.println(" * http://www.apache.org/licenses/LICENSE-2.0");
    pw.println(" * ");
    pw.println(" * Unless required by applicable law or agreed to in writing, " + "software");
    pw.println(" * distributed under the License is distributed on an \"AS "
        + "IS\" BASIS, WITHOUT");
    pw.println(" * WARRANTIES OR CONDITIONS OF ANY KIND, either express or " + "implied. See the");
    pw.println(" * License for the specific language governing permissions and "
        + "limitations under");
    pw.println(" * the License.");
    pw.println(" */");
  }

  protected void printPropertiesHeader(PrintWriter pw) {
    int year = 2012;
    pw.println("# Copyright " + year + " Google Inc.");
    pw.println("# ");
    pw.println("# Licensed under the Apache License, Version 2.0 (the "
        + "\"License\"); you may not");
    pw.println("# use this file except in compliance with the License. You "
        + "may obtain a copy of");
    pw.println("# the License at");
    pw.println("# ");
    pw.println("# http://www.apache.org/licenses/LICENSE-2.0");
    pw.println("# ");
    pw.println("# Unless required by applicable law or agreed to in writing, " + "software");
    pw.println("# distributed under the License is distributed on an \"AS "
        + "IS\" BASIS, WITHOUT");
    pw.println("# WARRANTIES OR CONDITIONS OF ANY KIND, either express or " + "implied. See the");
    pw.println("# License for the specific language governing permissions and "
        + "limitations under");
    pw.println("# the License.");
  }

  protected void printVersion(PrintWriter pw, GwtLocale locale, String prefix) {
    pw.println(prefix + "DO NOT EDIT - GENERATED FROM CLDR DATA");
  }

  protected static AnnotationSpec generatedAnnotation(Class<? extends Processor> processor) {
    return AnnotationSpec.builder(Generated.class)
            .addMember("value", "\""+"gwt-cldr-importer : "+processor.getCanonicalName()+", CLDR version : "+CldrVersion.value+"\"")
            .build();
  }

  /**
   * Set whether method definitions should use @Override.
   *
   * @param useOverride
   */
  protected void setOverrides(boolean useOverride) {
    this.useOverride = useOverride;
  }

  /**
   * Write output files produced by this processor.
   *
   * @throws IOException
   */
  protected abstract void writeOutputFiles() throws IOException;
}
