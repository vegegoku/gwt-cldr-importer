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
import org.gwtproject.i18n.shared.cldr.CurrencyList;
import org.gwtproject.i18n.shared.cldr.ListPattern;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extract list formatting information from CLDR data.
 */
public class ListFormattingProcessor extends Processor {

    public ListFormattingProcessor(File outputDir, InputFactory cldrFactory, LocaleData localeData) {
        super(outputDir, cldrFactory, localeData);
    }

    @Override
    protected void cleanupData() {
        localeData.removeCompleteDuplicates("list");
    }

    @Override
    protected void loadData() throws IOException {
        System.out.println("Loading data for list formatting");
        localeData.addVersions(cldrFactory);
        localeData.addEntries("list", cldrFactory, "//ldml/listPatterns/listPattern/",
                "listPatternPart", "type");
    }

    @Override
    protected void writeOutputFiles() throws IOException {
        Set<GwtLocale> localesToPrint = localeData.getNonEmptyLocales("list");
        String path = "shared/cldr/impl/";

        List<GwtLocale> sorted = localesToPrint.stream()
                .sorted(Comparator.comparing(GwtLocale::getAsString)
                        .reversed())
                .collect(Collectors.toList());
        String packageName = "org.gwtproject.i18n.shared.cldr.impl";
        generateFactory(path, packageName, sorted);
        for (GwtLocale locale : localesToPrint) {
            writeOnJavaFile(path, packageName, locale);
            writeOnPropertiesFile(path, locale);
        }
    }

    private void generateFactory(String path, String packageName, List<GwtLocale> sorted) throws IOException {
        MethodSpec.Builder createDefaultMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addStatement("return create(System.getProperty($S))", "locale")
                .returns(ListPattern.class);

        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(String.class, "locale").build())
                .returns(ListPattern.class);

        createMethod
                .addCode(CodeBlock.builder()
                        .beginControlFlow("if($L.equals($S))", "locale", "default")
                        .addStatement("return new $T()", ClassName.bestGuess("ListPatternsImpl"))
                        .endControlFlow()
                        .build());

        sorted.forEach(gwtLocale -> {
            if (!gwtLocale.isDefault()) {
                gwtLocale.isDefault();
                createMethod
                        .addCode(CodeBlock.builder()
                                .beginControlFlow("if($L.startsWith($S))", "locale", gwtLocale.getAsString())
                                .addStatement("return new $T()", ClassName.bestGuess("ListPatternsImpl" + Processor.localeSuffix(gwtLocale, "_")))
                                .endControlFlow()
                                .build()
                        );
            }
        });

        createMethod.addStatement("return new $T()", ClassName.bestGuess("ListPatternsImpl"));

        TypeSpec.Builder listPatternsFactory = TypeSpec.classBuilder("ListPatterns_factory")
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(createDefaultMethod.build())
                .addMethod(createMethod.build());


        PrintWriter pw = createOutputFile(path + "ListPatterns_factory.java");

        TypeSpec listPatternFactoryType = listPatternsFactory.build();

        JavaFile javaFile = JavaFile.builder(packageName, listPatternFactoryType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }

    private void writeOnJavaFile(String path, String packageName, GwtLocale locale) throws IOException {
        PrintWriter pw = createOutputFile(path + "/ListPatternsImpl" + Processor.localeSuffix(locale) + ".java");
        TypeSpec.Builder ListPatternsBuilder = TypeSpec.classBuilder("ListPatternsImpl" + Processor.localeSuffix(locale));
        ListPatternsBuilder
                .addAnnotation(generatedAnnotation(this.getClass()))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ListPattern.class);

        MethodSpec getTwo = MethodSpec.methodBuilder("getTwo")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", localeData.getEntry("list", locale, "2"))
                .build();

        MethodSpec getStart = MethodSpec.methodBuilder("getStart")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", localeData.getEntry("list", locale, "start"))
                .build();

        MethodSpec getMiddle = MethodSpec.methodBuilder("getMiddle")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", localeData.getEntry("list", locale, "middle"))
                .build();

        MethodSpec getEnd = MethodSpec.methodBuilder("getEnd")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", localeData.getEntry("list", locale, "end"))
                .build();

        ListPatternsBuilder
                .addMethod(getTwo)
                .addMethod(getStart)
                .addMethod(getMiddle)
                .addMethod(getEnd);

        TypeSpec listPatternsType = ListPatternsBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, listPatternsType)
                .build();

        javaFile.writeTo(pw);
        pw.close();
    }

    private void writeOnPropertiesFile(String path, GwtLocale locale) throws IOException {
        PrintWriter pw = null;

        for (String key : localeData.getKeys("list", locale)) {
            if (pw == null) {
                pw = createOutputFile(path + "/ListPatterns_" + locale.getAsString() + ".properties");
                printPropertiesHeader(pw);
                pw.println();
                printVersion(pw, locale, "# ");
            }
            pw.println(key + "=" + localeData.getEntry("list", locale, key));
        }
        if (pw != null) {
            pw.close();
        }
    }
}
