#!/bin/sh

# Gradle start-up script for UNIX.
#
# This is a thin shim around the official Gradle wrapper bootstrap. The actual
# gradle-wrapper.jar must be fetched once before running tests locally (the
# easiest path: `gradle wrapper` inside the project root after installing Gradle
# 8.10+ via Homebrew / sdkman / chocolatey).

set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar not found at $WRAPPER_JAR" >&2
  echo "Run 'gradle wrapper --gradle-version 8.10.2' once to fetch it, then retry." >&2
  exit 1
fi

exec java \
  -Dorg.gradle.appname="$(basename "$0")" \
  -classpath "$WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
