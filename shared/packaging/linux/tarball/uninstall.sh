#!/bin/sh
# Removes the @APP_NAME@ install created by install.sh.
# Your notes, vault, models and settings in ~/.emberr are deliberately left alone.

set -eu

APP_NAME="@APP_NAME@"
PACKAGE_NAME="@PACKAGE_NAME@"
ICON_SIZE="@ICON_SIZE@"

FORCE_CLOSE=0
if [ "${1:-}" = "--force" ]; then
    FORCE_CLOSE=1
fi

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
WRAPPER="$BIN_DIR/$PACKAGE_NAME"
DESKTOP_ENTRY="$DESKTOP_ENTRY_DIR/$PACKAGE_NAME.desktop"
ICON="$ICON_THEME_DIR/${ICON_SIZE}x${ICON_SIZE}/apps/$PACKAGE_NAME.png"
STARTUP_CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/$PACKAGE_NAME"

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

if [ -n "$(running_app_process_ids)" ] && [ "$FORCE_CLOSE" -eq 0 ]; then
    echo "Error: $APP_NAME is still running." >&2
    echo "Quit it from the tray icon, then run this script again." >&2
    echo "Or run \"$0 --force\" to close it and uninstall in one go." >&2
    exit 1
fi

if [ -n "$(running_app_process_ids)" ]; then
    close_running_app
fi

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

if [ -d "$STARTUP_CACHE_DIR" ]; then
    rm -rf "$STARTUP_CACHE_DIR"
    echo "Removed $STARTUP_CACHE_DIR"
fi

if [ -n "${XDG_RUNTIME_DIR:-}" ] && [ -d "$XDG_RUNTIME_DIR/$PACKAGE_NAME" ]; then
    rm -rf "$XDG_RUNTIME_DIR/$PACKAGE_NAME"
fi

if [ -L "$WRAPPER" ] && [ "$(readlink "$WRAPPER")" = "$LAUNCHER" ]; then
    rm -f "$WRAPPER"
    echo "Removed $WRAPPER"
elif [ -f "$WRAPPER" ] && grep -q "^exec \"$LAUNCHER\"" "$WRAPPER" 2>/dev/null; then
    rm -f "$WRAPPER"
    echo "Removed $WRAPPER"
fi

if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$DESKTOP_ENTRY_DIR" > /dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache > /dev/null 2>&1; then
    gtk-update-icon-cache --ignore-theme-index --quiet "$ICON_THEME_DIR" > /dev/null 2>&1 || true
fi

echo "Done. Your notes in $HOME/.emberr were not touched."
