package JDBCExplorer;
import java.util.concurrent.atomic.AtomicInteger;
import groovy.cli.commons.CliBuilder

class ConsolePrompt {

    public static void main(String[] args) {
        def cli = new CliBuilder(usage:'jdbc');
        cli.f(longOpt:'file',args:1,'The connections file. If none is specified connections.json is read.');
        cli.c(longOpt:'connection',args:1,'The name of the connection in the connections file.');
        cli.h(longOpt:'help', 'Show usage.');
        def options = cli.parse(args);

        if(options == null) {
            System.exit(0);
        }

        if(options.h) {
            cli.usage();
            System.exit(0);
        }
        String cFile = 'connections.json';
        if(options.f) cFile = options.f;

        ConnectionManager connManager = new ConnectionManager(cFile);

        String sqlPrompt = "sql >";
        String prompt = sqlPrompt, command = null;

        if(options.c) command = "use ${options.c}";

        AtomicInteger idleSeconds = new AtomicInteger();

        Thread.startDaemon {
            while(true) {
                idleSeconds.set(0);
                try {
                    if(command == null || command == '' ) command = System.console().readLine prompt;
                    if(command == null || command == '' ) continue;
                    if(command.endsWith(";")) command = command[0..-2];
                    def commandA = command.toLowerCase().tokenize(' ');
                    def commandC = command.tokenize(' '); // for case-sensitive databases

                    if(connManager.hasValidConnection() && commandA[0] == "select" ) {
                        def cmd = new SQLCommand(connManager.getSQL(), command, connManager.jsonObj.config.pagesize as int);
                        cmd.runSelect();
                    } else if(connManager.hasValidConnection() && 
                            (commandA[0] == "insert" || commandA[0] == "update" 
                             || commandA[0] == "delete" || commandA[0] == "cache" 
                             || commandA[0] == "replicate")) {
                        def cmd = new SQLCommand(connManager.getSQL(), command, connManager.jsonObj.config.pagesize as int);
                        if(commandA[0] == "insert") {
                            cmd.runCommand(true);
                        } else {
                            cmd.runCommand(false);
                        }
                    } else if(connManager.hasValidConnection() && (commandA[0] == "start" && commandA[1] == "batch")) {
                        def cmd = new SQLCommand(connManager.getSQL(), command, connManager.jsonObj.config.pagesize as int);
                        cmd.runBatch();
                    } else if(connManager.hasValidConnection() && (commandA[0] == "perf" || commandA[0] == "performance")) {
                        def pquery = System.console().readLine "Query: ";
                        def runs = (System.console().readLine("Runs Default(3): ")?:3) as int;
                        def threads = (System.console().readLine("Threads Default(1): ")?:1) as int;
                        def rint = (System.console().readLine("Row Progress Interval Default(25000): ")?:25000) as int;
                        PerformanceTest performance = new PerformanceTest(connManager, pquery, runs, threads, rint);
                        performance.runTest();
                        performance.showResults();
                    } else if(connManager.hasValidConnection() && (commandA[0] == "show" && commandA[1] == "tables")) {
                        new MetaDataHelper(connManager.getSQL()).showTables();
                    } else if(connManager.hasValidConnection() && (commandA[0] == "write" && commandA[1] == "metadata")) {
                        new MetaDataHelper(connManager.getSQL()).writeMetaData();
                    } else if(connManager.hasValidConnection() && commandA[0].startsWith("desc")) {
                        new MetaDataHelper(connManager.getSQL()).showColumns(commandC[1]);
                    } else if(connManager.hasValidConnection() && commandA[0].startsWith("show") && commandA[1].startsWith("meta")) {
                        def tablePattern = '%'
                            if( commandA.size() > 2) tablePattern = commandA[2]
                                new MetaDataHelper(connManager.getSQL()).showMetadata(tablePattern);
                    } else if(connManager.hasValidConnection() && commandA[0].startsWith("keys")) {
                        new MetaDataHelper(connManager.getSQL()).showKeys(commandA[1]);
                    } else if(connManager.hasValidConnection() && commandA[0].startsWith("fkeys")) {
                        new MetaDataHelper(connManager.getSQL()).showImportedKeys(commandA[1]);
                    } else if(commandA[0] == "show" && commandA[1] == "databases") {
                        connManager.showDatabases();
                    } else if(commandA[0] == "use") {
                        if(connManager.selectConnection(commandA[1])) 
                            prompt = "${connManager.selectedConnection.name} >"
                    } else if(commandA[0] == "config") { //override a configuration at runtime
                        if( commandA.size() != 3 ) println "Config must be specified as: config [name] [value]";
                        connectionManager.config[commandA[1]] = commandA[2];
                    } else if(commandA[0] == "q" || commandA[0] == "exit" || commandA[0] == "quit" ) {
                        connManager.closeConnection();
                        System.exit(0)
                    } else if(commandA[0] == "close") {
                        prompt = sqlPrompt; 
                        connManager.closeConnection();
                    } else if(command == "") {
                        // do nothing
                    } else if(command == "help") {
                        println "The following commands are supported:";
                        println "";
                        println "show databases;         Lists the drivers available in connections.json.";
                        println "use [db] [#driver] [connection];";
                        println "                        Connects to the chosen database. The optional driver input specifies which driver to pick.";
                        println "                        Additional connection properties can be specified. Helpful for Offline mode."   ;
                        println "keys [table];           Lists the primary keys in the table.";
                        println "fkeys [table];          Lists the foreign keys in the table.";
                        println "describe [table];       Lists the columns in the table.";
                        println "start batch;            Start a batch command to insert/update/delete.";
                        println "performance;            Start a performance test.";
                        println "help;                   This help.";
                        println "config {name} {value}   Change a config setting read from the connections.json.";
                        println "exit;                   Exits this program.";
                        println "SELECT/INSERT/UPDATE/DELETE statements";
                        println "";
                        println "Notes:";
                        println "1) For parameterized statements, use :name as the parameter placeholder.";
                        println "   For example: INSERT into Account (Name,City) values (:name,:city)";
                        println "2) Parameters are not allowed in batch commands.";
                    } else {
                        println "Unsupported command: [${command}]. You must select a database before running database commands.";
                    }
                } catch(Exception ex) {
                    println "Error: ${ex.getMessage()}";
                    ex.printStackTrace()
                    println ""
                }
                command = null;
            }
        }

        int timeoutSecs = 3000;
        int timeElapsed = 0;
        while(timeElapsed < timeoutSecs) {
            Thread.sleep(1000);
            timeElapsed = idleSeconds.addAndGet(1);
        }
        connManager.closeConection();
        println "\nExiting due to no activity for $timeElapsed seconds.";
    }
}



