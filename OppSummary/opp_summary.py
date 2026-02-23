#!/usr/bin/env python3
"""
OppSummary — Interactive CLI for analyzing Salesforce opportunities.
"""

import json
import os
import sys
import termios
import tty

from tabulate import tabulate


def _load_config() -> dict:
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    with open(config_path, "r") as f:
        return json.load(f)


def _read_single_key() -> str:
    """Read one character from stdin without requiring Enter (cbreak mode)."""
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setcbreak(fd)
        ch = sys.stdin.read(1)
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)
    return ch


def _prompt_mode() -> str:
    """Prompt user to choose Open or Closed mode. Returns 'open' or 'closed'."""
    while True:
        print("Analyze (O)pen or (C)losed opportunities? [O/C]: ", end="", flush=True)
        ch = _read_single_key().lower()
        print(ch)
        if ch == "o":
            return "open"
        if ch == "c":
            return "closed"
        print("  Please press O for Open or C for Closed.")


def _get_opp_type(opp: dict) -> str:
    stage = opp.get("StageName", "")
    if stage == "Closed Lost":
        return "Closed Lost"
    if stage == "Closed Won":
        return "Closed Won"
    return "Open"


def _display_opp_list(opps: list[dict], mode: str, counts: dict | None = None) -> None:
    def _cnt(opp_id: str, key: str) -> str:
        if counts is None:
            return "-"
        return str(counts.get(opp_id, {}).get(key, 0))

    if mode == "open":
        rows = [
            [
                i + 1,
                o.get("Name", ""),
                o.get("StageName", ""),
                o.get("CloseDate", ""),
                f"${o['Amount']:,.0f}" if o.get("Amount") else "N/A",
                _cnt(o["Id"], "cases"),
                _cnt(o["Id"], "emails"),
                _cnt(o["Id"], "jira"),
            ]
            for i, o in enumerate(opps)
        ]
        headers = ["#", "Name", "Stage", "Close Date", "Amount", "Cases", "Emails", "Jira"]
    else:
        rows = [
            [
                i + 1,
                o.get("Name", "") + (" (Won)" if o.get("StageName") == "Closed Won" else " (Lost)"),
                o.get("CloseDate", ""),
                f"${o['Amount']:,.0f}" if o.get("Amount") else "N/A",
                _cnt(o["Id"], "cases"),
                _cnt(o["Id"], "emails"),
                _cnt(o["Id"], "jira"),
            ]
            for i, o in enumerate(opps)
        ]
        headers = ["#", "Name", "Close Date", "Amount", "Cases", "Emails", "Jira"]
    print()
    print(tabulate(rows, headers=headers, tablefmt="simple"))
    print()


def _prompt_opp_choice(count: int):
    """Prompt until valid int in [1..count] or 'q'. Returns 0-based index or None."""
    while True:
        raw = input(f"Select opportunity [1-{count}] or q to quit: ").strip().lower()
        if raw == "q":
            return None
        try:
            n = int(raw)
            if 1 <= n <= count:
                return n - 1
        except ValueError:
            pass
        print(f"  Please enter a number between 1 and {count}, or q.")


def _step(label: str, result_str: str) -> None:
    print(f"  {label:<30} {result_str}", flush=True)


def _gather_and_display(opp: dict, collector, config: dict, mode: str) -> dict:
    print(f"\nGathering data for: {opp.get('Name', opp.get('Id', ''))}")

    # Products
    try:
        products = collector.get_opp_products(opp["Id"])
        _step("Products...", f"OK ({len(products)} line items)")
    except Exception as e:
        products = []
        _step("Products...", f"ERROR: {e}")

    # Cases
    max_cases = config.get("max_cases_per_opp", 10)
    try:
        all_cases = collector.get_opp_cases(opp["Id"])
        total_cases = len(all_cases)
        cases = all_cases[:max_cases]
        if total_cases > max_cases:
            _step("Cases...", f"OK ({total_cases} found, using top {max_cases})")
        else:
            _step("Cases...", f"OK ({total_cases} found)")
    except Exception as e:
        cases = []
        total_cases = 0
        _step("Cases...", f"ERROR: {e}")

    case_ids = [c["CaseId"] for c in cases if c.get("CaseId")]

    # Emails
    emails = []
    if case_ids:
        try:
            emails = collector.get_case_emails(case_ids)
            _step("Emails...", f"OK ({len(emails)})")
        except Exception as e:
            _step("Emails...", f"ERROR: {e}")
    else:
        _step("Emails...", "skipped (no cases)")

    # Case comments
    comments = []
    if case_ids:
        try:
            comments = collector.get_case_comments(case_ids)
            _step("Case comments...", f"OK ({len(comments)})")
        except Exception as e:
            _step("Case comments...", f"ERROR: {e}")
    else:
        _step("Case comments...", "skipped (no cases)")

    # Jira
    jira_keys = collector._extract_jira_keys(cases)
    jira_issues = []
    jira_comments = []
    if jira_keys:
        try:
            jira_issues = collector.get_jira_issues(jira_keys)
            _step("Jira tickets...", f"OK ({len(jira_issues)} issues)")
        except Exception as e:
            _step("Jira tickets...", f"ERROR: {e}")
        try:
            jira_comments = collector.get_jira_comments(jira_keys)
            # update previous line would be nicer but keep it simple
            _step("Jira comments...", f"OK ({len(jira_comments)} comments)")
        except Exception as e:
            _step("Jira comments...", f"ERROR: {e}")
    else:
        _step("Jira tickets...", "skipped (no Jira refs in cases)")

    # Sync trials
    sync_trials = []
    try:
        sync_trials = collector.get_sync_trials(opp["Id"])
        _step("Sync trials...", f"OK ({len(sync_trials)} rows)")
    except Exception as e:
        _step("Sync trials...", f"ERROR: {e}")

    # Cloud trials
    cloud_trials = []
    try:
        cloud_trials = collector.get_cloud_trials(opp["Id"])
        _step("Connect Cloud trials...", f"OK ({len(cloud_trials)} rows)")
    except Exception as e:
        _step("Connect Cloud trials...", f"ERROR: {e}")

    return {
        "opp": opp,
        "opp_type": _get_opp_type(opp),
        "products": products,
        "cases": cases,
        "total_cases_found": total_cases,
        "emails": emails,
        "comments": comments,
        "jira_issues": jira_issues,
        "jira_comments": jira_comments,
        "sync_trials": sync_trials,
        "cloud_trials": cloud_trials,
        "errors": [],
    }


def main():
    # Load config
    try:
        config = _load_config()
    except Exception as e:
        print(f"Error loading config.json: {e}")
        sys.exit(1)

    # Instantiate DataCollector
    print("Connecting to Salesforce and Jira...", flush=True)
    try:
        from data_collector import DataCollector
        collector = DataCollector(config)
        print("Connected.\n")
    except Exception as e:
        print(f"Connection error: {e}")
        sys.exit(1)

    # Instantiate Analyzer (validates API key early)
    try:
        from analyzer import Analyzer
        analyzer = Analyzer(config)
    except RuntimeError as e:
        print(f"Configuration error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Error initializing analyzer: {e}")
        sys.exit(1)

    # Mode selection
    mode = _prompt_mode()
    print()

    # Main loop
    while True:
        if mode == "open":
            print("Fetching Open opportunities closing this quarter...", flush=True)
            label = "this quarter"
            fetch_fn = collector.get_open_opps_this_quarter
        else:
            print("Fetching Closed opportunities from current and previous quarter...", flush=True)
            label = "current and previous quarter"
            fetch_fn = collector.get_closed_opps_recent_quarters

        try:
            opps = fetch_fn()
        except Exception as e:
            print(f"Error fetching opportunities: {e}")
            sys.exit(1)

        if not opps:
            print(f"No {'Open' if mode == 'open' else 'Closed'} Sync/Connect Cloud opportunities found for {label}.")
            sys.exit(0)

        print(f"Found {len(opps)} opportunities. Fetching preview counts...", flush=True)
        try:
            counts = collector.get_preview_counts([o["Id"] for o in opps])
        except Exception:
            counts = None
        _display_opp_list(opps, mode, counts)

        choice = _prompt_opp_choice(len(opps))
        if choice is None:
            print("Goodbye.")
            break

        selected_opp = opps[choice]

        # Gather data with per-step progress
        data = _gather_and_display(selected_opp, collector, config, mode)

        if data["errors"]:
            print(f"\nWarnings: {'; '.join(data['errors'])}")

        # Call Claude
        print("\nCalling Claude for analysis...", flush=True)
        banner = "=" * 70
        try:
            analysis = analyzer.analyze(data)
            print(f"\n{banner}")
            print(f"ANALYSIS: {selected_opp.get('Name', '')}")
            print(banner)
            print(analysis)
            print(banner)
        except Exception as e:
            print(f"Error calling Claude: {e}")

        # Continue?
        print("\nAnalyze another opportunity? [Y/N]: ", end="", flush=True)
        again = _read_single_key().lower()
        print(again)
        if again != "y":
            print("Goodbye.")
            break


if __name__ == "__main__":
    main()
