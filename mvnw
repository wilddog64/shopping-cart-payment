#!/bin/sh
# Maven Wrapper script
# Downloads and runs Maven if not already installed

set -e

# Find the project base directory
if [ -z "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
fi

WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

# Read distributionUrl from properties
if [ -f "$WRAPPER_PROPERTIES" ]; then
  distributionUrl=$(grep 'distributionUrl' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
  wrapperUrl=$(grep 'wrapperUrl' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

# Download wrapper jar if not present
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper..."
  if command -v curl > /dev/null 2>&1; then
    curl -sL -o "$WRAPPER_JAR" "$wrapperUrl"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$wrapperUrl"
  else
    echo "Error: curl or wget required"
    exit 1
  fi
fi

# Run Maven via wrapper
exec java $MAVEN_OPTS \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
