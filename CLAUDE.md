# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Collection of independent Python and Groovy utilities for data access and analysis. Each tool lives in its own top-level directory and runs via `make`.

## Tools

| Tool                 | Language | Purpose                                    | Run                                  |
|----------------------|----------|--------------------------------------------|--------------------------------------|
| JDBCExplorer         | Groovy   | Interactive SQL REPL via CData JDBC        | `cd JDBCExplorer && make runjava`    |
| PythonExplorer       | Python   | Interactive SQL REPL via CData Python      | `cd PythonExplorer && make run`      |
| CalendarAgentPython  | Python   | Exchange calendar free-slot finder         | `cd CalendarAgentPython && make run` |
| OppSummary           | Python   | Salesforce closed-lost analysis via Claude | `cd OppSummary && make run`          |

## Shared Infrastructure

### connections.json
- Location: `utils/connections.json` (git-ignored)
- Copy from `connections.sample.json`; see `ConnectionsJSONReadme.md`
- Referenced by each tool's `config.json` via absolute path

### Shared Python venv
- Location: `/Users/amits/code/venv` (override with `VENV=... make run`)
- Contains: CData Python connectors, pandas, tabulate, anthropic, colorama
- Every tool's Makefile uses `VENV ?= /Users/amits/code/venv`

### PythonExplorer as shared library
`PythonExplorer/connection_manager.py` is used by all Python tools. Consumer tools bootstrap it at import time:
```python
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'PythonExplorer'))
from connection_manager import ConnectionManager
```

## Architecture

### JDBCExplorer (Groovy)
- `ConnectionManager.groovy` — loads `../connections.json`, selects a connection, exposes a `groovy.sql.Sql` instance
- `ConsolePrompt.groovy` — REPL loop; dispatches commands to `SQLCommand`, `MetaDataHelper`, `PerformanceTest`
- `SQLCommand.groovy` — SELECT/INSERT/UPDATE/DELETE with parameterized query support (`:name` syntax)
- `MetaDataHelper.groovy` — schema exploration (tables, columns, keys, CSV export)
- `PerformanceTest.groovy` — multi-threaded query benchmarking

### PythonExplorer (Python)
Mirrors the Groovy structure. `connection_manager.py` auto-discovers `../connections.json` (one directory up from `PythonExplorer/`).

## Adding a New Python Utility

1. Create `MyTool/` at repo root
2. Add `config.json` with `"connections_file": "/Users/amits/code/utils/connections.json"`
3. Bootstrap PythonExplorer in the file that needs DB access (see pattern above)
4. Add `Makefile` with `run`, `test`, `setup` targets using `VENV ?= /Users/amits/code/venv`
5. Add `requirements.txt` (omit CData connectors — those are pre-installed in venv)
6. Update this file's Tools table
