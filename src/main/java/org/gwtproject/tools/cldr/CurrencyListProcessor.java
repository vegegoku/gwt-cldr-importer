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

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import org.gwtproject.i18n.shared.GwtLocale;
import org.gwtproject.i18n.shared.cldr.CurrencyData;
import org.gwtproject.i18n.shared.cldr.CurrencyList;
import org.unicode.cldr.util.XPathParts;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * Loads data needed to produce DateTimeFormatInfo implementations.
 */
public class CurrencyListProcessor extends Processor {

    private static final Logger LOGGER = Logger.getLogger(CurrencyListProcessor.class.getName());

    private Map<String, Integer> currencyFractions = new HashMap<String, Integer>();
    private int defaultCurrencyFraction;
    private Map<String, Integer> rounding = new HashMap<String, Integer>();

    private Set<String> stillInUse = new HashSet<String>();
    private Map<GwtLocale, List<LocaleData.Currency>> localesCurrencies;
    private Map<String, String> languageMostPopulatedRegion;
    private Map<String, String> regionsCurrencies;

    public CurrencyListProcessor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
        super(outputDir, cldrFactory, localeData);
    }

    @Override
    protected void cleanupData() {
    }

    @Override
    protected void loadData() throws IOException {
        LOGGER.info("Loading data for currencies");
        localeData.addVersions(cldrFactory);
        loadLocaleIndependentCurrencyData();
        localesCurrencies = localeData.addCurrencyEntries("currency", cldrFactory, currencyFractions,
                defaultCurrencyFraction, stillInUse, rounding);

        LOGGER.info("Loading regions currencies ...");
        regionsCurrencies = localeData.getRegionCurrencyEntries(cldrFactory);

        LOGGER.info("Loading languages population for def currency ...");
        languageMostPopulatedRegion = localeData.getLanguageMostPopulatedRegion(cldrFactory);
    }

    @Override
    protected void printHeader(PrintWriter pw) {
        printPropertiesHeader(pw);
        pw.println();
        pw.println("#");
        pw.println("# The key is an ISO4217 currency code, and the value is of the " + "form:");
        pw.println("#   display name|symbol|decimal digits|not-used-flag|rounding");
        pw.println("# If a symbol is not supplied, the currency code will be used");
        pw.println("# If # of decimal digits is omitted, 2 is used");
        pw.println("# If a currency is not generally used, not-used-flag=1");
        pw.println("# If a currency should be rounded to a multiple of of the least significant");
        pw.println("#   digit, rounding will be present");
        pw.println("# Trailing empty fields can be omitted");
        pw.println();
    }

    @Override
    protected void writeOutputFiles() throws IOException {
        Set<GwtLocale> localesToWrite = localeData.getNonEmptyLocales();
        String path = "shared/cldr/impl/";
        String packageName = "org.gwtproject.i18n.shared.cldr.impl";
        Map<GwtLocale, List<LocaleData.Currency>> temp = this.localesCurrencies;
        Map<String, CurrencyInfo> allCurrencyData = new HashMap<>();
        String lastDefaultCurrencyCode = null;

        List<GwtLocale> sorted = localesToWrite.stream()
                .sorted(Comparator.comparing(GwtLocale::getAsString)
                        .reversed())
                .collect(Collectors.toList());

        generateFactory(path, packageName, sorted);

        for (GwtLocale locale : sorted) {
            Map<String, String> currencyData = localeData.getEntries("currency", locale);
            String[] currencies = new String[currencyData.size()];
            currencyData.keySet().toArray(currencies);
            Arrays.sort(currencies);

            Properties extraInfo = new Properties();
            InputStream extraResource = this.getClass().getClassLoader().getResourceAsStream("CurrencyExtra" + Processor.localeSuffix(locale) + ".extraInfo");
            if (nonNull(extraResource)) {
                extraInfo.load(extraResource);
            }

            for (String currencyCode : currencies) {
                CurrencyInfo currencyInfo = new CurrencyInfo(currencyCode, currencyData.get(currencyCode), extraInfo.getProperty(currencyCode));
                allCurrencyData.put(currencyCode, currencyInfo);
            }

            String defCurrencyCode = getLocaleDefaultCurrency(locale);
            // If this locale specifies a particular locale, or the one that is
            // inherited has been changed in this locale, re-specify the default
            // currency so the method will be generated.
            if (defCurrencyCode == null && currencyData.containsKey(lastDefaultCurrencyCode)) {
                defCurrencyCode = lastDefaultCurrencyCode;
            }

            if (!currencyData.isEmpty() || defCurrencyCode != null) {
                generateOnLocale(path, packageName, locale, currencies, allCurrencyData, defCurrencyCode);
                lastDefaultCurrencyCode = defCurrencyCode;
            }

            generatePropertiesFile(path, locale, currencyData, currencies);
        }
    }

    private void generateFactory(String path, String packageName, List<GwtLocale> sorted) throws IOException {
        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(CurrencyList.class);

        createMethod
                .addCode(CodeBlock.builder()
                        .beginControlFlow("if(System.getProperty($S).equals($S))", "locale", "default")
                        .addStatement("return new $T()", ClassName.bestGuess("CurrencyList_"))
                        .endControlFlow()
                        .build());

        sorted.forEach(gwtLocale -> {
            if (!gwtLocale.isDefault()) {
                if (!gwtLocale.isDefault()) {
                    createMethod
                            .addCode(CodeBlock.builder()
                                    .beginControlFlow("if(System.getProperty($S).startsWith($S))", "locale", gwtLocale.getAsString())
                                    .addStatement("return new $T()", ClassName.bestGuess("CurrencyList" + Processor.localeSuffix(gwtLocale, "_")))
                                    .endControlFlow()
                                    .build()
                            );
                }
            }
        });

        createMethod.addStatement("return new $T()", ClassName.bestGuess("CurrencyList_"));

        TypeSpec.Builder currencyListFactory = TypeSpec.classBuilder("CurrencyList_factory")
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(createMethod.build());

        PrintWriter pw = createOutputFile(path + "CurrencyList_factory.java");

        TypeSpec currencyListFactoryType = currencyListFactory.build();

        JavaFile javaFile = JavaFile.builder(packageName, currencyListFactoryType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }

    private void generateOnLocale(String path, String packageName, GwtLocale locale, String[] currencies, Map<String, CurrencyInfo> allCurrencyData, String defCurrencyCode) throws IOException {
        LOGGER.info("Generating CurrencyList for locale : " + locale.getAsString());

        String classFileName = "CurrencyList" + Processor.localeSuffix(locale, "_");
        PrintWriter pw = createOutputFile(path + "/" + classFileName + ".java");
        TypeSpec.Builder currencyListBuilder = TypeSpec.classBuilder(classFileName);
        currencyListBuilder
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .superclass(getLocaleSuperClass(locale));

        if (defCurrencyCode != null) {
            CurrencyInfo currencyInfo = allCurrencyData.get(defCurrencyCode);
            if (currencyInfo == null) {
                // Synthesize a null info if the specified default wasn't found.
                currencyInfo = new CurrencyInfo(defCurrencyCode, null, null);
                allCurrencyData.put(defCurrencyCode, currencyInfo);
            }


            MethodSpec defaultMethod = MethodSpec.methodBuilder("getDefault")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(CurrencyData.class)
                    .addCode(currencyInfo.getDefaultImplCode())
                    .build();

            currencyListBuilder
                    .addMethod(defaultMethod);

        }

        if (currencies.length > 0) {
            writeCurrencyMethod(currencies, allCurrencyData)
                    .ifPresent(currencyListBuilder::addMethod);

            writeNamesMethod(currencies, allCurrencyData)
                    .ifPresent(currencyListBuilder::addMethod);

        }

        TypeSpec currencyListType = currencyListBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, currencyListType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }

    private Optional<MethodSpec> writeCurrencyMethod(String[] currencies, Map<String, CurrencyInfo> allCurrencyData) {

        if (currencies.length < 1) {
            return Optional.empty();
        }

        MethodSpec.Builder loadCurrencyMap = MethodSpec.methodBuilder("loadCurrencyMap");
        loadCurrencyMap
                .addModifiers(Modifier.PROTECTED)
                .returns(ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeName.get(String.class), TypeName.get(CurrencyData.class)))
                .addStatement("$T<$T,$T> result = super.loadCurrencyMap()", HashMap.class, String.class, CurrencyData.class);

        for (String currencyCode : currencies) {
            CurrencyInfo currencyInfo = allCurrencyData.get(currencyCode);

            loadCurrencyMap.addComment("$L", currencyInfo.getDisplayName());
            loadCurrencyMap.addCode(currencyInfo.asMapEntry());
        }
        loadCurrencyMap.addStatement("return result");
        return Optional.of(loadCurrencyMap.build());
    }

    private Optional<MethodSpec> writeNamesMethod(String[] currencies, Map<String, CurrencyInfo> allCurrencyData) {

        List<CodeBlock> statements = new ArrayList<>();

        for (String currencyCode : currencies) {
            CurrencyInfo currencyInfo = allCurrencyData.get(currencyCode);
            String displayName = currencyInfo.getDisplayName();
            if (displayName != null && !currencyCode.equals(displayName)) {
                statements.add(CodeBlock.builder()
                        .addStatement("result.put($S,$S)", currencyCode, displayName).build());
            }
        }

        if (!statements.isEmpty()) {
            MethodSpec.Builder loadNamesMethod = MethodSpec.methodBuilder("loadNamesMap")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .returns(ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeName.get(String.class), TypeName.get(String.class)))
                    .addStatement("$T result = super.loadNamesMap()", ParameterizedTypeName.get(ClassName.get(HashMap.class), TypeName.get(String.class), TypeName.get(String.class)));

            statements.forEach(loadNamesMethod::addCode);

            loadNamesMethod.addStatement("return result");

            return Optional.of(loadNamesMethod.build());
        }

        return Optional.empty();
    }

    private ClassName getLocaleSuperClass(GwtLocale locale) {
        if (locale.isDefault()) {
            return ClassName.get(CurrencyList.class);
        }

        GwtLocale parent = localeData.inheritsFrom(locale);
        if (isNull(parent)) {
            return ClassName.bestGuess("CurrencyList_");
        }
        return ClassName.bestGuess("CurrencyList" + Processor.localeSuffix(parent, "_"));
    }

    private String getLocaleDefaultCurrency(GwtLocale locale) {
        if ("default".equals(locale.getAsString())) {
            return "USD";
        }
        if (isNull(locale.getRegion())) {
            return regionsCurrencies.get(languageMostPopulatedRegion.get(locale.getLanguage()));
        }
        return regionsCurrencies.get(locale.getRegion());
    }

    private void generatePropertiesFile(String path, GwtLocale locale, Map<String, String> map, String[] keys) throws IOException {
        System.out.println("Generating currency data for locale : " + locale);
        PrintWriter pw = createOutputFile(path + "CurrencyData" + Processor.localeSuffix(locale) + ".properties");
        printHeader(pw);
        printVersion(pw, locale, "# ");

        for (String key : keys) {
            pw.print(key);
            pw.print(" = ");
            pw.println(map.get(key));
        }
        pw.close();
    }

    private void loadLocaleIndependentCurrencyData() {
        InputFile supp = cldrFactory.getSupplementalData();

        // load the table of default # of decimal places and rounding for each currency
        defaultCurrencyFraction = 0;
        XPathParts parts = new XPathParts();
        for (String path : supp.listPaths("//supplementalData/currencyData/fractions/info")) {
            parts.setForWritingWithSuppressionMap(supp.getFullXPath(path));
            Map<String, String> attr = parts.findAttributes("info");
            if (attr == null) {
                continue;
            }
            String curCode = attr.get("iso4217");
            int digits = Integer.valueOf(attr.get("digits"));
            if ("DEFAULT".equalsIgnoreCase(curCode)) {
                defaultCurrencyFraction = digits;
            } else {
                currencyFractions.put(curCode, digits);
            }
            int roundingDigits = Integer.valueOf(attr.get("rounding"));
            if (roundingDigits != 0) {
                rounding.put(curCode, roundingDigits);
            }
        }

        // find which currencies are still in use in some region, everything else
        // should be marked as deprecated
        for (String path : supp.listPaths("//supplementalData/currencyData/region")) {
            parts.setForWritingWithSuppressionMap(supp.getFullXPath(path));
            Map<String, String> attr = parts.findAttributes("currency");
            if (attr == null) {
                continue;
            }
            String region = parts.findAttributeValue("region", "iso3166");
            String curCode = attr.get("iso4217");
            if ("ZZ".equals(region) || "false".equals(attr.get("tender")) || "XXX".equals(curCode)) {
                // ZZ is an undefined region, XXX is an unknown currency code (and needs
                // to be special-cased because it is listed as used in Antarctica!)
                continue;
            }
            String to = attr.get("to");
            if (to == null) {
                stillInUse.add(curCode);
            }
        }
    }
}
