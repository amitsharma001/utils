import re
from datetime import date, timedelta


def parse_start_date(user_input: str) -> date:
    """Parse a natural language date string into a date object.

    Supports:
    - Empty / 'tomorrow'  → tomorrow
    - 'next week'         → Monday of next week
    - 'N days'            → today + N days
    - Explicit dates: 'Nov 11', '2024-11-11', '11/15', 'November 11 2024', ...
    Defaults to tomorrow if parsing fails.
    """
    today = date.today()

    if not user_input or not user_input.strip():
        return today + timedelta(days=1)

    normalized = user_input.strip().lower()

    # tomorrow
    if normalized == "tomorrow":
        return today + timedelta(days=1)

    # "next week" → Monday of next week
    if normalized == "next week":
        days_until_monday = (7 - today.weekday()) % 7
        days_until_monday = days_until_monday if days_until_monday != 0 else 7
        return today + timedelta(days=days_until_monday)

    # "N days" / "N day"
    m = re.match(r"^(\d+)\s+days?$", normalized)
    if m:
        return today + timedelta(days=int(m.group(1)))

    # ISO date: "2024-11-11"
    m = re.match(r"^(\d{4})-(\d{2})-(\d{2})$", normalized)
    if m:
        try:
            return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        except ValueError:
            pass

    # Numeric dates: "11/15" or "11/15/2024"
    m = re.match(r"^(\d{1,2})/(\d{1,2})(?:/(\d{4}))?$", normalized)
    if m:
        try:
            year = int(m.group(3)) if m.group(3) else today.year
            return date(year, int(m.group(1)), int(m.group(2)))
        except ValueError:
            pass

    # Month-name dates: "Nov 11", "November 11", "Nov 11 2024", "November 11 2024"
    month_map = {
        "jan": 1, "january": 1,
        "feb": 2, "february": 2,
        "mar": 3, "march": 3,
        "apr": 4, "april": 4,
        "may": 5,
        "jun": 6, "june": 6,
        "jul": 7, "july": 7,
        "aug": 8, "august": 8,
        "sep": 9, "september": 9,
        "oct": 10, "october": 10,
        "nov": 11, "november": 11,
        "dec": 12, "december": 12,
    }
    m = re.match(r"^([a-z]+)\s+(\d{1,2})(?:\s+(\d{4}))?$", normalized)
    if m:
        month_name = m.group(1)
        month_num = month_map.get(month_name)
        if month_num:
            try:
                year = int(m.group(3)) if m.group(3) else today.year
                return date(year, month_num, int(m.group(2)))
            except ValueError:
                pass

    # Default: tomorrow
    return today + timedelta(days=1)
