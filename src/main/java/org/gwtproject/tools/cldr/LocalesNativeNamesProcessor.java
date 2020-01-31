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

import com.squareup.javapoet.*;
import org.gwtproject.i18n.shared.cldr.*;
import org.gwtproject.i18n.shared.GwtLocale;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.Collator;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static org.gwtproject.tools.cldr.DateTimeFormatInfoProcessor.FORMATS;

/**
 * Extract list formatting information from CLDR data.
 */
public class LocalesNativeNamesProcessor extends Processor {

    public static final Logger LOGGER = Logger.getLogger(LocalesNativeNamesProcessor.class.getName());

    private final RegionLanguageData regionLanguageData;

    /**
     * Set of canonical language codes which are RTL.
     * this is a copy/paste from gwt
     */
    private static final Set<String> RTL_LOCALES = new HashSet<>();

    static {
        // TODO(jat): get this from CLDR data.
        RTL_LOCALES.add("ar");
        RTL_LOCALES.add("fa");
        RTL_LOCALES.add("he");
        RTL_LOCALES.add("ps");
        RTL_LOCALES.add("ur");
    }

    private static final Map<String, String> OVERRIDE_LOCALES_NATIVE_NAMES = new HashMap<>();

    static {
        OVERRIDE_LOCALES_NATIVE_NAMES.put("ssy", "Saho");
    }

    public LocalesNativeNamesProcessor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
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
                // only include real country codes
                if (!"ZZ".equals(regionCode) && regionCode.length() == 2) {
                    countryCodes.add(regionCode);
                }
            }
            Locale javaLocale =
                    new Locale(locale.getLanguageNotNull(), locale.getRegionNotNull(), locale
                            .getVariantNotNull());
            Collator collator = Collator.getInstance(javaLocale);
            LocalizedNamesProcessor.IndexedName[] names = new LocalizedNamesProcessor.IndexedName[countryCodes.size()];
            for (int i = 0; i < names.length; ++i) {
                names[i] = new LocalizedNamesProcessor.IndexedName(collator, i, map.get(countryCodes.get(i)));
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
            Set<RegionLanguageData.RegionPopulation> regions = getRegionsForLocale(locale);
            StringBuilder buf = new StringBuilder();
            if (!locale.isDefault()) {
                int count = 0;
                for (RegionLanguageData.RegionPopulation region : regions) {
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
        removeUnusedFormats();
        localeData.removeDuplicates("territory");
        localeData.removeDuplicates("language");
        localeData.removeDuplicates("script");
        localeData.removeDuplicates("variant");
    }

    /**
     * @param locale
     * @return region populations speaking this language
     */
    private Set<RegionLanguageData.RegionPopulation> getRegionsForLocale(GwtLocale locale) {
        Set<RegionLanguageData.RegionPopulation> retVal =
                regionLanguageData
                        .getRegions(locale.getLanguageNotNull() + "_" + locale.getScriptNotNull());
        if (retVal.isEmpty()) {
            retVal = regionLanguageData.getRegions(locale.getLanguageNotNull());
        }
        return retVal;
    }

    private void removeUnusedFormats() {
        for (GwtLocale locale : localeData.getAllLocales()) {
            Set<String> toRemove = new HashSet<String>();
            Map<String, String> map = localeData.getEntries("predef", locale);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!FORMATS.containsKey(entry.getKey())) {
                    toRemove.add(entry.getKey());
                }
            }
            localeData.removeEntries("predef", locale, toRemove);
        }
    }

    @Override
    protected void loadData() throws IOException {
        LOGGER.info("Loading data for locale native names formatting");
        localeData.addEntries("territory", cldrFactory, "//ldml/localeDisplayNames/territories",
                "territory", "type");
        localeData.addEntries("language", cldrFactory, "//ldml/localeDisplayNames/languages",
                "language", "type");
        localeData.addEntries("script", cldrFactory, "//ldml/localeDisplayNames/scripts", "script",
                "type");
        localeData.addEntries("variant", cldrFactory, "//ldml/localeDisplayNames/variants", "variant",
                "type");
        localeData.addEntries("predef", cldrFactory,
                "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/"
                        + "availableFormats", "dateFormatItem", "id");
    }

    @Override
    protected void writeOutputFiles() throws IOException {
        Set<GwtLocale> localesToPrint = getLocales();
        String path = "shared/cldr/impl";

        List<GwtLocale> sorted = localesToPrint.stream()
                .sorted(Comparator.comparing(GwtLocale::getAsString))
                .collect(Collectors.toList());
        String packageName = "org.gwtproject.i18n.shared.cldr.impl";

        generateFactory(path, packageName, sorted);

        TypeSpec.Builder localesNativeNamesBuilder = TypeSpec.classBuilder("LocalesNativeNames");

        localesNativeNamesBuilder
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC);

        MethodSpec.Builder getLocaleNames = MethodSpec.methodBuilder("getLocalesNativeNames")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        TypeName.get(String.class),
                        TypeName.get(String.class)
                ))
                .addStatement("$T<$T,$T> result = new $T<>()", Map.class, String.class, String.class, HashMap.class);

        PrintWriter propertiesWriter = createOutputFile(path + "/LocaleNativeDisplayNames-generated.properties");
        printPropertiesHeader(propertiesWriter);
        propertiesWriter.println();

        for (GwtLocale locale : sorted) {

            generateOneLocale(path, packageName, locale);

            if (!locale.isDefault()) {
                String languageName = localeData.getEntry("language", locale, locale.getLanguage());
                String territoryName = isNull(locale.getRegion()) ? null : localeData.getEntry("territory", locale, locale.getRegion());
                String localeNativeName = isNull(territoryName) ? languageName : languageName + " - " + territoryName;

                getLocaleNames.addStatement("result.put($S,$S)", locale.getAsString(), localeNativeName);
                propertiesWriter.println(locale.getAsString() + "=" + localeNativeName);
            }
        }

        OVERRIDE_LOCALES_NATIVE_NAMES
                .keySet()
                .forEach(language -> {
                    getLocaleNames.addStatement("result.put($S,$S)", language, OVERRIDE_LOCALES_NATIVE_NAMES.get(language));
                    propertiesWriter.println(language + "=" + OVERRIDE_LOCALES_NATIVE_NAMES.get(language));
                });

        propertiesWriter.close();

        getLocaleNames.addStatement("return result");

        localesNativeNamesBuilder.addMethod(getLocaleNames.build());

        TypeSpec localesNativeNamesType = localesNativeNamesBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, localesNativeNamesType)
                .build();

        PrintWriter classWriter = createOutputFile(path + "/LocalesNativeNames.java");
        javaFile.writeTo(classWriter);
        classWriter.close();

    }

    private Set<GwtLocale> getLocales() {
        Set<GwtLocale> result = new HashSet<>();

        Set<GwtLocale> locales = localeData.getNonEmptyLocales();
        locales.forEach(gwtLocale -> {
            Map<String, String> namesMap = localeData.getEntries("territory", gwtLocale);
            List<String> regionCodesWithNames = new ArrayList<String>();
            for (String regionCode : namesMap.keySet()) {
                if (!regionCode.startsWith("!")) {
                    // skip entries which aren't actually region codes
                    regionCodesWithNames.add(regionCode);
                }
            }

            String[] sortOrder = getRegionOrder(namesMap.get("!sortorder"));
            String[] likelyOrder = getRegionOrder(namesMap.get("!likelyorder"));
            if ((!regionCodesWithNames.isEmpty() || sortOrder != null || likelyOrder != null)) {
                result.add(gwtLocale);
            }
        });
        return result;
    }

    /**
     * Split a list of region codes into an array.client
     *
     * @param regionList comma-separated list of region codes
     * @return array of region codes, null if none
     */
    private String[] getRegionOrder(String regionList) {
        String[] split = null;
        if (regionList != null && regionList.length() > 0) {
            split = regionList.split(",");
        }
        return split;
    }

    private void generateOneLocale(String path, String packageName, GwtLocale locale) throws IOException {
        LOGGER.info("Generating LocaleInfoImpl for locale : " + locale.getAsString());

        PrintWriter pw = createOutputFile(path + "/LocaleInfoImpl" + Processor.localeSuffix(locale, "_") + ".java");
        TypeSpec.Builder localeInfoBuilder = TypeSpec.classBuilder("LocaleInfoImpl" + Processor.localeSuffix(locale, "_"));
        localeInfoBuilder
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .superclass(LocaleInfoImpl.class);

        MethodSpec.Builder getLocaleName = MethodSpec.methodBuilder("getLocaleName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", locale.toString());

        MethodSpec.Builder getDateTimeFormatInfo = MethodSpec.methodBuilder("getDateTimeFormatInfo")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(DateTimeFormatInfo.class)
                .addStatement("return new $T()", locale.isDefault() ? DateTimeFormatInfoImpl.class : ClassName.bestGuess("DateTimeFormatInfoImpl_" + locale.toString()));

        MethodSpec.Builder getLocalizedNames = MethodSpec.methodBuilder("getLocalizedNames")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(LocalizedNames.class)
                .addStatement("return new $T()", ClassName.bestGuess("LocalizedNamesImpl" + (locale.isDefault() ? "" : "_" + locale.toString())));

        MethodSpec.Builder getNumberConstants = MethodSpec.methodBuilder("getNumberConstants")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(NumberConstants.class)
                .addStatement("return new $T()", ClassName.bestGuess("NumberConstantsImpl" + (locale.isDefault() ? "" : "_" + locale.toString())));

        MethodSpec.Builder isRTL = MethodSpec.methodBuilder("isRTL")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return $L", RTL_LOCALES.contains(locale.getLanguage()));


        localeInfoBuilder
                .addMethod(getLocaleName.build())
                .addMethod(getLocalizedNames.build())
                .addMethod(getDateTimeFormatInfo.build())
                .addMethod(getNumberConstants.build())
                .addMethod(isRTL.build());

        TypeSpec localeInfoType = localeInfoBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, localeInfoType)
                .build();

        javaFile.writeTo(pw);
        pw.close();

    }

    private void generateFactory(String path, String packageName, List<GwtLocale> sorted) throws IOException {
        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(LocaleInfoImpl.class);

        sorted.forEach(gwtLocale -> {
            if (!gwtLocale.isDefault()) {
                createMethod
                        .addCode(CodeBlock.builder()
                                .beginControlFlow("if(System.getProperty($S).startsWith($S))", "locale", gwtLocale.isDefault() ? "default" : gwtLocale.getAsString())
                                .addStatement("return new $T()", ClassName.bestGuess("LocaleInfoImpl" + Processor.localeSuffix(gwtLocale, "_")))
                                .endControlFlow()
                                .build()
                        );
            }
        });

        createMethod.addStatement("return new $T()", ClassName.bestGuess("LocaleInfoImpl_"));

        TypeSpec.Builder listPatternsFactory = TypeSpec.classBuilder("LocaleInfo_factory")
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(createMethod.build());


        PrintWriter pw = createOutputFile(path + "/LocaleInfo_factory.java");

        TypeSpec listPatternFactoryType = listPatternsFactory.build();

        JavaFile javaFile = JavaFile.builder(packageName, listPatternFactoryType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }
}
