#!/bin/bash

set -e

# Update Repo
git pull
git submodule update --init --remote --recursive
git submodule status

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
java -jar build/libs/field-guide*.jar -i Modpack-Modern -o output

# Congratulation
echo "✅ Build Success"