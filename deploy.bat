@echo off
setlocal EnableDelayedExpansion

:: Env: set SKIP_BUILD=1 to reuse cli\build\libs\field-guide-tfg-*.jar
::      set SKIP_PAKKU=1 to skip pakku fetch

git pull
if %errorlevel% neq 0 (
    echo ❌ Git pull failed
    exit /b 1
)

git submodule sync
if %errorlevel% neq 0 (
    echo ❌ Git submodule sync failed
    exit /b 1
)

git submodule update --force --init --depth=1 Modpack-Modern
if %errorlevel% neq 0 (
    echo ❌ Git submodule update failed
    exit /b 1
)

if not "%SKIP_PAKKU%"=="1" (
    cd Modpack-Modern
    if %errorlevel% neq 0 (
        echo ❌ Cannot enter Modpack-Modern directory
        exit /b 1
    )
    java -jar pakku.jar fetch
    if %errorlevel% neq 0 (
        echo ❌ pakku.jar Fetch failed
        exit /b 1
    )
    cd ..
) else (
    echo SKIP_PAKKU=1 — using existing Modpack-Modern mods
)

if not "%SKIP_BUILD%"=="1" (
    call gradlew :cli:jar
    if %errorlevel% neq 0 (
        echo ❌ Gradle :cli:jar failed
        exit /b 1
    )
) else (
    echo SKIP_BUILD=1 — looking for existing CLI jar
)

set "CLI_JAR="
for %%i in (cli\build\libs\field-guide-tfg-*.jar) do set "CLI_JAR=%%i"
if not defined CLI_JAR (
    for %%i in (cli\build\libs\field-guide-*.jar) do set "CLI_JAR=%%i"
)
if not defined CLI_JAR (
    echo ❌ No CLI jar under cli\build\libs\ — run: gradlew :cli:jar
    exit /b 1
)
if "%SKIP_BUILD%"=="1" echo Using !CLI_JAR!

if exist output rmdir /s /q output

java -jar "!CLI_JAR!" -i Modpack-Modern -o output
if %errorlevel% neq 0 (
    echo ❌ Field Guide build failed
    exit /b 1
)

echo ✅ Build Success
