# Building SuperStorage

Requirements: JDK 25, Gradle 9.1+ (internet needed the first time to fetch FlatLaf).

## 1. Build the jar
    gradle clean build
Output: `build/libs/SuperStorage-1.0.0.jar` (self-contained, includes FlatLaf).
Test it: `java -jar build/libs/SuperStorage-1.0.0.jar`

## 2. Make a Windows app (no Java needed on the user's PC)
Run from the project folder (needs the JDK's `jpackage`):

    jpackage --type app-image --name SuperStorage ^
      --input build/libs --main-jar SuperStorage-1.0.0.jar ^
      --main-class superstorage.SuperStorage ^
      --app-version 1.0.0 --vendor JacobProjects ^
      --copyright "JacobProjects SuperStorage 2026 Do not distribute" ^
      --description "SuperStorage" --icon icon.ico

Add `--type exe --win-menu --win-shortcut --win-dir-chooser` instead of
`--type app-image` to produce an installer (requires WiX Toolset 3.x installed).
`icon.ico` is optional - drop your own .ico next to build.gradle or remove `--icon`.

(On PowerShell use the backtick ` instead of ^ for line breaks, or put it on one line.)

## 3. Before you hand it out
- Unsigned .exe files trigger Windows SmartScreen warnings. A code-signing certificate fixes that.
- Ship THIRD_PARTY_NOTICES.txt alongside the app (FlatLaf's Apache 2.0 license requires attribution).
- Test on a clean Windows machine, including the UAC "restart as administrator" prompt.
