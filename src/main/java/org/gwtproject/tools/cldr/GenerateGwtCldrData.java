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

import com.ibm.icu.dev.tool.UOption;
import org.gwtproject.i18n.processor.GwtLocaleFactoryImpl;
import org.gwtproject.i18n.shared.GwtLocaleFactory;
import org.unicode.cldr.util.CLDRPaths;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generate a country list for each locale, taking into account the literate
 * population of each country speaking the language.
 */
public class GenerateGwtCldrData {

    public static final Logger LOGGER = Logger.getLogger(GenerateGwtCldrData.class.getName());

    private static final GwtLocaleFactory factory = new GwtLocaleFactoryImpl();

    private static final String DEFAULT_PROCESSORS = "CurrencyListProcessor,LocalesNativeNamesProcessor,"
            + "NumberConstantsProcessor,ListFormattingProcessor,LocalizedNamesProcessor,DateTimeFormatInfoProcessor";

    public static void main(String[] args) throws IOException, SecurityException,
            NoSuchMethodException, IllegalArgumentException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        LOGGER.info("Starting to generate from CLDR data (ignore -D lines "
                + "produced by cldr-tools)");
        UOption[] options = {
                UOption.HELP_H(), UOption.HELP_QUESTION_MARK(),
                UOption.SOURCEDIR().setDefault(CLDRPaths.MAIN_DIRECTORY),
                outputDir().setDefault("./"),
                restrictLocales(),
                processors().setDefault(DEFAULT_PROCESSORS),
                cldrVersion()
        };
        UOption.parseArgs(args, options);
        String sourceDir = options[2].value; // SOURCEDIR
        String targetDir = options[3].value; // outputDir
        String restrictLocales = options[4].value; // --restrictLocales
        String procNames = options[5].value; // processors
        CldrVersion.value = options[6].value; // processors

        List<Class<? extends Processor>> processorClasses = new ArrayList<Class<? extends Processor>>();
        for (String procName : procNames.split(",")) {
            if (!procName.contains(".")) {
                procName = Processor.class.getPackage().getName() + "." + procName;
            }
            Throwable thrown = null;
            try {
                Class<?> clazz = Class.forName(procName);
                processorClasses.add(clazz.asSubclass(Processor.class));
            } catch (ClassNotFoundException e) {
                thrown = e;
            } catch (ClassCastException e) {
                thrown = e;
            }
            if (thrown != null) {
                LOGGER.log(Level.SEVERE, "Ignoring " + procName + " (" + thrown + ")");
            }
        }
        InputFactory cldrFactory = new InputFactory(sourceDir);

        List<String> locales = cldrFactory.chooseLocales(restrictLocales);
        LOGGER.info("Processing " + locales.size() + " locales");
        File outputDir = new File(targetDir);
        LocaleData localeData = new LocaleData(factory, locales);
        for (Class<? extends Processor> processorClass : processorClasses) {
            Constructor<? extends Processor> ctor =
                    processorClass.getConstructor(File.class, InputFactory.class, LocaleData.class);
            Processor processor = ctor.newInstance(outputDir, cldrFactory, localeData);
            processor.run();
        }
        LOGGER.info("Finished.");
    }

    private static UOption outputDir() {
        return UOption.create("outdir", 'o', UOption.REQUIRES_ARG);
    }

    private static UOption cldrVersion() {
        return UOption.create("cldrversion", 'v', UOption.REQUIRES_ARG);
    }

    private static UOption processors() {
        return UOption.create("processors", 'p', UOption.REQUIRES_ARG);
    }

    private static UOption restrictLocales() {
        return UOption.create("restrictLocales", 'r', UOption.REQUIRES_ARG);
    }
}
