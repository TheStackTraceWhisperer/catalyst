#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

URL="https://github.com/jgraph/drawio-desktop/releases/download/v31.1.8/drawio-x86_64-31.1.8.AppImage"
FILE_PATH="$SCRIPT_DIR/drawio-x86_64-31.1.8.AppImage"

# Download the file directly into the script's directory if it doesn't exist
if [ ! -f "$FILE_PATH" ]; then
    echo "Downloading draw.io AppImage to $SCRIPT_DIR..."
    curl -L -o "$FILE_PATH" "$URL"
fi

# Make it executable
chmod +x "$FILE_PATH"

# Launch in fire-and-forget mode
echo "Launching draw.io..."
nohup "$FILE_PATH" > /dev/null 2>&1 &
disown