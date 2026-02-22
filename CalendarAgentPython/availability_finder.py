from datetime import date, datetime, time, timedelta
from typing import List, Optional, Tuple

from calendar_event import CalendarEvent
from calendar_data_access import CalendarDataAccess


# A gap in the schedule: (start_datetime, end_datetime, duration_hours)
AvailableSlot = Tuple[datetime, datetime, float]
# A dated slot: (date, start_datetime, end_datetime, duration_hours)
DatedSlot = Tuple[date, datetime, datetime, float]


class AvailabilityFinder:
    def __init__(self, data_access: CalendarDataAccess):
        self._da = data_access
        self._config = data_access.config

        work_start_str = self._config["workHours"]["start"]
        work_end_str = self._config["workHours"]["end"]
        h, m = map(int, work_start_str.split(":"))
        self._work_start = time(h, m)
        h, m = map(int, work_end_str.split(":"))
        self._work_end = time(h, m)

    # ------------------------------------------------------------------
    # Data loading
    # ------------------------------------------------------------------

    def load_events(self, start_date: date, end_date: date) -> List[CalendarEvent]:
        query = (
            "SELECT Subject, Start, End, IsRecurring, OrganizerName "
            "FROM Calendar "
            f"WHERE Start >= '{start_date}' AND End <= '{end_date}' "
            "ORDER BY Start ASC"
        )
        events = []
        try:
            cursor = self._da.get_cursor()
            cursor.execute(query)
            columns = [desc[0] for desc in cursor.description]
            for row in cursor.fetchall():
                event = CalendarEvent.from_row(row, columns)
                if event.start and event.end:
                    events.append(event)
            cursor.close()
            print(f"Loaded {len(events)} events from {start_date} to {end_date}")
        except Exception as e:
            print(f"Error loading events: {e}")
        return events

    # ------------------------------------------------------------------
    # Slot finding
    # ------------------------------------------------------------------

    def get_available_slots_for_day(
        self, target_date: date, events: List[CalendarEvent]
    ) -> List[AvailableSlot]:
        """Return free gaps on target_date within work hours."""
        work_start_dt = datetime.combine(target_date, self._work_start)
        work_end_dt = datetime.combine(target_date, self._work_end)

        sorted_events = sorted(
            [e for e in events if e.start and e.end], key=lambda e: e.start
        )

        slots: List[AvailableSlot] = []
        current = work_start_dt

        for event in sorted_events:
            if current < event.start:
                gap_hours = (event.start - current).total_seconds() / 3600
                if gap_hours > 0:
                    slots.append((current, event.start, gap_hours))
            if event.end > current:
                current = event.end

        # Gap after last event
        if current < work_end_dt:
            gap_hours = (work_end_dt - current).total_seconds() / 3600
            if gap_hours > 0:
                slots.append((current, work_end_dt, gap_hours))

        return slots

    def get_all_available_slots(
        self, start_date: date, weeks_ahead: int
    ) -> List[DatedSlot]:
        """Collect all free slots from start_date across weeks_ahead weeks."""
        end_date = start_date + timedelta(weeks=weeks_ahead)
        all_events = self.load_events(start_date, end_date)

        # Group events by date
        events_by_date: dict = {}
        for event in all_events:
            d = event.start.date()
            events_by_date.setdefault(d, []).append(event)

        dated_slots: List[DatedSlot] = []
        for d, day_events in sorted(events_by_date.items()):
            for slot_start, slot_end, hours in self.get_available_slots_for_day(d, day_events):
                dated_slots.append((d, slot_start, slot_end, hours))

        return dated_slots

    # ------------------------------------------------------------------
    # Context lookup
    # ------------------------------------------------------------------

    def get_slot_context(
        self, slot_start: datetime, slot_end: datetime, all_events: List[CalendarEvent]
    ) -> Tuple[Optional[CalendarEvent], Optional[CalendarEvent]]:
        """Return (before_event, after_event) within 30 min of the slot."""
        before_cutoff = slot_start - timedelta(minutes=30)
        after_cutoff = slot_end + timedelta(minutes=30)

        before_event = next(
            (
                e
                for e in all_events
                if e.start and e.end
                and e.start < slot_start
                and e.end > before_cutoff
            ),
            None,
        )
        after_event = next(
            (
                e
                for e in all_events
                if e.start and e.end
                and e.start < after_cutoff
                and e.end > slot_end
            ),
            None,
        )
        return before_event, after_event
