#!/bin/bash

set -e

# Update Repo
git pull
git submodule sync
git submodule update --force --init --depth=1 Modpack-Modern

# Build Project
./gradlew clean build || {
   echo "❌ Gradle Build Failed"
   exit 1
}

# Fetch Mods
cd Modpack-Modern || exit
java -jar pakku.jar fetch || {
   echo "❌ pakku.jar Fetch failed"
   exit 1
}

# Clean Output
cd ..
rm -rf output

# Build Field Guide TFG
java -jar build/libs/field-guide-tfg*.jar -i Modpack-Modern -o output

# Congratulation
echo "✅ Build Success"