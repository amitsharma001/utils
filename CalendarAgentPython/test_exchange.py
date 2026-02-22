#!/usr/bin/env python3
"""Connectivity and functionality tests for CalendarAgentPython.
Run with: python test_exchange.py
"""

import os
import sys
import traceback
from datetime import datetime


def test_config_load():
    """Test that config.json loads with required keys."""
    import json
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    with open(config_path) as f:
        config = json.load(f)

    required = ["workHours", "availabilityHours", "maxHoursPerDay",
                "minDaysRequired", "weeksToLookAhead",
                "connectionsFile", "exchangeConnectionName"]
    missing = [k for k in required if k not in config]
    assert not missing, f"Missing keys: {missing}"
    print(f"  config keys OK: {list(config.keys())}")


def test_connect_to_exchange():
    """Test that ConnectionManager connects without error."""
    from calendar_data_access import CalendarDataAccess
    da = CalendarDataAccess()
    cursor = da.get_cursor()
    assert cursor is not None
    cursor.close()
    da.close()
    print("  Connection established and closed successfully.")


def test_calendar_query():
    """Test a basic calendar query returns >= 0 rows and prints first 3."""
    from calendar_data_access import CalendarDataAccess
    da = CalendarDataAccess()
    cursor = da.get_cursor()
    cursor.execute("SELECT Subject, Start, End FROM Calendar")
    columns = [desc[0] for desc in cursor.description]
    rows = cursor.fetchmany(3)
    cursor.close()
    da.close()

    print(f"  Columns: {columns}")
    print(f"  First {len(rows)} row(s):")
    for row in rows:
        print(f"    {row}")
    assert isinstance(rows, list)


def test_calendar_event_parse():
    """Test CalendarEvent.from_row with a sample row."""
    from calendar_event import CalendarEvent

    columns = ["Subject", "Start", "End", "IsRecurring", "OrganizerName"]
    row = ("Team Standup", "2024-11-11 09:00:00", "2024-11-11 09:30:00", False, "Alice")
    event = CalendarEvent.from_row(row, columns)

    assert event.subject == "Team Standup"
    assert event.start == datetime(2024, 11, 11, 9, 0, 0)
    assert event.end == datetime(2024, 11, 11, 9, 30, 0)
    assert event.is_recurring is False
    assert event.organizer_name == "Alice"
    print(f"  Parsed: {event}")


TESTS = [
    test_config_load,
    test_connect_to_exchange,
    test_calendar_query,
    test_calendar_event_parse,
]


def run_all():
    passed = 0
    failed = 0
    for test in TESTS:
        name = test.__name__
        print(f"\n[{name}]")
        try:
            test()
            print(f"PASS: {name}")
            passed += 1
        except Exception as e:
            print(f"FAIL: {name} — {e}")
            traceback.print_exc()
            failed += 1

    print(f"\n{'='*40}")
    print(f"Results: {passed} passed, {failed} failed")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    run_all()
