#!/bin/sh
# Starts Novel-Grabber-Claude. Without arguments it opens the window, with arguments it runs
# the command line version, e.g.  ./novel-grabber-claude.sh -link https://host.com/novel/ -chapters 1 5
REQUIRED_JAVA=25
APP_DIR=$(cd "$(dirname "$0")" && pwd)

# Use JAVA_HOME when it points to a Java installation, otherwise java from the PATH.
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

# "java -version" prints e.g.: openjdk version "25.0.1" 2025-10-21  (Java 8: "1.8.0_451")
version=$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
if [ -z "$version" ]; then
    echo "Java was not found. Novel-Grabber-Claude needs Java $REQUIRED_JAVA or newer." >&2
    echo "Install it with your package manager or from https://adoptium.net/" >&2
    exit 1
fi
major=${version%%[.+_-]*}
case $major in
    ''|*[!0-9]*) major=0 ;;
esac
if [ "$major" -lt "$REQUIRED_JAVA" ]; then
    echo "Novel-Grabber-Claude needs Java $REQUIRED_JAVA or newer, but found Java $version ($JAVA)." >&2
    echo "Install a newer Java with your package manager or from https://adoptium.net/" >&2
    exit 1
fi

exec "$JAVA" -jar "$APP_DIR/Novel-Grabber-Claude.jar" "$@"
