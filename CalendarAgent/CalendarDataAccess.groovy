import groovy.json.*
import groovy.sql.Sql
import java.time.*
import java.time.format.*

class CalendarDataAccess {
    def config
    def connectionManager
    def sql
    
    CalendarDataAccess(String configFile = "config.json") {
        loadConfig(configFile)
        setupConnection()
    }
    
    void loadConfig(String configFile) {
        File f = new File(configFile)
        if (!f.exists()) {
            throw new Exception("Configuration file ${f.getAbsolutePath()} does not exist.")
        }
        config = new JsonSlurper().parse(f)
    }
    
    void setupConnection() {
        // Load the ConnectionManager class from the JDBCExplorer
        def connectionManagerClass = new GroovyClassLoader().parseClass(
            new File("/Users/amits/code/utils/JDBCExplorer/ConnectionManager.groovy")
        )
        connectionManager = connectionManagerClass.newInstance(config.connectionsFile)
        
        // Select the exchange connection
        if (!connectionManager.selectConnection(config.exchangeConnectionName, false)) {
            throw new Exception("Failed to connect to exchange database")
        }
        
        sql = connectionManager.getSQL()
        println "Successfully connected to Exchange database"
    }
    
    // Getter methods for external access
    def getConfig() {
        return config
    }
    
    def getAvailabilityFinder() {
        def calendarAvailabilityFinderClass = new GroovyClassLoader().parseClass(new File("CalendarAvailabilityFinder.groovy"))
        return calendarAvailabilityFinderClass.newInstance(sql, config)
    }
    
    void close() {
        connectionManager?.closeConnection()
    }
}
