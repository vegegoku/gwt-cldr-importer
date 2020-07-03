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
import org.gwtproject.i18n.shared.GwtLocale;
import org.gwtproject.i18n.shared.cldr.NumberConstants;
import org.gwtproject.i18n.shared.cldr.NumberConstantsImpl;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * Loads data needed to produce DateTimeFormatInfo implementations.
 */
public class NumberConstantsProcessor extends Processor {

    public static final Logger LOGGER = Logger.getLogger(NumberConstantsProcessor.class.getName());

    private Map<String, String> numberSystemEntries;
    private Map<String, String> languageMostPopulatedRegion;
    private Map<String, String> regionsCurrencies;

    public NumberConstantsProcessor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
        super(outputDir, cldrFactory, localeData);
    }

    @Override
    protected void cleanupData() {
    }

    @Override
    protected void loadData() throws IOException {
        LOGGER.info("Loading data for number constants");

        LOGGER.info("Loading Symbols ...");
        localeData.addNumberConstantsEntries("numberConstants", cldrFactory,
                "//ldml/numbers/symbols[@numberSystem=\"${numberSystem}\"", "symbols", null, null);

        LOGGER.info("Loading scientific format patterns ...");
        localeData.addNumberConstantsEntries("numberConstants", cldrFactory,
                "//ldml/numbers/scientificFormats[@numberSystem=\"${numberSystem}\"]/" +
                        "scientificFormatLength/scientificFormat", "pattern", null, "scientificPattern");

        LOGGER.info("Loading percent format patterns ...");
        localeData.addNumberConstantsEntries("numberConstants", cldrFactory,
                "//ldml/numbers/percentFormats[@numberSystem=\"${numberSystem}\"]/" +
                        "percentFormatLength/percentFormat", "pattern", null, "percentPattern");

        LOGGER.info("Loading currency format patterns ...");
        localeData.addNumberConstantsEntries("numberConstants", cldrFactory,
                "//ldml/numbers/currencyFormats[@numberSystem=\"${numberSystem}\"]/" +
                        "currencyFormatLength/currencyFormat", "pattern", null, "currencyPattern");

        LOGGER.info("Loading decimal format patterns ...");
        localeData.addNumberConstantsEntries("numberConstants", cldrFactory,
                "//ldml/numbers/decimalFormats[@numberSystem=\"${numberSystem}\"]/" +
                        "decimalFormatLength/decimalFormat", "pattern", null, "decimalPattern");

        LOGGER.info("Loading Numbering systems ...");
        numberSystemEntries = localeData.addNumberSystemEntries(cldrFactory);

        LOGGER.info("Loading regions currencies ...");
        regionsCurrencies = localeData.getRegionCurrencyEntries(cldrFactory);

        LOGGER.info("Loading languages population for def currency ...");
        languageMostPopulatedRegion = localeData.getLanguageMostPopulatedRegion(cldrFactory);

    }

    @Override
    protected void printHeader(PrintWriter pw) {
        printJavaHeader(pw);
    }

    @Override
    protected void writeOutputFiles() throws IOException {
        Set<GwtLocale> localesToWrite = localeData.getNonEmptyLocales();
        String path = "shared/cldr/impl/";
        String packageName = "org.gwtproject.i18n.shared.cldr.impl";

        List<GwtLocale> sorted = localesToWrite.stream()
                .sorted(Comparator.comparing(GwtLocale::getAsString)
                        .reversed())
                .collect(Collectors.toList());


        generateFactory(path, packageName, sorted);

        for (GwtLocale locale : sorted) {
            LOGGER.info("Generating Number constants data for locale : " + locale);
            writeOneJavaFile(path, packageName, locale);
        }
    }

    private void generateFactory(String path, String packageName, List<GwtLocale> sorted) throws IOException {

        MethodSpec.Builder createDefaultMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addStatement("return create(System.getProperty($S))", "locale")
                .returns(NumberConstants.class);

        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(String.class, "locale").build())
                .returns(NumberConstants.class);

        createMethod
                .addCode(CodeBlock.builder()
                        .beginControlFlow("if($L.equals($S))", "locale", "default")
                        .addStatement("return new $T()", ClassName.bestGuess("NumberConstantsImpl"))
                        .endControlFlow()
                        .build());

        sorted.forEach(gwtLocale -> {
            if (!gwtLocale.isDefault()) {
                gwtLocale.isDefault();
                createMethod
                        .addCode(CodeBlock.builder()
                                .beginControlFlow("if($L.startsWith($S))", "locale", gwtLocale.getAsString())
                                .addStatement("return new $T()", ClassName.bestGuess("NumberConstantsImpl" + Processor.localeSuffix(gwtLocale)))
                                .endControlFlow()
                                .build()
                        );
            }
        });

        createMethod.addStatement("return new $T()", ClassName.bestGuess("NumberConstantsImpl"));

        TypeSpec.Builder numberConstantsFactory = TypeSpec.classBuilder("NumberConstants_factory")
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(createDefaultMethod.build())
                .addMethod(createMethod.build());

        PrintWriter pw = createOutputFile(path + "NumberConstants_factory.java");

        TypeSpec currencyListFactoryType = numberConstantsFactory.build();

        JavaFile javaFile = JavaFile.builder(packageName, currencyListFactoryType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }

    protected void writeOneJavaFile(String path, String packageName,
                                    GwtLocale locale) throws IOException {
        LOGGER.info("Generating NumberConstants for locale : " + locale.getAsString());

        PrintWriter pw = createOutputFile(path + "/NumberConstantsImpl" + Processor.localeSuffix(locale) + ".java");
        TypeSpec.Builder numberConstantsBuilder = TypeSpec.classBuilder("NumberConstantsImpl" + Processor.localeSuffix(locale));
        numberConstantsBuilder
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(NumberConstantsImpl.class);

        String defaultNumberingSystem = localeData.getEntries("defaultNumberingSystem", locale).get("defaultNumberingSystem");

        String decimalSeparator = getEntry(locale, "decimal");
        String groupingSeparator = getEntry(locale, "group");
        String percent = getEntry(locale, "percentSign");
        String zeroDigit = getZeroDigit(isNull(defaultNumberingSystem) ? "latn" : defaultNumberingSystem);
        String plusSign = getEntry(locale, "plusSign");
        String minusSign = getEntry(locale, "minusSign");
        String exponentialSymbol = getEntry(locale, "exponential");
        String perMill = getEntry(locale, "perMille");
        String infinity = getEntry(locale, "infinity");
        String notANumber = getEntry(locale, "nan");
        String decimalPattern = getEntry(locale, "decimalPattern");
        String scientificPattern = getEntry(locale, "scientificPattern");
        String percentPattern = getEntry(locale, "percentPattern");
        String currencyPattern = getEntry(locale, "currencyPattern");
        String simpleCurrencyPattern = currencyPattern.replace("¤", "¤¤¤¤");
        String globalCurrencyPattern = toGlobalCurrencyFormat(simpleCurrencyPattern);
        String defCurrencyCode = getLocaleDefaultCurrency(locale);

        numberConstantsBuilder
                .addMethod(getMethod("notANumber", notANumber))
                .addMethod(getMethod("decimalPattern", decimalPattern))
                .addMethod(getMethod("decimalSeparator", decimalSeparator))
                .addMethod(getMethod("defCurrencyCode", defCurrencyCode))
                .addMethod(getMethod("exponentialSymbol", exponentialSymbol))
                .addMethod(getMethod("groupingSeparator", groupingSeparator))
                .addMethod(getMethod("infinity", infinity))
                .addMethod(getMethod("minusSign", minusSign))
                .addMethod(getMethod("monetaryGroupingSeparator", groupingSeparator))
                .addMethod(getMethod("monetarySeparator", decimalSeparator))
                .addMethod(getMethod("percent", percent))
                .addMethod(getMethod("percentPattern", percentPattern))
                .addMethod(getMethod("perMill", perMill))
                .addMethod(getMethod("plusSign", plusSign))
                .addMethod(getMethod("scientificPattern", scientificPattern))
                .addMethod(getMethod("currencyPattern", currencyPattern))
                .addMethod(getMethod("simpleCurrencyPattern", simpleCurrencyPattern))
                .addMethod(getMethod("globalCurrencyPattern", globalCurrencyPattern))
                .addMethod(getMethod("zeroDigit", zeroDigit))
        ;

        TypeSpec numberConstantsType = numberConstantsBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, numberConstantsType)
                .addFileComment("$L", getFileHeaderComments())
                .build();

        javaFile.writeTo(pw);
        pw.close();
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

    public String toGlobalCurrencyFormat(String simpleCurrencyFormat) {

        /*
        The below code in comments is the google internal python tool code snippet shared in the GWT contributors google groups
        https://groups.google.com/forum/#!topic/google-web-toolkit-contributors/24YOrGKVMds
        */

        /*
          global_pattern = ''
          in_quote = False
          for ch in simple_pattern:
            if ch == '\'':
              in_quote = not in_quote
            elif ch == ';' and not in_quote:
              global_pattern += ur' \u00a4\u00a4'
            global_pattern += ch
          global_pattern += ur' \u00a4\u00a4'
          return global_pattern
         */

        String globalPattern = "";
        boolean inQuote = false;
        for (char ch : simpleCurrencyFormat.toCharArray()) {
            if (ch == '\'') {
                inQuote = !inQuote;
            } else if (ch == ';' && !inQuote) {
                globalPattern += " ¤¤";
            }
            globalPattern += ch;
        }
        globalPattern += " ¤¤";
        return globalPattern;
    }

    public String getZeroDigit(String numberSystem) {
        String digits = numberSystemEntries.get(numberSystem);
        return Character.toString(digits.charAt(0));
    }

    private String getEntry(GwtLocale locale, String key) {
        return localeData.getEntries("numberConstants", locale).get(key);
    }

    private MethodSpec getMethod(String name, String value) {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", value)
                .build();
    }

    public String getFileHeaderComments() {
        int year = 2012;
        return "/*\n" +
                " * Copyright " + year + " Google Inc.\n" +
                " * \n" +
                " * Licensed under the Apache License, Version 2.0 (the "
                + "\"License\"); you may not\n" +
                " * use this file except in compliance with the License. You "
                + "may obtain a copy of\n" +
                " * the License at\n" +
                " * \n" +
                " * http://www.apache.org/licenses/LICENSE-2.0\n" +
                " * \n" +
                " * Unless required by applicable law or agreed to in writing, " + "software\n" +
                " * distributed under the License is distributed on an \"AS "
                + "IS\" BASIS, WITHOUT\n" +
                " * WARRANTIES OR CONDITIONS OF ANY KIND, either express or " + "implied. See the\n" +
                " * License for the specific language governing permissions and "
                + "limitations under\n" +
                " * the License.\n" +
                " */\n";
    }

}
