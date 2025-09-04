import groovy.sql.Sql
import java.time.*
import java.time.format.*

class CalendarAvailabilityFinder {
    def sql
    def config
    
    CalendarAvailabilityFinder(Sql sql, def config) {
        this.sql = sql
        this.config = config
    }
    
    // Load events for a specific date range
    List<CalendarEvent> loadEvents(LocalDate fromDate, LocalDate toDate) {
        def events = []
        
        try {
            // Use the specific query format requested with parameterized query
            def query = "SELECT Subject, Start, End, IsRecurring, OrganizerName FROM Calendar WHERE Start >= ? AND End <= ? ORDER BY Start ASC"
            
            def results = sql.rows(query, [fromDate.toString(), toDate.toString()])
            
            results.each { row ->
                def event = new CalendarEvent(row)
                if (event.start && event.end) {
                    events << event
                }
            }
            
            println "Loaded ${events.size()} events from ${fromDate} to ${toDate}"
            
        } catch (Exception e) {
            println "Error loading events: ${e.message}"
        }
        
        return events
    }
    
    // Load events for the next N weeks
    List<CalendarEvent> loadEventsForWeeks(int weeks) {
        def today = LocalDate.now()
        def endDate = today.plusWeeks(weeks)
        return loadEvents(today, endDate)
    }
    
    // Group events by day
    Map<LocalDate, List<CalendarEvent>> groupEventsByDay(List<CalendarEvent> events) {
        def grouped = [:]
        
        events.each { event ->
            if (event.start) {
                def date = event.start.toLocalDate()
                if (!grouped.containsKey(date)) {
                    grouped[date] = []
                }
                grouped[date] << event
            }
        }
        
        return grouped
    }
    
    // Get work hours from config
    LocalTime getWorkStartTime() {
        return LocalTime.parse(config.workHours.start)
    }
    
    LocalTime getWorkEndTime() {
        return LocalTime.parse(config.workHours.end)
    }
    
    // Check if a time is within work hours
    boolean isWithinWorkHours(LocalDateTime dateTime) {
        def time = dateTime.toLocalTime()
        return time.isAfter(getWorkStartTime()) && time.isBefore(getWorkEndTime())
    }
    
        // Get available time slots for a specific day (supports non-contiguous slots)
    List<AvailableTimeSlot> getAvailableTimeSlotsForADay(LocalDate targetDate, List<CalendarEvent> dailyEvents, int requiredTotalHours) {
        def availableSlots = []
        def workStartTime = getWorkStartTime()
        def workEndTime = getWorkEndTime()
        
        // Sort events by start time
        def sortedDailyEvents = dailyEvents.sort { it.start }
        
        // Find all gaps between events (including before first and after last)
        def currentDateTime = targetDate.atTime(workStartTime)
        
        sortedDailyEvents.each { calendarEvent ->
            if (calendarEvent.start && calendarEvent.end) {
                // Check if there's a gap before this event
                if (currentDateTime.isBefore(calendarEvent.start)) {
                    def gapDurationInHours = Duration.between(currentDateTime, calendarEvent.start).toHours()
                    if (gapDurationInHours > 0) {
                        availableSlots << new AvailableTimeSlot(currentDateTime, calendarEvent.start, gapDurationInHours)
                    }
                }
                // Move current time to the end of this event
                if (calendarEvent.end.isAfter(currentDateTime)) {
                    currentDateTime = calendarEvent.end
                }
            }
        }
        
        // Check if there's time after the last event
        if (currentDateTime.isBefore(targetDate.atTime(workEndTime))) {
            def finalGapDurationInHours = Duration.between(currentDateTime, targetDate.atTime(workEndTime)).toHours()
            if (finalGapDurationInHours > 0) {
                availableSlots << new AvailableTimeSlot(currentDateTime, targetDate.atTime(workEndTime), finalGapDurationInHours)
            }
        }
        
        return availableSlots
    }
    

    

    
    // Get all available slots across multiple days (for user selection)
    List<DatedTimeSlot> getAllAvailableSlots(int weeksToLookAhead, int requiredTotalHours) {
        def calendarEvents = loadEventsForWeeks(weeksToLookAhead)
        def eventsByDate = groupEventsByDay(calendarEvents)
        def allAvailableSlots = []
        
        // Collect all available time slots across all days
        eventsByDate.each { dateKey, eventsForDay ->
            def dailySlots = getAvailableTimeSlotsForADay(dateKey, eventsForDay, 1) // Get all slots, even small ones
            dailySlots.each { slot ->
                allAvailableSlots << new DatedTimeSlot(dateKey, slot)
            }
        }
        
        // Sort by date and time
        return allAvailableSlots.sort { a, b -> 
            if (a.date != b.date) {
                return a.date <=> b.date
            }
            return a.timeSlot.startTime <=> b.timeSlot.startTime
        }
    }
    
    // Find multi-day flexible availability (max 3 hours per day, minimum 3 days)
    MultiDayFlexibleAvailability findMultiDayFlexibleAvailability(int weeksToLookAhead, int requiredTotalHours, int minDays = 3, int minSlotsToShow = 7) {
        // Use getAllAvailableSlots to avoid code duplication
        def allAvailableSlots = getAllAvailableSlots(weeksToLookAhead, requiredTotalHours)
        
        // Find optimal combination with 3-hour max per day
        def selectedSlots = selectOptimalMultiDaySlots(allAvailableSlots, requiredTotalHours, minDays)
        
        // If we don't have enough slots, add more from the available slots
        if (selectedSlots.size() < minSlotsToShow && allAvailableSlots.size() >= minSlotsToShow) {
            def additionalSlots = allAvailableSlots.findAll { slot ->
                !selectedSlots.any { selected -> 
                    selected.date == slot.date && selected.timeSlot.startTime == slot.timeSlot.startTime 
                }
            }
            
            // Add additional slots until we reach the minimum
            def slotsToAdd = minSlotsToShow - selectedSlots.size()
            additionalSlots.take(slotsToAdd).each { slot ->
                selectedSlots << slot
            }
            
            // Re-sort by date and time
            selectedSlots.sort { a, b -> 
                if (a.date != b.date) {
                    return a.date <=> b.date
                }
                return a.timeSlot.startTime <=> b.timeSlot.startTime
            }
        }
        
        if (selectedSlots.size() > 0) {
            def totalHours = selectedSlots.sum { it.timeSlot.durationInHours }
            return new MultiDayFlexibleAvailability(selectedSlots, requiredTotalHours, totalHours, true)
        } else {
            // Calculate total available hours across all slots for better error reporting
            def totalAvailableHours = allAvailableSlots ? allAvailableSlots.sum { it.timeSlot.durationInHours } : 0
            println "⚠️  Could only find ${totalAvailableHours} hours in the available ${weeksToLookAhead} weeks (${requiredTotalHours} hours needed)"
            return new MultiDayFlexibleAvailability([], requiredTotalHours, totalAvailableHours, false)
        }
    }
    
    // Select optimal combination with 3-hour max per day
    private List<DatedTimeSlot> selectOptimalMultiDaySlots(List<DatedTimeSlot> allSlots, int requiredHours, int minDays) {
        def selectedSlots = []
        def remainingHoursNeeded = requiredHours
        def usedDays = [] as Set
        def maxHoursPerDay = 3
        
        // Group slots by date for easier processing
        def slotsByDate = allSlots.groupBy { it.date }
        
        // Sort dates by total available hours (descending)
        def sortedDates = slotsByDate.keySet().sort { date ->
            -slotsByDate[date].sum { it.timeSlot.durationInHours }
        }
        

        
        // Take slots from different days until we have enough hours
        for (date in sortedDates) {
            if (remainingHoursNeeded <= 0) break
            
            def daySlots = slotsByDate[date].sort { -it.timeSlot.durationInHours }
            def hoursUsedForDay = 0
            

            
            // Take at most 3 hours from this day
            for (timeSlot in daySlots) {
                if (remainingHoursNeeded <= 0) break
                if (hoursUsedForDay >= maxHoursPerDay) break
                
                def availableHoursForDay = maxHoursPerDay - hoursUsedForDay
                def hoursToTake = Math.min(Math.min(timeSlot.timeSlot.durationInHours as int, remainingHoursNeeded), availableHoursForDay)
                

                
                if (hoursToTake > 0) {
                    if (hoursToTake == timeSlot.timeSlot.durationInHours) {
                        // Take the entire slot
                        selectedSlots << timeSlot
                    } else {
                        // Take partial slot (from the beginning)
                        def partialSlot = new AvailableTimeSlot(
                            timeSlot.timeSlot.startTime, 
                            timeSlot.timeSlot.startTime.plusHours(hoursToTake), 
                            hoursToTake
                        )
                        selectedSlots << new DatedTimeSlot(timeSlot.date, partialSlot)
                    }
                    
                    remainingHoursNeeded -= hoursToTake
                    hoursUsedForDay += hoursToTake
                }
            }
            
            if (hoursUsedForDay > 0) {
                usedDays.add(date)

            }
            
            // If we have enough days and enough hours, we can stop
            if (usedDays.size() >= minDays && remainingHoursNeeded <= 0) break
        }
        
        // Ensure we have at least minDays
        if (usedDays.size() < minDays) {
            return [] // Not enough days available
        }
        
        return selectedSlots.sort { a, b -> 
            if (a.date != b.date) {
                return a.date <=> b.date
            }
            return a.timeSlot.startTime <=> b.timeSlot.startTime
        }
    }
    
    // Get context information for a time slot (30 minutes before and after)
    SlotContext getSlotContext(AvailableTimeSlot slot, List<CalendarEvent> allEvents) {
        def slotStart = slot.startTime
        def slotEnd = slot.endTime
        
        // Find events 30 minutes before the slot
        def beforeTime = slotStart.minusMinutes(30)
        def beforeEvent = allEvents.find { event ->
            event.start && event.end && 
            event.start.isBefore(slotStart) && 
            event.end.isAfter(beforeTime)
        }
        
        // Find events 30 minutes after the slot
        def afterTime = slotEnd.plusMinutes(30)
        def afterEvent = allEvents.find { event ->
            event.start && event.end && 
            event.start.isBefore(afterTime) && 
            event.end.isAfter(slotEnd)
        }
        
        return new SlotContext(beforeEvent, afterEvent)
    }
}

// Represents a single available time slot
class AvailableTimeSlot {
    LocalDateTime startTime
    LocalDateTime endTime
    long durationInHours
    
    AvailableTimeSlot(LocalDateTime startTime, LocalDateTime endTime, long durationInHours) {
        this.startTime = startTime
        this.endTime = endTime
        this.durationInHours = durationInHours
    }
    
    @Override
    String toString() {
        def timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        return "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)} (${durationInHours}h)"
    }
}



// Represents a time slot with its associated date
class DatedTimeSlot {
    LocalDate date
    AvailableTimeSlot timeSlot
    
    DatedTimeSlot(LocalDate date, AvailableTimeSlot timeSlot) {
        this.date = date
        this.timeSlot = timeSlot
    }
    
    @Override
    String toString() {
        def dateFormatter = DateTimeFormatter.ofPattern("MMM dd")
        def timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        return "${date.format(dateFormatter)} ${timeSlot.startTime.format(timeFormatter)}-${timeSlot.endTime.format(timeFormatter)} (${timeSlot.durationInHours}h)"
    }
}

// Represents flexible availability spread across multiple days
class MultiDayFlexibleAvailability {
    List<DatedTimeSlot> selectedTimeSlots
    int requiredTotalHours
    long totalAvailableHours
    boolean hasEnoughTime
    
    MultiDayFlexibleAvailability(List<DatedTimeSlot> selectedTimeSlots, int requiredTotalHours, long totalAvailableHours, boolean hasEnoughTime) {
        this.selectedTimeSlots = selectedTimeSlots
        this.requiredTotalHours = requiredTotalHours
        this.totalAvailableHours = totalAvailableHours
        this.hasEnoughTime = hasEnoughTime
    }
    
    int getNumberOfDaysUsed() {
        return selectedTimeSlots.collect { it.date }.unique().size()
    }
    
    @Override
    String toString() {
        if (!hasEnoughTime) {
            return "❌ Only ${totalAvailableHours}h available across all days (${requiredTotalHours}h needed)"
        }
        
        def result = ""
        
        // Group by date
        def groupedByDate = selectedTimeSlots.groupBy { it.date }
        groupedByDate.each { date, slots ->
            def dayOfWeek = date.format(DateTimeFormatter.ofPattern("EEEE"))
            def monthDay = date.format(DateTimeFormatter.ofPattern("M/d"))
            
            slots.each { slot ->
                def startTime = slot.timeSlot.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                def endTime = slot.timeSlot.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                result += "${dayOfWeek} (${monthDay}): ${startTime} to ${endTime}\n"
            }
        }
        
        return result.trim()
    }
}

// Get context information for a time slot (30 minutes before and after)
class SlotContext {
    CalendarEvent beforeEvent
    CalendarEvent afterEvent
    
    SlotContext(CalendarEvent beforeEvent, CalendarEvent afterEvent) {
        this.beforeEvent = beforeEvent
        this.afterEvent = afterEvent
    }
    
    @Override
    String toString() {
        def result = ""
        
        // Before slot
        if (beforeEvent) {
            def beforeStart = beforeEvent.start.format(DateTimeFormatter.ofPattern("h:mm a"))
            def beforeEnd = beforeEvent.end.format(DateTimeFormatter.ofPattern("h:mm a"))
            result += "Before: ${beforeStart} - ${beforeEnd}: ${beforeEvent.subject}\n"
        } else {
            result += "Before: Free\n"
        }
        
        // After slot
        if (afterEvent) {
            def afterStart = afterEvent.start.format(DateTimeFormatter.ofPattern("h:mm a"))
            def afterEnd = afterEvent.end.format(DateTimeFormatter.ofPattern("h:mm a"))
            result += "After:  ${afterStart} - ${afterEnd}: ${afterEvent.subject}\n"
        } else {
            result += "After:  Free\n"
        }
        
        return result
    }
}


