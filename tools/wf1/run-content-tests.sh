#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/../.."
WF1_JAVA_BIN=${WF1_JAVA_BIN:-$(ls -d /tmp/claude-0/-home-user-Clashweave/*/scratchpad/jdk25/zulu*/bin | head -1)}
WF1_GSON=${WF1_GSON:-/root/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.13.2/48b8230771e573b54ce6e867a9001e75977fe78e/gson-2.13.2.jar}
WF1_JUNIT=${WF1_JUNIT:-/opt/gradle-8.14.3/lib/junit-4.13.2.jar}
WF1_HAMCREST=${WF1_HAMCREST:-$(ls /opt/gradle-8.14.3/lib/hamcrest-core*.jar | head -1)}
WF1_CLASSES=$(mktemp -d /tmp/wf1a-classes.XXXXXX)
trap 'rm -rf "$WF1_CLASSES"' EXIT
WF1_CP="$WF1_GSON:$WF1_JUNIT:$WF1_HAMCREST"
# Pure-logic tests only: skip tests that need Minecraft, Forge, netty or ASM on the classpath.
PURE_TESTS=$(grep -L -E 'net\.minecraft|clashweave\.(forge|network|coremod|client)|io\.netty|org\.objectweb' \
  src/test/java/com/layue13/clashweave/core/*Test.java | tr '\n' ' ')
env -u JAVA_TOOL_OPTIONS "$WF1_JAVA_BIN/javac" --release 8 -cp "$WF1_CP" -d "$WF1_CLASSES" \
  src/main/java/com/layue13/clashweave/core/*.java \
  src/main/java/com/layue13/clashweave/content/*.java \
  src/test/java/com/layue13/clashweave/content/*.java \
  $PURE_TESTS
env -u JAVA_TOOL_OPTIONS "$WF1_JAVA_BIN/java" \
  -cp "$WF1_CLASSES:$WF1_CP:src/main/resources:src/test/resources" \
  org.junit.runner.JUnitCore $(ls src/test/java/com/layue13/clashweave/content/*Test.java $PURE_TESTS | sed 's#^src/test/java/##' | sed 's/\.java$//; s#/#.#g' | tr '\n' ' ')
