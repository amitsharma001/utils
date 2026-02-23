# JDBCExplorer Python Edition

This is a Python port of the original Groovy-based JDBCExplorer application. It provides the same functionality for exploring and querying databases using CData Python libraries instead of JDBC drivers.

## Features

- **Multi-database support**: Connect to Salesforce, JIRA, Snowflake, Google BigQuery, NetSuite, and more
- **Interactive SQL client**: Execute SELECT, INSERT, UPDATE, DELETE statements
- **Parameterized queries**: Use `:parameter` syntax for dynamic queries
- **Metadata exploration**: View tables, columns, keys, and foreign keys
- **Performance testing**: Run queries with multiple threads and measure performance
- **Batch operations**: Execute multiple SQL commands in a batch
- **CSV export**: Export metadata and query results to CSV files

## Installation

1. **Install Python dependencies**:
   ```bash
   pip3 install -r requirements.txt
   ```

2. **Install CData Python libraries** (if not already installed):
   ```bash
   pip3 install cdata-salesforce cdata-jira cdata-snowflake cdata-googlebigquery cdata-netsuite
   ```

## Configuration

The application uses the same `connections.json` file as the original Groovy version. The Python version automatically parses JDBC connection strings and converts them to the appropriate Python connector properties.

### Example connections.json:
```json
{
    "config": {
        "logdir": "/path/to/logs",
        "libdir": "/path/to/lib",
        "verbosity": "3",
        "pagesize": "100"
    },
    "connections": [
        {
            "name": "salesforce",
            "connection": "jdbc:salesforce:CredentialsLocation='';InitiateOAuth=GETANDREFRESH;OAuthSettingsLocation='/path/to/oauth.txt';",
            "driver": "cdata.jdbc.salesforce.SalesforceDriver"
        }
    ]
}
```

**Note**: The Python version uses the `connections.json` file from the parent directory (JDBCExplorer root). This avoids duplication and ensures both Groovy and Python versions use the same configuration. The `connections.json` file is excluded from git for security reasons.

## Usage

### Basic Usage

1. **Start the application**:
   ```bash
   python3 console_prompt.py
   ```

2. **Show available databases**:
   ```
   sql >show databases
   ```

3. **Connect to a database**:
   ```
   sql >use salesforce
   ```

4. **Execute SQL queries**:
   ```
   salesforce >SELECT * FROM Account LIMIT 10
   ```

### Command Line Options

- `-f, --file`: Specify a custom connections file (default: uses connections.json from parent directory)
- `-c, --connection`: Auto-connect to a specific database
- `-h, --help`: Show help information

Example:
```bash
python3 console_prompt.py -f /path/to/custom_connections.json -c salesforce
```

### Available Commands

#### Database Commands
- `show databases` - List available database connections
- `use [connection_name]` - Connect to a specific database
- `close` - Close the current connection

#### SQL Commands
- `SELECT ...` - Execute SELECT queries
- `INSERT ...` - Execute INSERT statements
- `UPDATE ...` - Execute UPDATE statements
- `DELETE ...` - Execute DELETE statements
- `start batch` - Start batch command mode

#### Metadata Commands
- `show tables` - List all tables
- `describe [table]` - Show columns for a table
- `keys [table]` - Show primary keys for a table
- `fkeys [table]` - Show foreign keys for a table
- `show tablestats` - Show table statistics
- `write metadata` - Export metadata to CSV files

#### Utility Commands
- `performance` - Run performance tests
- `config [name] [value]` - Change configuration settings
- `help` - Show help information
- `exit`, `quit`, `q` - Exit the application

### Parameterized Queries

Use `:parameter` syntax for dynamic queries:

```
salesforce >SELECT * FROM Account WHERE Name = :name
name: Acme Corp
```

### Performance Testing

```
salesforce >performance
Query: SELECT * FROM Account
Runs Default(3): 5
Threads Default(1): 2
Row Progress Interval Default(25000): 10000
```

### Batch Operations

```
salesforce >start batch
batchcmd >INSERT INTO Account (Name, Type) VALUES ('Test1', 'Customer')
batchcmd >INSERT INTO Account (Name, Type) VALUES ('Test2', 'Prospect')
batchcmd >end
```

## Architecture

The Python version consists of several modules that mirror the original Groovy classes:

- **`connection_manager.py`**: Handles database connections and JDBC string parsing
- **`sql_command.py`**: Executes SQL queries and handles parameters
- **`result_set_helper.py`**: Formats and displays query results
- **`metadata_helper.py`**: Provides database metadata operations
- **`performance_test.py`**: Runs performance tests with threading
- **`console_prompt.py`**: Main application entry point

## JDBC to Python Conversion

The application automatically converts JDBC connection strings to Python connector properties:

1. **Parse JDBC string**: Extract key-value pairs from the connection string
2. **Map drivers**: Convert JDBC driver names to Python module names
3. **Create connection**: Use the appropriate CData Python connector

Example conversion:
```
JDBC: jdbc:salesforce:CredentialsLocation='';InitiateOAuth=GETANDREFRESH;
Python: cdata.salesforce.connect(CredentialsLocation='', InitiateOAuth='GETANDREFRESH')
```

## Error Handling

The application includes comprehensive error handling:
- Connection failures are reported with detailed error messages
- SQL errors are caught and displayed
- Invalid commands are handled gracefully
- Keyboard interrupts are handled properly

## Logging

Performance test results are automatically logged to files in the configured log directory:
- Format: `{driver_name}.perf.txt`
- Location: Specified in `connections.json` config.logdir

## Differences from Groovy Version

1. **Native Python libraries**: Uses CData Python connectors instead of JDBC drivers
2. **Pandas integration**: Uses pandas DataFrames for result handling
3. **Modern Python features**: Type hints, f-strings, context managers
4. **Enhanced formatting**: Uses tabulate library for better table display
5. **Improved error handling**: More detailed error messages and exception handling

## Troubleshooting

### Common Issues

1. **Import errors**: Ensure all CData Python libraries are installed
2. **Connection failures**: Check OAuth settings and credentials
3. **Permission errors**: Verify file permissions for log directories
4. **Memory issues**: Reduce page size for large result sets

### Debug Mode

Enable verbose logging by setting `verbosity` in the config section of `connections.json`.

## License

This project maintains the same license as the original JDBCExplorer application.
