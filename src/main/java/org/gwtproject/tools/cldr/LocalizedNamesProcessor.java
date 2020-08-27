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

import org.gwtproject.i18n.shared.GwtLocale;
import org.gwtproject.tools.cldr.RegionLanguageData.RegionPopulation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.CollationKey;
import java.text.Collator;
import java.util.*;

/**
 * Extract localized names from CLDR data.
 */
public class LocalizedNamesProcessor extends Processor {

    static class IndexedName implements Comparable<IndexedName> {

        private final int index;
        private final CollationKey key;

        public IndexedName(Collator collator, int index, String value) {
            this.index = index;
            this.key = collator.getCollationKey(value);
        }

        @Override
        public int compareTo(IndexedName o) {
            return key.compareTo(o.key);
        }

        /**
         * @return index of this name.
         */
        public int getIndex() {
            return index;
        }
    }

    /**
     * Split a list of region codes into an array.
     *
     * @param regionList comma-separated list of region codes
     * @return array of region codes, null if none
     */
    private static String[] getRegionOrder(String regionList) {
        String[] split = null;
        if (regionList != null && regionList.length() > 0) {
            split = regionList.split(",");
        }
        return split;
    }

    private final RegionLanguageData regionLanguageData;

    public LocalizedNamesProcessor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
        super(outputDir, cldrFactory, localeData);
        regionLanguageData = new RegionLanguageData(cldrFactory);
    }

    @Override
    protected void cleanupData() {
        localeData.copyLocaleData("en", "default", "territory", "languages", "scripts", "variants");
        // Generate a sort order before removing duplicates
        for (GwtLocale locale : localeData.getNonEmptyLocales("territory")) {
            // TODO(jat): deal with language population data that has a script
            Map<String, String> map = localeData.getEntries("territory", locale);
            List<String> countryCodes = new ArrayList<String>();
            for (String regionCode : map.keySet()) {
                if (!ZZ.equals(regionCode) && regionCode.length() == 2) {
                    countryCodes.add(regionCode);
                }
            }
            Locale javaLocale =
                    new Locale(locale.getLanguageNotNull(), locale.getRegionNotNull(), locale
                            .getVariantNotNull());
            Collator collator = Collator.getInstance(javaLocale);
            IndexedName[] names = new IndexedName[countryCodes.size()];
            for (int i = 0; i < names.length; ++i) {
                names[i] = new IndexedName(collator, i, map.get(countryCodes.get(i)));
            }
            Arrays.sort(names);
            StringBuilder buf = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < names.length; ++i) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }
                buf.append(countryCodes.get(names[i].getIndex()));
            }
            localeData.addEntry("territory", locale, "!sortorder", buf.toString());
        }
        for (GwtLocale locale : localeData.getAllLocales()) {
            Set<RegionPopulation> regions = getRegionsForLocale(locale);
            StringBuilder buf = new StringBuilder();
            if (!locale.isDefault()) {
                int count = 0;
                for (RegionPopulation region : regions) {
                    // only keep the first 10, and stop if there aren't many speakers
                    if (++count > 10 || region.getLiteratePopulation() < 3000000) {
                        break;
                    }
                    if (count > 1) {
                        buf.append(',');
                    }
                    buf.append(region.getRegion());
                }
            }
            localeData.addEntry("territory", locale, "!likelyorder", buf.toString());
        }
        localeData.removeDuplicates("territory");
        localeData.removeDuplicates("language");
        localeData.removeDuplicates("script");
        localeData.removeDuplicates("variant");
    }

    @Override
    protected void loadData() throws IOException {
        System.out.println("Loading data for localized names");
        localeData.addVersions(cldrFactory);
        localeData.addEntries("territory", cldrFactory, "//ldml/localeDisplayNames/territories",
                "territory", "type");
        localeData.addEntries("language", cldrFactory, "//ldml/localeDisplayNames/languages",
                "language", "type");
        localeData.addEntries("script", cldrFactory, "//ldml/localeDisplayNames/scripts", "script",
                "type");
        localeData.addEntries("variant", cldrFactory, "//ldml/localeDisplayNames/variants", "variant",
                "type");
    }

    @Override
    protected void writeOutputFiles() throws IOException {
        Set<GwtLocale> localesToPrint = localeData.getNonEmptyLocales("territory");
        String cldrDir = "shared/cldr/impl/";

        String factoryPath = "shared/cldr/impl/LocalizedNames_factory.java";
        PrintWriter factoryWriter = createOutputFile(factoryPath);
        printHeader(factoryWriter);
        factoryWriter.print("package " + "org.gwtproject.i18n.shared.cldr.impl;");
        // GWT now requires JDK 1.6, so we always generate @Overrides
        setOverrides(true);
        factoryWriter.println();
        factoryWriter.println("// DO NOT EDIT - GENERATED FROM CLDR AND ICU DATA");
        factoryWriter.println();
        factoryWriter.println("import org.gwtproject.i18n.shared.cldr.LocalizedNames;");
        factoryWriter.println();
        factoryWriter.println("public class LocalizedNames_factory {");
        factoryWriter.println();
        factoryWriter.println(" public static LocalizedNames create(){");
        factoryWriter.println();

        for (GwtLocale locale : localesToPrint) {
            Map<String, String> namesMap = localeData.getEntries("territory", locale);
            List<String> regionCodesWithNames = new ArrayList<String>();
            for (String regionCode : namesMap.keySet()) {
                if (!regionCode.startsWith("!")) {
                    // skip entries which aren't actually region codes
                    regionCodesWithNames.add(regionCode);
                }
            }

            String[] sortOrder = getRegionOrder(namesMap.get("!sortorder"));
            String[] likelyOrder = getRegionOrder(namesMap.get("!likelyorder"));
            if (regionCodesWithNames.isEmpty() && sortOrder == null && likelyOrder == null) {
                // nothing to do
                return;
            }
            // sort for deterministic output
            Collections.sort(regionCodesWithNames);
            if (locale.isDefault()) {
                generateDefaultLocale("shared/cldr/impl/DefaultLocalizedNames.java",
                        locale, namesMap, regionCodesWithNames, sortOrder, likelyOrder);
            }

            // Choose filename
            String localePart = locale.getAsString();
            if (localePart == null || localePart.isEmpty()) {
                localePart = "";
            } else {
                localePart = "_" + localePart;
            }
            String path = cldrDir + "LocalizedNamesImpl" + localePart + "." + "java";

            factoryWriter.println("   if(\"" + locale.getAsString() + "\".equals(System.getProperty(\"locale\"))){");
            factoryWriter.println("     return new LocalizedNamesImpl" + localePart + "();");
            factoryWriter.println("   }");
            factoryWriter.println();

            generateLocale(path, locale, namesMap, regionCodesWithNames, sortOrder, likelyOrder);
        }
        factoryWriter.println("     return new DefaultLocalizedNames();");
        factoryWriter.println(" }");
        factoryWriter.println("}");
        factoryWriter.close();
    }

    private void generateDefaultLocale(String path, GwtLocale locale, Map<String, String> namesMap,
                                       List<String> regionCodesWithNames, String[] sortOrder, String[] likelyOrder)
            throws IOException {
        PrintWriter pw = null;
        try {
            pw = createOutputFile(path);
            printHeader(pw);
            pw.println("package org.gwtproject.i18n.shared.cldr.impl;");
            pw.println();
            printVersion(pw, locale, "// ");
            pw.println();
            pw.println("import org.gwtproject.i18n.shared.cldr.DefaultLocalizedNamesBase;");
            pw.println();
            pw.println("/**");
            pw.println(" * Default LocalizedNames implementation.");
            pw.println(" */");
            pw.print("public class DefaultLocalizedNames extends " + "DefaultLocalizedNamesBase {");
            if (likelyOrder != null) {
                writeStringListMethod(pw, "loadLikelyRegionCodes", likelyOrder);
            }
            pw.println();
            pw.println("  @Override");
            pw.println("  protected void loadNameMap() {");
            pw.println("    super.loadNameMap();");
            for (String code : regionCodesWithNames) {
                String name = namesMap.get(code);
                if (name != null) {
                    pw.println("    namesMap.put(\"" + quote(code) + "\", \"" + quote(name) + "\");");
                }
            }
            pw.println("  }");
            if (sortOrder != null) {
                writeStringListMethod(pw, "loadSortedRegionCodes", sortOrder);
            }
            pw.println("}");
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    private void generateLocale(String path, GwtLocale locale, Map<String, String> namesMap,
                                List<String> regionCodesWithNames, String[] sortOrder, String[] likelyOrder)
            throws IOException {
        PrintWriter pw = null;
        try {
            pw = createOutputFile(path);
            printHeader(pw);
            pw.println("package org.gwtproject.i18n.shared.cldr.impl;");
            pw.println();
            pw.println("import org.gwtproject.i18n.shared.cldr.LocalizedNamesImplBase;");
            pw.println();
            printVersion(pw, locale, "// ");
            pw.println();
            pw.println("/**");
            pw.println(" * Localized names for the \"" + locale + "\" locale.");
            pw.println(" */");
            pw.print("public class LocalizedNamesImpl" + localeSuffix(locale) + " extends ");
            if (locale.isDefault()) {
                pw.print("LocalizedNamesImplBase");
            } else {
                pw.print("LocalizedNamesImpl" + localeSuffix(localeData.inheritsFrom(locale)));
            }
            pw.println(" {");
            if (!locale.isDefault()) {
                if (likelyOrder != null) {
                    writeStringListMethod(pw, "loadLikelyRegionCodes", likelyOrder);
                }
                if (sortOrder != null) {
                    writeStringListMethod(pw, "loadSortedRegionCodes", sortOrder);
                }
                if (!regionCodesWithNames.isEmpty()) {
                    pw.println();
                    pw.println("  @Override");
                    pw.println("  protected void loadNameMap() {");
                    pw.println("    super.loadNameMap();");
                    for (String code : regionCodesWithNames) {
                        String name = namesMap.get(code);
                        if (name != null && !name.equals(code)) {
                            pw.println("    namesMap.put(\"" + quote(code) + "\", \"" + quote(name) + "\");");
                        }
                    }
                    pw.println("  }");
                    pw.println();

                }
            } else if (!regionCodesWithNames.isEmpty()) {
                pw.println();
            }
            pw.println("}");
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }


    /**
     * @param locale
     * @return region populations speaking this language
     */
    private Set<RegionPopulation> getRegionsForLocale(GwtLocale locale) {
        Set<RegionPopulation> retVal =
                regionLanguageData
                        .getRegions(locale.getLanguageNotNull() + "_" + locale.getScriptNotNull());
        if (retVal.isEmpty()) {
            retVal = regionLanguageData.getRegions(locale.getLanguageNotNull());
        }
        return retVal;
    }

    /**
     * Generate a method which returns an array of string constants.
     *
     * @param pw         PrintWriter to write on
     * @param methodName the name of the method to create
     * @param values     the list of string values to return.
     */
    private void writeStringListMethod(PrintWriter pw, String methodName, String[] values) {
        pw.println();
        pw.println("  @Override");
        pw.println("  public String[] " + methodName + "() {");
        pw.println("    return new String[] {");
        for (String code : values) {
            pw.println("        \"" + Processor.quote(code) + "\",");
        }
        pw.println("    };");
        pw.println("  }");
    }
}
