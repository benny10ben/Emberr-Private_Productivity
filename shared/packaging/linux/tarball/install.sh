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
SYMLINK="$BIN_DIR/$PACKAGE_NAME"
DESKTOP_ENTRY="$DESKTOP_ENTRY_DIR/$PACKAGE_NAME.desktop"

SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE_APP_DIR="$SOURCE_DIR/$PACKAGE_NAME"
SOURCE_ICON="$SOURCE_DIR/$PACKAGE_NAME.png"

if [ ! -f "$SOURCE_APP_DIR/bin/$APP_NAME" ] || [ ! -f "$SOURCE_ICON" ]; then
    echo "Error: run this script from inside the extracted $PACKAGE_NAME folder." >&2
    exit 1
fi

if [ -d "$APP_DIR" ]; then
    echo "Removing the previous install at $APP_DIR"
    rm -rf "$APP_DIR"
fi

echo "Installing $APP_NAME $APP_VERSION to $APP_DIR"
mkdir -p "$APP_DIR" "$DESKTOP_ENTRY_DIR" "$ICON_DIR" "$BIN_DIR"
cp -R "$SOURCE_APP_DIR/." "$APP_DIR/"

chmod +x "$LAUNCHER"
if [ -d "$APP_DIR/lib/runtime/bin" ]; then
    find "$APP_DIR/lib/runtime/bin" -type f -exec chmod +x {} +
fi
if [ -f "$APP_DIR/lib/runtime/lib/jspawnhelper" ]; then
    chmod +x "$APP_DIR/lib/runtime/lib/jspawnhelper"
fi

cp "$SOURCE_ICON" "$ICON_DIR/$PACKAGE_NAME.png"

cat > "$DESKTOP_ENTRY" <<DESKTOP_ENTRY_CONTENT
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Minimalist offline-first notes and daily reminders
Exec=$LAUNCHER
Icon=$PACKAGE_NAME
Terminal=false
Categories=$MENU_CATEGORY;
StartupNotify=true
StartupWMClass=$WINDOW_CLASS
DESKTOP_ENTRY_CONTENT

ln -sfn "$LAUNCHER" "$SYMLINK"

if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DESKTOP_ENTRY_DIR" > /dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache > /dev/null 2>&1; then
    gtk-update-icon-cache --ignore-theme-index --quiet "$ICON_THEME_DIR" > /dev/null 2>&1 || true
fi

echo "Done."
echo "  Application  $APP_DIR"
echo "  Menu entry   $DESKTOP_ENTRY"
echo "  Terminal     $SYMLINK"

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
