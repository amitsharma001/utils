#!/usr/bin/env groovy

import java.time.format.DateTimeFormatter
import java.time.LocalDate

// Interactive Calendar Availability Finder
println "Finding available time slots with context..."

try {
    // Load the CalendarDataAccess dynamically
    def calendarDataAccessClass = new GroovyClassLoader().parseClass(new File("CalendarDataAccess.groovy"))
    
    // Create an instance
    def calendarDataAccess = calendarDataAccessClass.newInstance()
    
    // Get the availability finder from the data access layer
    def dataAccess = calendarDataAccess.getAvailabilityFinder()
    def config = calendarDataAccess.getConfig()
    
    // Load all events for context
    def allEvents = dataAccess.loadEventsForWeeks(config.weeksToLookAhead)
    println "Loaded ${allEvents.size()} events for analysis"
    
    // Get all available slots across multiple days
    def allAvailableSlots = dataAccess.getAllAvailableSlots(1, config.availabilityHours)
    
    if (allAvailableSlots.size() > 0) {
        def maxHoursPerDay = config.containsKey('maxHoursPerDay') ? config.maxHoursPerDay : 3
        def minDaysRequired = config.containsKey('minDaysRequired') ? config.minDaysRequired : 3
        def totalHoursNeeded = config.availabilityHours
        
        println "\n=== Available Time Slots ==="
        println "Looking for ${config.availabilityHours} hours spread across multiple days"
        println "You can select up to ${maxHoursPerDay} hours per day. Once you reach ${maxHoursPerDay} hours for a day, remaining slots for that day will be skipped."
        println "You must select at least ${minDaysRequired} days to meet the requirements."
        println ""
        
        def confirmedSlots = []
        def hoursPerDay = [:] as Map<LocalDate, Integer>
        def totalHoursSelected = 0
        
        // Show each slot with context and ask for confirmation
        for (datedSlot in allAvailableSlots) {
            // Stop if we have enough total hours AND enough days
            if (totalHoursSelected >= totalHoursNeeded && hoursPerDay.keySet().size() >= minDaysRequired) {
                println "✅ Required hours (${totalHoursNeeded}) and minimum days (${minDaysRequired}) reached. Stopping slot presentation."
                break
            }
            

            
            def slot = datedSlot.timeSlot
            def date = datedSlot.date
            def currentDayHours = hoursPerDay.get(date, 0)
            
            // Skip this slot if we've already selected 3 hours for this day
            if (currentDayHours >= maxHoursPerDay) {
                println "⏭️  Skipping ${date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE (M/d)"))} - already selected ${currentDayHours} hours for this day"
                continue
            }
            
            // If we have enough hours but not enough days, continue to get more days
            if (totalHoursSelected >= totalHoursNeeded && hoursPerDay.keySet().size() < minDaysRequired) {
                // Only show slots for new days we haven't used yet
                if (hoursPerDay.containsKey(date)) {
                    println "⏭️  Skipping ${date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE (M/d)"))} - need more days, already used this day"
                    continue
                }
            }
            def dayOfWeek = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE"))
            def monthDay = date.format(java.time.format.DateTimeFormatter.ofPattern("M/d"))
            def startTime = slot.startTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            def endTime = slot.endTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            
            // Get context for this slot
            def context = dataAccess.getSlotContext(slot, allEvents)
            
            // Display in order: Before, Proposed, After
            println "📅 ${dayOfWeek} (${monthDay})"
            
            // Before slot
            if (context.beforeEvent) {
                def beforeStart = context.beforeEvent.start.format(DateTimeFormatter.ofPattern("h:mm a"))
                def beforeEnd = context.beforeEvent.end.format(DateTimeFormatter.ofPattern("h:mm a"))
                println "Before: ${beforeStart} - ${beforeEnd}: ${context.beforeEvent.subject}"
            } else {
                println "Before: Free"
            }
            
            // Proposed slot
            println "Proposed: ${startTime} - ${endTime} (${slot.durationInHours}h)"
            
            // After slot
            if (context.afterEvent) {
                def afterStart = context.afterEvent.start.format(DateTimeFormatter.ofPattern("h:mm a"))
                def afterEnd = context.afterEvent.end.format(DateTimeFormatter.ofPattern("h:mm a"))
                println "After:  ${afterStart} - ${afterEnd}: ${context.afterEvent.subject}"
            } else {
                println "After:  Free"
            }
            
            // Ask for user confirmation
            print "Accept this slot? (y/n): "
            def userInput = System.in.newReader().readLine().trim().toLowerCase()
            
            if (userInput == 'y' || userInput == 'yes') {
                confirmedSlots << datedSlot
                totalHoursSelected += slot.durationInHours
                hoursPerDay[date] = currentDayHours + slot.durationInHours
                println "✅ Slot accepted! (${slot.durationInHours}h added, ${hoursPerDay[date]}h total for ${dayOfWeek})"
            } else {
                println "❌ Slot rejected"
            }
            println ""
        }
        
        // Show final summary
        if (confirmedSlots.size() > 0) {
            println "=== Final Selection ==="
            def totalHours = confirmedSlots.sum { it.timeSlot.durationInHours }
            def daysUsed = hoursPerDay.keySet().size()
            println "Selected ${confirmedSlots.size()} slots totaling ${totalHours} hours across ${daysUsed} days:"
            
            // Group slots by date
            def slotsByDate = confirmedSlots.groupBy { it.date }
            slotsByDate.each { date, daySlots ->
                def dayOfWeek = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE"))
                def monthDay = date.format(java.time.format.DateTimeFormatter.ofPattern("M/d"))
                def dayHours = hoursPerDay[date]
                
                // Format all slots for this day
                def slotStrings = daySlots.collect { datedSlot ->
                    def slot = datedSlot.timeSlot
                    def startTime = slot.startTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                    def endTime = slot.endTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                    return "${startTime} to ${endTime}"
                }
                
                println "${dayOfWeek} (${monthDay}): ${slotStrings.join(' and ')} (${dayHours}h)"
            }
        } else {
            println "No slots were selected."
        }
        
    } else {
        println "No sufficient availability found"
    }
    
    // Close the connection
    calendarDataAccess.close()
    
} catch (Exception e) {
    println "Error: ${e.message}"
    e.printStackTrace()
}
