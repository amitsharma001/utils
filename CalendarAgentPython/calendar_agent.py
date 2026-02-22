#!/usr/bin/env python3
"""Interactive Calendar Availability Finder (Python version)."""

from collections import defaultdict
from datetime import date, timedelta
from typing import Dict, List, Tuple

import date_parser
from calendar_data_access import CalendarDataAccess
from availability_finder import AvailabilityFinder


def format_time(dt) -> str:
    return dt.strftime("%I:%M %p").lstrip("0")


def format_day(d: date) -> str:
    return d.strftime("%A (%m/%d)").replace("(0", "(")


def display_day_schedule(d: date, day_events: list, day_slots: list) -> list:
    """Print the full day schedule with events and numbered free slots interleaved.

    Returns a list of available (start, end, hours) in the order they were numbered.
    """
    print(f"\n{'='*56}")
    print(f"  {format_day(d)}")
    print(f"{'='*56}")

    # Merge events and free slots into a single timeline sorted by start time
    items = []
    for event in day_events:
        items.append(("event", event.start, event.end, event))
    for s, e, h in day_slots:
        items.append(("slot", s, e, h))
    items.sort(key=lambda x: x[1])

    available_slots = []
    slot_num = 0
    for item in items:
        if item[0] == "event":
            _, start, end, event = item
            label = event.subject if len(event.subject) <= 42 else event.subject[:39] + "..."
            print(f"  {format_time(start):<10} {format_time(end):<10}  {label}")
        else:
            _, start, end, h = item
            slot_num += 1
            hours_display = f"{h:.1f}h" if h != int(h) else f"{h:.0f}h"
            print(f"  {format_time(start):<10} {format_time(end):<10}  [{slot_num}] {hours_display} free")
            available_slots.append((start, end, h))

    return available_slots


def parse_selection(user_input: str, max_idx: int) -> List[int]:
    """Parse space-separated slot numbers into 0-based indices. Deduplicates."""
    indices, seen = [], set()
    for token in user_input.strip().split():
        try:
            n = int(token)
            if 1 <= n <= max_idx and n not in seen:
                indices.append(n - 1)
                seen.add(n)
        except ValueError:
            pass
    return indices


def main():
    data_access = CalendarDataAccess()
    config = data_access.config
    finder = AvailabilityFinder(data_access)

    max_hours_per_day = config.get("maxHoursPerDay", 3)
    min_days_required = config.get("minDaysRequired", 3)
    total_hours_needed = config["availabilityHours"]
    weeks_ahead = config["weeksToLookAhead"]

    print(f"\nGoal: {total_hours_needed}h total, max {max_hours_per_day}h/day, across >= {min_days_required} days")
    print("\nFrom which date should slots be picked?")
    print("Examples: 'next week', '3 days', 'Nov 11', '2024-11-11' (or Enter for tomorrow)")
    raw_input = input("Start date: ").strip()
    start_date = date_parser.parse_start_date(raw_input)
    print(f"Searching from {start_date.strftime('%A, %b %d, %Y')}\n")

    # Load all events once for the entire scan window
    scan_end = start_date + timedelta(weeks=weeks_ahead)
    all_events = finder.load_events(start_date, scan_end)

    # Group and sort events by date
    events_by_date: Dict[date, list] = defaultdict(list)
    for event in all_events:
        if event.start:
            events_by_date[event.start.date()].append(event)
    for d in events_by_date:
        events_by_date[d].sort(key=lambda e: e.start)

    confirmed_slots: List[Tuple] = []
    hours_per_day: Dict[date, float] = {}
    total_hours_selected = 0.0

    for d in sorted(events_by_date.keys()):
        if d < start_date:
            continue
        if total_hours_selected >= total_hours_needed and len(hours_per_day) >= min_days_required:
            print(f"\nGoal reached: {total_hours_selected:.0f}h across {len(hours_per_day)} days.")
            break

        day_events = events_by_date[d]
        day_slots = finder.get_available_slots_for_day(d, day_events)

        if not day_slots:
            continue  # fully booked day, skip silently

        available = display_day_schedule(d, day_events, day_slots)

        progress = (
            f"{total_hours_selected:.1f}/{total_hours_needed}h"
            f" | {len(hours_per_day)}/{min_days_required} days"
            f" | max {max_hours_per_day}h today"
        )
        print(f"\n  Progress: {progress}")
        user_input = input(
            "  Select slots (e.g. '1', '1 2', Enter to skip, 'done' to finish): "
        ).strip()

        if user_input.lower() in ("done", "d"):
            print("Done.")
            break
        if not user_input:
            continue

        selected = parse_selection(user_input, len(available))
        if not selected:
            print("  No valid slot numbers entered.")
            continue

        day_added = 0.0
        for idx in selected:
            s, e, h = available[idx]
            if hours_per_day.get(d, 0.0) + day_added + h > max_hours_per_day:
                print(f"  [{idx + 1}] skipped — would exceed {max_hours_per_day}h daily limit.")
                continue
            confirmed_slots.append((d, s, e, h))
            day_added += h
            total_hours_selected += h

        if day_added > 0:
            hours_per_day[d] = hours_per_day.get(d, 0.0) + day_added
            print(
                f"  Added {day_added:.1f}h"
                f" ({hours_per_day[d]:.1f}h total for {d.strftime('%A')})"
            )

    # Final summary
    if confirmed_slots:
        print(f"\n{'='*56}")
        print(f"  Final Selection — {total_hours_selected:.0f}h across {len(hours_per_day)} day(s)")
        print(f"{'='*56}")
        by_date: Dict[date, list] = defaultdict(list)
        for d, s, e, h in confirmed_slots:
            by_date[d].append((s, e, h))
        for d in sorted(by_date):
            day_label = f"{d.strftime('%A')} ({d.strftime('%-m/%-d')})"
            slot_strs = [f"{format_time(s)} - {format_time(e)}" for s, e, _ in by_date[d]]
            print(f"  {day_label}: {', '.join(slot_strs)}  ({hours_per_day[d]:.0f}h)")
    else:
        print("\nNo slots selected.")

    data_access.close()


if __name__ == "__main__":
    main()
