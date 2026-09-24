#!/bin/sh
# Installs @APP_NAME@ @APP_VERSION@ for the current user only.
# No root, no package manager. Your notes in ~/.emberr are never touched.

set -eu

APP_NAME="@APP_NAME@"
PACKAGE_NAME="@PACKAGE_NAME@"
APP_VERSION="@APP_VERSION@"
WINDOW_CLASS="@WINDOW_CLASS@"
MENU_CATEGORY="@MENU_CATEGORY@"
ICON_SIZE="@ICON_SIZE@"

FORCE_CLOSE=0
LAUNCH_AFTER=0
WAIT_FOR_PID=""
while [ $# -gt 0 ]; do
    case "$1" in
        --force) FORCE_CLOSE=1 ;;
        --launch-after) LAUNCH_AFTER=1 ;;
        --wait-for-pid)
            if [ $# -lt 2 ]; then
                echo "Error: --wait-for-pid needs a process id." >&2
                exit 1
            fi
            WAIT_FOR_PID="$2"
            shift
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: $0 [--force]" >&2
            exit 1
            ;;
    esac
    shift
done

if [ -z "${HOME:-}" ]; then
    echo "Error: HOME is not set, so there is nowhere to install to." >&2
    exit 1
fi

DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
APP_DIR="$DATA_HOME/$PACKAGE_NAME"
DESKTOP_ENTRY_DIR="$DATA_HOME/applications"
ICON_THEME_DIR="$DATA_HOME/icons/hicolor"
ICON_DIR="$ICON_THEME_DIR/${ICON_SIZE}x${ICON_SIZE}/apps"
BIN_DIR="$HOME/.local/bin"

LAUNCHER="$APP_DIR/bin/$APP_NAME"
WRAPPER="$BIN_DIR/$PACKAGE_NAME"
DESKTOP_ENTRY="$DESKTOP_ENTRY_DIR/$PACKAGE_NAME.desktop"

SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE_APP_DIR="$SOURCE_DIR/$PACKAGE_NAME"
SOURCE_ICON="$SOURCE_DIR/$PACKAGE_NAME.png"

if [ ! -f "$SOURCE_APP_DIR/bin/$APP_NAME" ] || [ ! -f "$SOURCE_ICON" ]; then
    echo "Error: run this script from inside the extracted $PACKAGE_NAME folder." >&2
    exit 1
fi

running_app_process_ids() {
    if ! command -v pgrep > /dev/null 2>&1; then
        return 0
    fi
    pgrep -f "^$LAUNCHER" 2>/dev/null || true
}

close_running_app() {
    echo "Closing $APP_NAME"
    kill $(running_app_process_ids) 2>/dev/null || true

    attempts=0
    while [ "$attempts" -lt 100 ] && [ -n "$(running_app_process_ids)" ]; do
        sleep 0.1
        attempts=$((attempts + 1))
    done

    if [ -n "$(running_app_process_ids)" ]; then
        echo "It did not close in ten seconds, stopping it the hard way."
        kill -9 $(running_app_process_ids) 2>/dev/null || true
        sleep 1
    fi
}

wait_for_updating_app_to_exit() {
    echo "Waiting for $APP_NAME to close"
    attempts=0
    while kill -0 "$WAIT_FOR_PID" 2>/dev/null; do
        if [ "$attempts" -ge 600 ]; then
            echo "Error: $APP_NAME was still running after a minute, so nothing was changed." >&2
            exit 1
        fi
        sleep 0.1
        attempts=$((attempts + 1))
    done
}

if [ -n "$WAIT_FOR_PID" ]; then
    wait_for_updating_app_to_exit
fi

if [ -n "$(running_app_process_ids)" ] && [ "$FORCE_CLOSE" -eq 0 ]; then
    echo "Error: $APP_NAME is running, so the old version would stay in memory." >&2
    echo "Quit it from the tray icon, then run this script again." >&2
    echo "Or run \"$0 --force\" to close it and install in one go." >&2
    exit 1
fi

NEW_APP_DIR="$APP_DIR.new"
OLD_APP_DIR="$APP_DIR.old"

echo "Installing $APP_NAME $APP_VERSION to $APP_DIR"
rm -rf "$NEW_APP_DIR" "$OLD_APP_DIR"
mkdir -p "$NEW_APP_DIR" "$DESKTOP_ENTRY_DIR" "$ICON_DIR" "$BIN_DIR"
cp -R "$SOURCE_APP_DIR/." "$NEW_APP_DIR/"

chmod +x "$NEW_APP_DIR/bin/$APP_NAME"
if [ -d "$NEW_APP_DIR/lib/runtime/bin" ]; then
    find "$NEW_APP_DIR/lib/runtime/bin" -type f -exec chmod +x {} +
fi
if [ -f "$NEW_APP_DIR/lib/runtime/lib/jspawnhelper" ]; then
    chmod +x "$NEW_APP_DIR/lib/runtime/lib/jspawnhelper"
fi

if [ -n "$(running_app_process_ids)" ]; then
    if [ "$FORCE_CLOSE" -eq 0 ]; then
        rm -rf "$NEW_APP_DIR"
        echo "Error: $APP_NAME was opened again during the install, so nothing was changed." >&2
        exit 1
    fi
    close_running_app
fi

if [ -d "$APP_DIR" ]; then
    echo "Replacing the previous install at $APP_DIR"
    mv "$APP_DIR" "$OLD_APP_DIR"
fi
mv "$NEW_APP_DIR" "$APP_DIR"
rm -rf "$OLD_APP_DIR"

cp "$SOURCE_ICON" "$ICON_DIR/$PACKAGE_NAME.png"

cat > "$DESKTOP_ENTRY" <<DESKTOP_ENTRY_CONTENT
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Minimalist offline-first notes and daily reminders
Exec=$WRAPPER
Icon=$PACKAGE_NAME
Terminal=false
Categories=$MENU_CATEGORY;
StartupNotify=true
StartupWMClass=$WINDOW_CLASS
DESKTOP_ENTRY_CONTENT

rm -f "$WRAPPER"
cat > "$WRAPPER" <<WRAPPER_CONTENT
#!/bin/sh
MALLOC_ARENA_MAX=2
export MALLOC_ARENA_MAX

STARTUP_CACHE_DIR="\${XDG_CACHE_HOME:-\${HOME:-/tmp}/.cache}/$PACKAGE_NAME"
STARTUP_ARCHIVE="\$STARTUP_CACHE_DIR/startup-classes.jsa"
mkdir -p "\$STARTUP_CACHE_DIR" 2>/dev/null
if [ -f "\$STARTUP_ARCHIVE" ] && [ ! -s "\$STARTUP_ARCHIVE" ]; then
    rm -f "\$STARTUP_ARCHIVE"
fi
JAVA_TOOL_OPTIONS="\${JAVA_TOOL_OPTIONS:+\$JAVA_TOOL_OPTIONS }-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=\$STARTUP_ARCHIVE"
export JAVA_TOOL_OPTIONS

exec "$LAUNCHER" "\$@"
WRAPPER_CONTENT
chmod +x "$WRAPPER"

if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DESKTOP_ENTRY_DIR" > /dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache > /dev/null 2>&1; then
    gtk-update-icon-cache --ignore-theme-index --quiet "$ICON_THEME_DIR" > /dev/null 2>&1 || true
fi

echo "Done."
echo "  Application  $APP_DIR"
echo "  Menu entry   $DESKTOP_ENTRY"
echo "  Terminal     $WRAPPER"

case ":$PATH:" in
    *":$BIN_DIR:"*)
        echo "Run it with: $PACKAGE_NAME"
        ;;
    *)
        echo
        echo "Note: $BIN_DIR is not on your PATH, so typing $PACKAGE_NAME will not work yet."
        echo "Add this line to your shell profile, then open a new terminal:"
        echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
        ;;
esac

if [ "$LAUNCH_AFTER" -eq 1 ]; then
    echo "Starting $APP_NAME"
    nohup "$WRAPPER" > /dev/null 2>&1 &
fi
