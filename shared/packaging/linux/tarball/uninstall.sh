#!/bin/sh
# Removes the @APP_NAME@ install created by install.sh.
# Your notes, vault, models and settings in ~/.emberr are deliberately left alone.

set -eu

APP_NAME="@APP_NAME@"
PACKAGE_NAME="@PACKAGE_NAME@"
ICON_SIZE="@ICON_SIZE@"

if [ -z "${HOME:-}" ]; then
    echo "Error: HOME is not set, so there is nothing to remove." >&2
    exit 1
fi

DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
APP_DIR="$DATA_HOME/$PACKAGE_NAME"
DESKTOP_ENTRY_DIR="$DATA_HOME/applications"
ICON_THEME_DIR="$DATA_HOME/icons/hicolor"
BIN_DIR="$HOME/.local/bin"

LAUNCHER="$APP_DIR/bin/$APP_NAME"
SYMLINK="$BIN_DIR/$PACKAGE_NAME"
DESKTOP_ENTRY="$DESKTOP_ENTRY_DIR/$PACKAGE_NAME.desktop"
ICON="$ICON_THEME_DIR/${ICON_SIZE}x${ICON_SIZE}/apps/$PACKAGE_NAME.png"

if [ -d "$APP_DIR" ]; then
    rm -rf "$APP_DIR"
    echo "Removed $APP_DIR"
fi

if [ -f "$DESKTOP_ENTRY" ]; then
    rm -f "$DESKTOP_ENTRY"
    echo "Removed $DESKTOP_ENTRY"
fi

if [ -f "$ICON" ]; then
    rm -f "$ICON"
    echo "Removed $ICON"
fi

if [ -L "$SYMLINK" ] && [ "$(readlink "$SYMLINK")" = "$LAUNCHER" ]; then
    rm -f "$SYMLINK"
    echo "Removed $SYMLINK"
fi

if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DESKTOP_ENTRY_DIR" > /dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache > /dev/null 2>&1; then
    gtk-update-icon-cache --ignore-theme-index --quiet "$ICON_THEME_DIR" > /dev/null 2>&1 || true
fi

echo "Done. Your notes in $HOME/.emberr were not touched."
