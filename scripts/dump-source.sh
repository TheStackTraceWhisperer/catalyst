#!/bin/bash

# Check if a directory is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <directory>"
  exit 1
fi

# The directory to search for .java files
SEARCH_DIR=$1
# Output file
OUTPUT_FILE="source_dump.md"

# Clear the output file if it exists
> "$OUTPUT_FILE"

# Find all .java files and process them
find "$SEARCH_DIR" -name "*.java" | while read -r file; do
  echo "Processing $file..."
  # Append the file path as a header
  echo "# \`$file\`" >> "$OUTPUT_FILE"
  # Append the file content in a Java code block
  echo "\`\`\`java" >> "$OUTPUT_FILE"
  cat "$file" >> "$OUTPUT_FILE"
  echo "\`\`\`" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
done

echo "Markdown file '$OUTPUT_FILE' created successfully."
