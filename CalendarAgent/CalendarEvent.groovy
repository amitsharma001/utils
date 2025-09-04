import java.time.*

class CalendarEvent {
    String subject
    LocalDateTime start
    LocalDateTime end
    String location
    boolean isAllDayEvent
    boolean isCancelled
    boolean isMeeting
    String timeZone
    String duration
    boolean isRecurring
    String organizerName
    
    CalendarEvent() {}
    
    CalendarEvent(Map row) {
        this.subject = row.Subject ?: ""
        this.location = ""
        this.isAllDayEvent = false
        this.isCancelled = false
        this.isMeeting = true
        this.timeZone = ""
        this.duration = ""
        this.isRecurring = row.IsRecurring ?: false
        this.organizerName = row.OrganizerName ?: ""
        
        // Parse start and end times
        if (row.Start) {
            this.start = parseDateTime(row.Start)
        }
        if (row.End) {
            this.end = parseDateTime(row.End)
        }
    }
    
    private LocalDateTime parseDateTime(def dateTime) {
        if (dateTime instanceof String) {
            return LocalDateTime.parse(dateTime.replace(' ', 'T'))
        } else if (dateTime instanceof java.sql.Timestamp) {
            return dateTime.toLocalDateTime()
        } else if (dateTime instanceof java.util.Date) {
            return dateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
        return null
    }
    
    boolean isInTimeRange(LocalDateTime from, LocalDateTime to) {
        if (!start || !end) return false
        return !(end.isBefore(from) || start.isAfter(to))
    }
    
    boolean isInDateRange(LocalDate from, LocalDate to) {
        if (!start) return false
        LocalDate eventDate = start.toLocalDate()
        return !(eventDate.isBefore(from) || eventDate.isAfter(to))
    }
    
    @Override
    String toString() {
        return "CalendarEvent[subject='${subject}', start=${start}, end=${end}, location='${location}', isRecurring=${isRecurring}, organizer='${organizerName}']"
    }
}
