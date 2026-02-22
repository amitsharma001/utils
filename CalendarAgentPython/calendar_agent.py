#!/usr/bin/env python3
"""Interactive Calendar Availability Finder (Python version)."""

from datetime import date, timedelta

import date_parser
from calendar_data_access import CalendarDataAccess
from availability_finder import AvailabilityFinder


def format_time(dt) -> str:
    return dt.strftime("%I:%M %p").lstrip("0")


def format_day(d: date) -> str:
    return d.strftime("%A (%m/%d)").replace("(0", "(")


def main():
    print("Finding available time slots with context...")

    data_access = CalendarDataAccess()
    config = data_access.config
    finder = AvailabilityFinder(data_access)

    max_hours_per_day = config.get("maxHoursPerDay", 3)
    min_days_required = config.get("minDaysRequired", 3)
    total_hours_needed = config["availabilityHours"]
    weeks_ahead = config["weeksToLookAhead"]

    # Ask for start date
    print("\nFrom which date onwards should slots be picked?")
    print("Examples: 'next week', '3 days', 'Nov 11', '2024-11-11' (or press Enter for tomorrow)")
    raw_input = input("Enter start date: ").strip()
    start_date = date_parser.parse_start_date(raw_input)
    print(f"Searching for slots starting from {start_date.strftime('%A, %b %d, %Y')}")

    # Load all events for context
    scan_end = start_date + timedelta(weeks=weeks_ahead)
    all_events = finder.load_events(start_date, scan_end)
    print(f"Loaded {len(all_events)} events for analysis")

    # Get all available slots
    all_dated_slots = finder.get_all_available_slots(start_date, weeks_ahead)

    # Filter to on/after start_date (already done by get_all_available_slots, but be safe)
    all_dated_slots = [(d, s, e, h) for d, s, e, h in all_dated_slots if d >= start_date]

    if not all_dated_slots:
        print("No available slots found in the look-ahead window.")
        data_access.close()
        return

    print(f"\n=== Available Time Slots ===")
    print(f"Looking for {total_hours_needed} hours spread across multiple days")
    print(f"You can select up to {max_hours_per_day} hours per day. Once you reach {max_hours_per_day} hours for a day, remaining slots for that day will be skipped.")
    print(f"You must select at least {min_days_required} days to meet the requirements.")
    print()

    confirmed_slots = []
    hours_per_day: dict = {}
    total_hours_selected = 0.0

    for d, slot_start, slot_end, hours in all_dated_slots:
        # Stop condition: enough hours AND enough days
        if total_hours_selected >= total_hours_needed and len(hours_per_day) >= min_days_required:
            print(f"Required hours ({total_hours_needed}) and minimum days ({min_days_required}) reached. Stopping.")
            break

        current_day_hours = hours_per_day.get(d, 0.0)

        # Skip if this day is already at max
        if current_day_hours >= max_hours_per_day:
            day_label = format_day(d)
            print(f"Skipping {day_label} - already selected {current_day_hours:.0f}h for this day")
            continue

        # If we have enough hours but need more days, skip days already used
        if total_hours_selected >= total_hours_needed and len(hours_per_day) < min_days_required:
            if d in hours_per_day:
                day_label = format_day(d)
                print(f"Skipping {day_label} - need more days, already used this day")
                continue

        # Display the slot with context
        day_label = format_day(d)
        start_str = format_time(slot_start)
        end_str = format_time(slot_end)
        hours_display = f"{hours:.0f}h" if hours == int(hours) else f"{hours:.1f}h"

        before_event, after_event = finder.get_slot_context(slot_start, slot_end, all_events)

        print(f"📅 {day_label}")
        if before_event:
            print(f"Before:   {format_time(before_event.start)} - {format_time(before_event.end)}: {before_event.subject}")
        else:
            print("Before:   Free")

        print(f"Proposed: {start_str} - {end_str} ({hours_display})")

        if after_event:
            print(f"After:    {format_time(after_event.start)} - {format_time(after_event.end)}: {after_event.subject}")
        else:
            print("After:    Free")

        user_input = input("Accept this slot? (y/n/done): ").strip().lower()
        print()

        if user_input in ("done", "d"):
            print("Done selecting slots.")
            break
        elif user_input in ("y", "yes"):
            confirmed_slots.append((d, slot_start, slot_end, hours))
            total_hours_selected += hours
            hours_per_day[d] = current_day_hours + hours
            day_total = hours_per_day[d]
            print(f"Slot accepted! ({hours_display} added, {day_total:.0f}h total for {d.strftime('%A')})")
        else:
            print("Slot rejected.")

    # Final summary
    if confirmed_slots:
        print("\n=== Final Selection ===")
        days_used = len(hours_per_day)
        print(f"Selected {len(confirmed_slots)} slots totaling {total_hours_selected:.0f}h across {days_used} days:")

        # Group by date
        slots_by_date: dict = {}
        for d, slot_start, slot_end, hours in confirmed_slots:
            slots_by_date.setdefault(d, []).append((slot_start, slot_end, hours))

        for d in sorted(slots_by_date):
            day_label = f"{d.strftime('%A')} ({d.strftime('%-m/%-d')})"
            day_slots = slots_by_date[d]
            slot_strs = [f"{format_time(s)} to {format_time(e)}" for s, e, _ in day_slots]
            day_hours = hours_per_day[d]
            print(f"{day_label}: {' and '.join(slot_strs)} ({day_hours:.0f}h)")
    else:
        print("\nNo slots were selected.")

    data_access.close()


if __name__ == "__main__":
    main()
