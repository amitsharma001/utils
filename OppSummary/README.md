# OppSummary

Interactive CLI for analyzing Closed Lost Salesforce opportunities for CData Sync and Connect Cloud. Pulls data from Salesforce, Jira, and trial telemetry, then generates a structured analysis using Claude.

## Prerequisites

- Python 3.11+
- CData Salesforce and Jira Python connectors installed in your venv (same ones used by JDBCExplorer/PythonExplorer)
- `JDBCExplorer/connections.json` configured with `sf` and `jira` connections
- Anthropic API key

## Setup

### 1. Set your API key

Add to `~/.zshrc` or `~/.bashrc`:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Then restart your shell or run `source ~/.zshrc`.

### 2. Install Python dependencies

If you already have a venv for JDBCExplorer/PythonExplorer with the CData connectors:

```bash
cd /path/to/JDBCExplorer/PythonExplorer
source venv/bin/activate
pip install anthropic>=0.25.0 tabulate>=0.9.0 pandas>=1.5.0
```

Or create a new venv:

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
# Then install cdata-salesforce and cdata-jira connectors manually
```

### 3. Configure connections

Edit `config.json` to match your connection names in `connections.json`:

```json
{
  "sf_connection": "sf",
  "jira_connection": "jira",
  "connections_file": "/path/to/JDBCExplorer/connections.json"
}
```

## Usage

```bash
cd OppSummary
python opp_summary.py
```

The CLI will:
1. Display a numbered list of Closed Lost Sync/Connect Cloud opportunities
2. Prompt you to select one
3. Print per-step data gathering progress
4. Call Claude to generate a structured analysis covering:
   - Why the deal was lost
   - Product gaps and bugs
   - Engagement quality
   - Trial health
   - Competitive/pricing signals
   - Systemic issues

## Configuration options (`config.json`)

| Key | Default | Description |
|-----|---------|-------------|
| `sf_connection` | `"sf"` | Name of Salesforce connection in connections.json |
| `jira_connection` | `"jira"` | Name of Jira connection in connections.json |
| `connections_file` | absolute path | Path to connections.json |
| `claude_model` | `"claude-sonnet-4-6"` | Claude model to use |
| `closed_lost_since` | `"2026-01-01"` | Only show opps closed after this date |
| `max_cases_per_opp` | `10` | Max support cases to include per opp |
| `max_emails_per_case` | `5` | Max emails to include per case |
| `max_comments_per_case` | `5` | Max case comments per case |
| `max_jira_comments` | `20` | Max Jira comments total across all issues |
| `max_trial_rows` | `10` | Max trial rows for Sync and Cloud each |
