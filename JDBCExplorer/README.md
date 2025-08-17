# JDBCExplorer

A database exploration tool that supports multiple database types through JDBC drivers and CData Python libraries.

## Overview

JDBCExplorer provides an interactive SQL client for exploring and querying various databases including:
- Salesforce
- JIRA
- Snowflake
- Google BigQuery
- NetSuite
- And more...

## Two Versions Available

### 1. Groovy Version (Original)
- **Location**: Root directory
- **Technology**: Groovy with JDBC drivers
- **Run with**: `groovy ConsolePrompt.groovy`
- **Compile with**: `make all`

### 2. Python Version (New)
- **Location**: `PythonExplorer/` directory
- **Technology**: Python with CData Python libraries
- **Run with**: `python run_python_explorer.py` or `cd PythonExplorer && python console_prompt.py`
- **Install dependencies**: `cd PythonExplorer && pip install -r requirements.txt`

## Quick Start

### Initial Setup
```bash
# Set up your connections.json file (if not already done)
python3 setup_connections.py

# Edit connections.json with your database credentials
# The file is excluded from git for security
```

### Groovy Version
```bash
# Compile the application
make all

# Run the application
groovy ConsolePrompt.groovy

# Show available databases
sql >show databases

# Connect to a database
sql >use salesforce
```

### Python Version
```bash
# Install dependencies
cd PythonExplorer
pip3 install -r requirements.txt

# Run the application
python3 console_prompt.py

# Or use the launcher from the root directory
python3 run_python_explorer.py
```

## Configuration

Both versions use the same `connections.json` file in the root directory for database configuration. The Python version automatically finds and uses the connections.json file from the parent directory, avoiding duplication. The Python version automatically parses JDBC connection strings and converts them to Python connector properties.

## Features

- **Interactive SQL client** with command-line interface
- **Parameterized queries** using `:parameter` syntax
- **Metadata exploration** (tables, columns, keys, foreign keys)
- **Performance testing** with multi-threading support
- **Batch operations** for multiple SQL commands
- **CSV export** for metadata and query results
- **Multiple database support** through CData connectors

## Key Differences

| Feature | Groovy Version | Python Version |
|---------|----------------|----------------|
| Technology | Groovy + JDBC | Python + CData |
| Dependencies | Groovy, JDBC drivers | Python, CData libraries |
| Result Display | Custom formatting | Pandas + Tabulate |
| Error Handling | Basic | Enhanced with type hints |
| Performance | Good | Excellent with pandas |

## File Structure

```
JDBCExplorer/
├── *.groovy              # Groovy version files
├── makefile              # Build script for Groovy version
├── connections.json      # Database configuration
├── run_python_explorer.py # Python version launcher
├── README.md             # This file
└── PythonExplorer/       # Python version
    ├── *.py              # Python application files
    ├── requirements.txt  # Python dependencies
    ├── README.md         # Python version documentation
    └── test_python_app.py # Test script
```

## Testing

### Test Python Version
```bash
cd PythonExplorer
python3 test_python_app.py
```

## Support

- **Groovy Version**: Original implementation with JDBC drivers
- **Python Version**: Modern implementation with CData Python libraries

Both versions provide the same core functionality but with different technology stacks.
