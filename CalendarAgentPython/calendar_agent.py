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


def merge_contiguous(slots: list) -> list:
    """Merge adjacent or overlapping slots into single blocks.

    Input: list of (start, end, hours, is_overridable, event_or_None)
    Output: list of (start, end, hours, [overridden_subjects])
    """
    if not slots:
        return []
    sorted_slots = sorted(slots, key=lambda s: s[0])
    s0, e0, h0, ov0, ev0 = sorted_slots[0]
    cur = [s0, e0, h0, [ev0.subject] if ov0 and ev0 else []]
    merged = []
    for slot in sorted_slots[1:]:
        s, e, h, is_ov, ev = slot
        if s <= cur[1]:  # contiguous or overlapping
            cur[1] = max(cur[1], e)
            cur[2] += h
            if is_ov and ev:
                cur[3].append(ev.subject)
        else:
            merged.append(tuple(cur))
            cur = [s, e, h, [ev.subject] if is_ov and ev else []]
    merged.append(tuple(cur))
    return merged  # each: (start, end, hours, [override_subjects])


def display_day_schedule(d: date, day_events: list, day_slots: list) -> list:
    """Print the full day schedule with events and numbered slots interleaved.

    day_slots: list of (start, end, hours, is_overridable, event_or_None)
    Non-recurring events are shown as blocked time (no number).
    Recurring events appear as numbered overridable slots marked with ~name~.
    Returns available_slots in display order: (start, end, hours, is_overridable, event).
    """
    print(f"\n{'='*56}")
    print(f"  {format_day(d)}")
    print(f"{'='*56}")

    has_overridable = any(s[3] for s in day_slots)
    if has_overridable:
        print(f"  (~ = recurring meeting, can be overridden)")

    # Only show non-recurring events as blocked time; recurring ones appear as numbered slots
    items = []
    for event in day_events:
        if not event.is_recurring:
            items.append(("event", event.start, event.end, event))
    for slot in day_slots:
        s, e, h, is_ov, ev = slot
        items.append(("slot", s, e, h, is_ov, ev))
    items.sort(key=lambda x: x[1])

    available_slots = []
    slot_num = 0
    for item in items:
        if item[0] == "event":
            _, start, end, event = item
            label = event.subject if len(event.subject) <= 42 else event.subject[:39] + "..."
            print(f"  {format_time(start):<10} {format_time(end):<10}  {label}")
        else:
            _, start, end, h, is_ov, ev = item
            slot_num += 1
            hours_display = f"{h:.1f}h" if h != int(h) else f"{h:.0f}h"
            if is_ov:
                subj = ev.subject if ev else ""
                label = subj if len(subj) <= 32 else subj[:29] + "..."
                print(f"  {format_time(start):<10} {format_time(end):<10}  [{slot_num}] {hours_display} ~{label}~")
            else:
                print(f"  {format_time(start):<10} {format_time(end):<10}  [{slot_num}] {hours_display} free")
            available_slots.append((start, end, h, is_ov, ev))

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

        # Merge contiguous selected slots before applying constraints
        raw_selected = [available[idx] for idx in selected]
        merged_selected = merge_contiguous(raw_selected)

        day_added = 0.0
        for s, e, h, overrides in merged_selected:
            if hours_per_day.get(d, 0.0) + day_added + h > max_hours_per_day:
                print(f"  {format_time(s)}-{format_time(e)} skipped — would exceed {max_hours_per_day}h daily limit.")
                continue
            confirmed_slots.append((d, s, e, h, overrides))
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
        for d, s, e, h, overrides in confirmed_slots:
            by_date[d].append((s, e, h, overrides))
        for d in sorted(by_date):
            day_label = f"{d.strftime('%A')} ({d.strftime('%-m/%-d')})"
            slot_strs = []
            for s, e, h, overrides in by_date[d]:
                time_str = f"{format_time(s)} - {format_time(e)}"
                if overrides:
                    ovr = ", ".join(overrides[:2])
                    if len(overrides) > 2:
                        ovr += f", +{len(overrides) - 2} more"
                    time_str += f"  (~override: {ovr}~)"
                slot_strs.append(time_str)
            print(f"  {day_label}: {', '.join(slot_strs)}  ({hours_per_day[d]:.0f}h)")
    else:
        print("\nNo slots selected.")

    data_access.close()


if __name__ == "__main__":
    main()
