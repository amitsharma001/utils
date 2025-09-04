# Calendar Availability Finder

A simple Groovy console application that finds available time slots in your Exchange calendar.

## Features

- **Interactive confirmation**: User confirms each time slot before selection
- **Context awareness**: Shows meetings 30 minutes before and after each slot
- **Multi-day distribution**: Enforces minimum days and maximum hours per day
- **Configurable limits**: 
  - Minimum total hours (default: 6)
  - Maximum hours per day (default: 3)
  - Minimum days required (default: 3)
- **Smart day progression**: Automatically skips days when limit reached
- **Exchange integration**: Connects to Exchange calendar via JDBC
- **Clean output**: Shows multiple slots per day on single lines

## Quick Start

1. **Configure**: Update `config.json` with your settings
2. **Run**: `groovy runCalendarAvailabilityFinder.groovy`

## Configuration

Edit `config.json` to customize:

```json
{
  "workHours": {
    "start": "09:00",
    "end": "18:00"
  },
  "availabilityHours": 6,
  "maxHoursPerDay": 3,
  "minDaysRequired": 3,
  "weeksToLookAhead": 3,
  "connectionsFile": "/Users/amits/code/utils/JDBCExplorer/connections.json",
  "exchangeConnectionName": "exchange"
}
```

## Output Format

The program now provides an interactive experience:

```
Finding available time slots with context...
Loaded 42 events for analysis

=== Available Time Slots ===
Looking for 6 hours spread across multiple days
You can select up to 3 hours per day. Once you reach 3 hours for a day, remaining slots for that day will be skipped.
You must select at least 3 days to meet the requirements.

📅 Monday (9/1): 10:30 AM to 11:30 AM (1h)
Before: Daily Standup Meeting
After: Product & Technology Leadership Sync

Accept this slot? (y/n): y
✅ Slot accepted! (1h added, 1h total for Monday)

📅 Monday (9/1): 12:30 PM to 4:30 PM (4h)
Before: Product & Technology Leadership Sync
After: Weekly Project Sync

Accept this slot? (y/n): y
✅ Slot accepted! (4h added, 5h total for Monday)

⏭️  Skipping Monday (9/1) - already selected 5 hours for this day

📅 Tuesday (9/2): 2:00 PM to 3:00 PM (1h)
Before: Internal Meeting
After: Senior Leadership Weekly

Accept this slot? (y/n): y
✅ Slot accepted! (1h added, 1h total for Tuesday)

=== Final Selection ===
Selected 3 slots totaling 6 hours across 2 days:
Monday (9/1): 10:30 AM to 11:30 AM and 12:30 PM to 4:30 PM (5h)
Tuesday (9/2): 2:00 PM to 3:00 PM (1h)
```

## Requirements

- Groovy
- Exchange JDBC driver
- Connection configuration in `/Users/amits/code/utils/JDBCExplorer/connections.json`

## Files

- `runCalendarAvailabilityFinder.groovy` - Main execution script

- `CalendarAvailabilityFinder.groovy` - Core application logic
- `CalendarDataAccess.groovy` - Database access layer
- `CalendarEvent.groovy` - Event data model
- `config.json` - Configuration settings
