# XJS Exporter

XJS Exporter is a tool for converting legacy XJS format journal entries to JSON and generating HTML journal entries from the JSON data.

## Features

- Convert legacy XJS format to JSON intermediate format
- Generate HTML journal entries from JSON data
- Add plaintext content with metadata through a web interface
- Browse entries by person, category, or year
- View attachments and images

## Getting Started

### Running the Application

1. Start the application using the provided `run.bat` script or using Maven:
   ```
   mvn spring-boot:run
   ```

2. The application will start in command-line mode with Spring Shell.

### Using the Web Interface

The application now includes a web interface for adding plaintext content with metadata:

1. Access the web interface at: http://localhost:8080/entry-form
2. Fill in the form with your journal entry details:
   - Title (required)
   - Date Created (required)
   - Location (optional)
   - Persons (comma-separated list)
   - Categories (comma-separated list)
   - Content (required plaintext content)
3. Click "Save Entry" to save the entry to the JSON intermediate format

### Generating HTML from JSON

After adding entries through the web interface or converting from XJS format, you can generate HTML:

1. In the command-line shell, use the `generate` command:
   ```
   generate
   ```

2. This will process all JSON entries and generate HTML files in the `OUT` directory.

3. You can specify custom paths for the intermediate data and output:
   ```
   generate --intermediatePath=C:\path\to\intermediate-data\ --targetPath=C:\path\to\output\
   ```

## Directory Structure

- `intermediate-data/` - Contains the JSON intermediate format files
  - `entries/` - Individual entry JSON files
  - `metadata/` - Metadata JSON files (people, categories, attachments)
- `OUT/` - Contains the generated HTML files
  - `entries/` - Individual entry HTML files
  - `persons/` - Person-filtered entry lists
  - `categories/` - Category-filtered entry lists
  - `years/` - Year-filtered entry lists
  - `lists/` - Index lists for persons, categories, and years
  - `attachments/` - Copied attachment files

## Adding Content

You can add content to the system in two ways:

1. **From Legacy XJS Format**: Use the exporter functionality to convert XJS files to JSON
2. **From Plaintext**: Use the web interface at `/entry-form` to add plaintext content with metadata

## Viewing Content

After generating HTML, open the `OUT/index.html` file in a web browser to browse all entries.