# PrintWizard 🧙

New kind of tool, records every events that occurs in a program execution and let you explore them interactively.

## run PrintWizard

- Instrument Java Program. Compile the java class with javac and our plugin, see : *runPlugin.sh*
- Run program. Run with java command and add path to logging module to classPath, see *script.sh*
- Run frontend. In *./Frontend*, change *ProjectFile* path to the path where the program was executed, then run *server.js* using node. 

## compile PrintWizard

- Compile PrintWizard with maven, it will also execute tests.
- Compile frontend with typescript, example : `npx tsc -w`

## Setup
- openjdk 21.0.3
- Maven 3.9.6
- node v21.7.3