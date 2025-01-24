package JDBCExplorer;
import groovy.sql.Sql
import groovy.time.*

class SQLCommand {
    Sql sql;
    int pagesize;
    String query;
    Map params = [:];

    SQLCommand(Sql sql, String query, int pagesize) {
        this.sql = sql;
        this.query = query;
        this.pagesize = pagesize;
    }

    private void getParamsFromConsole() {
        query.findAll(/:(\w*)/) { match, name ->
            def pvalue = System.console().readLine "${name}: ";
            params[name] = pvalue;
        }
    }

    public runSelect() {
        getParamsFromConsole();
        def timeStart = new Date();
        sql.query(query, params) { resultSet ->  
            new ResultSetHelper(resultSet).showResults(null,pagesize);
        } 
        TimeDuration duration = TimeCategory.minus(new Date(), timeStart)
            println "Total Time: $duration"
    }

    public runCommand() {
        getParamsFromConsole();
        def timeStart = new Date();
        if(query.toLowerCase().startsWith("insert")) {
            def keys = sql.executeInsert(query, params);
            println "Generated Keys: ${keys[0]}";
        }
        else {
            sql.execute(query, params);
        }
        TimeDuration duration = TimeCategory.minus(new Date(), timeStart);
        println "Total Time: $duration";
    }

    public runBatch() {
        println "Enter batch command, type [end] to finish and commit the batch.";
        def results = sql.withBatch() { stmt ->
            while(true) {
                def command = System.console().readLine "batchcmd >";
                if(command == null || command == "") continue;
                if(command.toLowerCase().equals("end")) break;
                stmt.addBatch(command);
            }
        }
        println "Generated Keys: ${results}";
    }
}
