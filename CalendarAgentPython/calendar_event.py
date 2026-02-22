from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional, List, Any


@dataclass
class CalendarEvent:
    subject: str
    start: Optional[datetime]
    end: Optional[datetime]
    is_recurring: bool
    organizer_name: str

    @classmethod
    def from_row(cls, row: tuple, columns: List[str]) -> "CalendarEvent":
        """Create a CalendarEvent from a DB row tuple and column name list."""
        row_dict = dict(zip(columns, row))

        subject = row_dict.get("Subject") or row_dict.get("subject") or ""
        is_recurring = bool(row_dict.get("IsRecurring") or row_dict.get("is_recurring") or False)
        organizer_name = row_dict.get("OrganizerName") or row_dict.get("organizer_name") or ""

        start = cls._parse_datetime(row_dict.get("Start") or row_dict.get("start"))
        end = cls._parse_datetime(row_dict.get("End") or row_dict.get("end"))

        return cls(
            subject=subject,
            start=start,
            end=end,
            is_recurring=is_recurring,
            organizer_name=organizer_name,
        )

    @staticmethod
    def _parse_datetime(value: Any) -> Optional[datetime]:
        if value is None:
            return None
        if isinstance(value, datetime):
            return value
        if isinstance(value, str):
            for fmt in (
                "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%dT%H:%M:%S",
                "%Y-%m-%d %H:%M:%S.%f",
                "%Y-%m-%dT%H:%M:%S.%f",
                "%m/%d/%Y %H:%M:%S",
                "%m/%d/%Y %I:%M:%S %p",
            ):
                try:
                    return datetime.strptime(value, fmt)
                except ValueError:
                    continue
        return None

    def __str__(self) -> str:
        start_str = self.start.strftime("%H:%M") if self.start else "?"
        end_str = self.end.strftime("%H:%M") if self.end else "?"
        return f"{start_str}-{end_str}: {self.subject}"
