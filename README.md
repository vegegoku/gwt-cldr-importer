# gwt-cldr-importer
Gwt tool to generate CLDR classes for gwt from CLDR data

### Usage

- Prerequisites

    - git + git lfs https://help.github.com/en/articles/installing-git-large-file-storage
    - ANT

- Checkout the repository
- To update the CLDR data execute:
 
    `gradle build -Pupdate -Dcldr.version=<cldr data version>`
    
    cldr data will be downloaded into the build directory `build/cldr-data`
    
    this command will also build the cldr data java tools and generate the jars needed for gwt classes generation, and will copy them into the `cldr-lib` folder.
    
- To generate gwt cldr classes into the build dir execute the generate task :

    `gradle generate -Dcldr.version=<cldr data version>`
    
    the classes will be generated into `build/gwt-cldr`
    
    to generate the classes for a limited set of locales you pass the locale system property
    
    `gradle generate -Dlocale=<comma separated locales> -Dcldr.version=<cldr data version>`
    
    to generate the classes into the gwt-cldr repository use 
    
    `gradle generate -Dcldr.version=<cldr data version> -Dgwt.cldr.root=<local gwt cldr repo root folder>`
    
    
> CLDR version can be a tag name or a branch from the new github repository https://github.com/unicode-org/cldr


### Generated data:

- Currency Lists
- Locales Native Names
- Number Constants
- List Formatting
- Localized Names
- DateTime Format Info

    
