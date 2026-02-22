# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Python port of the CalendarAgent — an interactive CLI that queries an Exchange calendar via a CData JDBC-to-Python connector, finds free time slots within work hours, and walks the user through accepting/rejecting them.

## Running

The makefile uses the shared venv at `/Users/amits/code/venv`:

```bash
make run     # python calendar_agent.py
make test    # python test_exchange.py
```

Or directly with the venv active:
```bash
source /Users/amits/code/venv/bin/activate
python calendar_agent.py
python test_exchange.py
```

## Configuration

`config.json` (not git-ignored here) contains:
- `connectionsFile` — **absolute path** to `JDBCExplorer/connections.json` (git-ignored, holds credentials)
- `exchangeConnectionName` — name of the Exchange entry in that file
- `workHours`, `availabilityHours`, `maxHoursPerDay`, `minDaysRequired`, `weeksToLookAhead`

There is no LLM-based date parsing here; `date_parser.py` is purely rule-based (unlike the Groovy version which optionally calls the Anthropic API).

## Architecture

**Entry point:** `calendar_agent.py` — interactive loop that prompts for a start date, displays each free slot with before/after context, and collects user confirmations. Enforces `maxHoursPerDay` and `minDaysRequired` constraints.

**`CalendarDataAccess`** — reads `config.json`, then **dynamically adds `../JDBCExplorer/PythonExplorer/` to `sys.path`** and imports `ConnectionManager` from there. This is the key cross-project dependency: if the repo layout changes, update `config.json`'s `connectionsFile` and the relative path in `calendar_data_access.py:17`.

**`AvailabilityFinder`** — queries `Calendar` table via the CData connector, gaps free time within work hours day by day, and finds the nearest events before/after each slot for context display.

**`CalendarEvent`** — dataclass with a `from_row()` factory that handles both camelCase and snake_case column names and multiple datetime string formats.

**`date_parser.py`** — standalone module (no imports beyond stdlib) that parses inputs like `"next week"`, `"3 days"`, `"Nov 11"`, `"2024-11-11"` into a `date`. Defaults to tomorrow on parse failure.

## Tests

`test_exchange.py` contains four tests run sequentially (no test framework needed):
- `test_config_load` — validates required keys in config.json (no live connection)
- `test_connect_to_exchange` — live Exchange connection test
- `test_calendar_query` — runs a real SQL query and prints sample rows
- `test_calendar_event_parse` — unit test for `CalendarEvent.from_row`, no connection needed

To run a single test: import and call it directly, e.g. `python -c "from test_exchange import test_calendar_event_parse; test_calendar_event_parse()"`.
