import re
from datetime import date, datetime, time, timedelta
from typing import List, Optional, Tuple

from calendar_event import CalendarEvent
from calendar_data_access import CalendarDataAccess


# A slot: (start, end, duration_hours, is_overridable, recurring_event_or_None)
AvailableSlot = Tuple[datetime, datetime, float, bool, Optional["CalendarEvent"]]
# A dated slot: (date, start, end, duration_hours, is_overridable, recurring_event_or_None)
DatedSlot = Tuple[date, datetime, datetime, float, bool, Optional["CalendarEvent"]]


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

        config_free_list = self._config.get("overridableMeetings", [])
        # Parse each entry into (title_pattern, name_pattern_or_None).
        # Format: "Meeting Title (Person Name)" — name in parens is optional.
        self._config_free_patterns: List[Tuple[str, Optional[str]]] = []
        _bracket_re = re.compile(r"^(.*?)\s*\(([^)]+)\)\s*$")
        for entry in config_free_list:
            m = _bracket_re.match(entry.strip())
            if m:
                self._config_free_patterns.append((m.group(1).strip(), m.group(2).strip()))
            else:
                self._config_free_patterns.append((entry.strip(), None))

    def _is_config_free(self, subject: str, organizer_name: str = "") -> bool:
        """Return True if this meeting matches an overridableMeetings entry.

        Each config entry is split into a title (before parens) and an optional
        name (inside parens).  Both are matched as case-insensitive substrings
        (equivalent to *pattern* glob) against the event subject; the name is
        also matched against the organizer name.  Both title AND name must match
        when a name is present.
        """
        if not self._config_free_patterns:
            return False
        subj = (subject or "").strip()
        org = (organizer_name or "").strip()
        for title_pat, name_pat in self._config_free_patterns:
            if not re.search(re.escape(title_pat), subj, re.IGNORECASE):
                continue
            if name_pat is not None:
                name_found = re.search(re.escape(name_pat), subj, re.IGNORECASE) or \
                             re.search(re.escape(name_pat), org, re.IGNORECASE)
                if not name_found:
                    continue
            return True
        return False

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
        """Return selectable slots on target_date within work hours.

        Non-recurring events block time.
        Recurring events listed in overridableMeetings are treated as free time
        (their window is absorbed into adjacent free slots — no user action needed).
        All other recurring events become overridable ~slots~ the user can pick case-by-case.
        """
        work_start_dt = datetime.combine(target_date, self._work_start)
        _6pm = datetime.combine(target_date, time(18, 0))
        work_end_dt = min(datetime.combine(target_date, self._work_end), _6pm)

        fixed = sorted(
            [e for e in events if e.start and e.end and not e.is_recurring],
            key=lambda e: e.start,
        )
        # Recurring events NOT in the config-free list → shown as user-selectable ~ slots
        recurring = sorted(
            [
                e for e in events
                if e.start and e.end and e.is_recurring
                and not self._is_config_free(e.subject or "", e.organizer_name or "")
            ],
            key=lambda e: e.start,
        )
        # Config-free recurring events are simply omitted — their time becomes free windows

        # Find available windows (time not blocked by fixed events)
        available_windows: List[Tuple[datetime, datetime]] = []
        current = work_start_dt
        for event in fixed:
            ev_start = max(event.start, work_start_dt)
            ev_end = min(event.end, work_end_dt)
            if ev_start >= work_end_dt or ev_end <= work_start_dt:
                continue
            if current < ev_start:
                available_windows.append((current, ev_start))
            if ev_end > current:
                current = ev_end
        if current < work_end_dt:
            available_windows.append((current, work_end_dt))

        # Within each window, subdivide by recurring events
        slots: List[AvailableSlot] = []
        for win_start, win_end in available_windows:
            win_recurring = sorted(
                [e for e in recurring if e.start < win_end and e.end > win_start],
                key=lambda e: e.start,
            )
            pos = win_start
            for ev in win_recurring:
                ev_start = max(ev.start, win_start)
                ev_end = min(ev.end, win_end)
                if pos < ev_start:
                    gap_h = (ev_start - pos).total_seconds() / 3600
                    if gap_h > 0:
                        slots.append((pos, ev_start, gap_h, False, None))
                if ev_end > ev_start:
                    rec_h = (ev_end - ev_start).total_seconds() / 3600
                    if rec_h > 0:
                        slots.append((ev_start, ev_end, rec_h, True, ev))
                pos = max(pos, ev_end)
            if pos < win_end:
                gap_h = (win_end - pos).total_seconds() / 3600
                if gap_h > 0:
                    slots.append((pos, win_end, gap_h, False, None))

        return slots

    def get_all_available_slots(
        self, start_date: date, weeks_ahead: int
    ) -> List[DatedSlot]:
        """Collect all slots from start_date across weeks_ahead weeks."""
        end_date = start_date + timedelta(weeks=weeks_ahead)
        all_events = self.load_events(start_date, end_date)

        events_by_date: dict = {}
        for event in all_events:
            d = event.start.date()
            events_by_date.setdefault(d, []).append(event)

        dated_slots: List[DatedSlot] = []
        for d, day_events in sorted(events_by_date.items()):
            for slot in self.get_available_slots_for_day(d, day_events):
                dated_slots.append((d, *slot))

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
