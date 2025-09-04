# Calendar Availability Finder - Implementation Summary

## Overview

A simplified Groovy console application that finds available time slots in Exchange calendars. The application connects to an Exchange database, loads calendar events, and finds multi-day flexible availability based on configured requirements.

## Core Features

### ✅ **Multi-Day Distribution Logic**
- Enforces minimum total hours (default: 6)
- Enforces maximum hours per day (default: 3)
- Enforces minimum days required (default: 3)
- Automatically skips days when daily limit reached
- Continues until both hour and day requirements met

### ✅ **Exchange Integration**
- Connects to Exchange calendar via JDBC
- Uses parameterized queries for security
- Loads events from specified date ranges
- Handles calendar event parsing and validation

### ✅ **Interactive User Experience**
- User confirmation for each time slot
- Context information (30 minutes before/after)
- Shows meeting subjects or "Free" status
- Final summary of selected slots
- AM/PM time format with day/date information

## Architecture

### **Core Classes**

1. **`CalendarAvailabilityFinder`** - Main application orchestrator
   - Handles configuration loading
   - Manages database connections
   - Provides data access layer access

2. **`CalendarDataAccess`** - Database and availability logic
   - Loads calendar events from Exchange
   - Groups events by day
   - Finds available time slots
   - Implements multi-day availability algorithm

3. **`CalendarEvent`** - Data model
   - Represents calendar events
   - Handles date/time parsing
   - Provides event validation

4. **`MultiDayFlexibleAvailability`** - Availability result
   - Contains selected time slots
   - Provides formatted output
   - Tracks total available hours

5. **`SlotContext`** - Context information
   - Shows meetings 30 minutes before/after slots
   - Displays "Free" when no conflicts exist
   - Provides meeting subject information

### **Key Algorithms**

#### **Multi-Day Availability Finding**
```groovy
1. Load events for specified weeks
2. Group events by day
3. Find all available time slots per day
4. Sort slots by duration (largest first)
5. Select optimal combination across days
6. Return formatted availability result
```

#### **Time Slot Selection**
- Greedy algorithm selects largest available slots first
- Respects maximum days constraint (default: 3)
- Combines slots until required hours are met
- Handles partial slot selection when needed

## Configuration

### **config.json Settings**
- `workHours`: Start/end times for availability search
- `availabilityHours`: Total hours needed
- `weeksToLookAhead`: Search period
- `connectionsFile`: JDBC connection configuration path
- `exchangeConnectionName`: Exchange connection name

### **Default Values**
- Work hours: 9:00 AM - 6:00 PM
- Required availability: 6 hours
- Maximum hours per day: 3
- Minimum days required: 3
- Look-ahead period: 3 weeks

## Interactive Output Format

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

## Security Features

- **Parameterized Queries**: Prevents SQL injection
- **Input Validation**: Validates date ranges and configurations
- **Error Handling**: Graceful error handling and reporting

## Usage

```bash
groovy runCalendarAvailabilityFinder.groovy
```

## Dependencies

- **Groovy**: Runtime environment
- **Exchange JDBC Driver**: Database connectivity
- **Connection Configuration**: JDBC connection settings

## Project Status

✅ **Complete**: Core functionality implemented and tested
✅ **Simplified**: Removed unnecessary agent functionality
✅ **Clean**: Focused on essential availability finding
✅ **Secure**: Parameterized queries and proper error handling

