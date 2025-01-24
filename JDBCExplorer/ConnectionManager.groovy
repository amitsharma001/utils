package JDBCExplorer;
import groovy.json.*
import groovy.sql.Sql;

class ConnectionManager {
    def jsonObj; // Parsed JSON object that is a collection of Lists and Maps
    def selectedConnection = null;
    File selectedJar = null;
    Sql sql= null;

    ConnectionManager(String file) {
        File f = new File(file);
        if(!f.exists()) 
            throw new Exception("The connection file chosen ${f.getAbsolutePath()} does not exist.");
        jsonObj = new JsonSlurper().parse(f);
        if(jsonObj.config.cachejar) 
            this.getClass().classLoader.rootLoader.addURL((new File(jsonObj.config.cachejar).toURL()))
    }

    public Sql getSQL() {
        if(sql == null) 
            throw new Exception("No connection has been selected. Select a valid connection before executing SQL queries.");
        else return sql;
    }
    
    public boolean hasValidConnection() {
        return sql as boolean;
    }

    public void closeConnection() {
        sql?.close();
        sql = null;
    }

    public boolean selectConnection(String connectionName, boolean showDetails=true) {
        selectedConnection = jsonObj.connections.find {key -> key.name == connectionName;}
        if(selectedConnection != null) {
            if (selectedConnection.jar != null) {
                selectedJar = new File(selectedConnection.jar);
            } else {
                String objname = selectedConnection.driver.tokenize('.')[2];
                selectedJar = new File(jsonObj.config.libdir + File.separator + "cdata.jdbc." + objname + ".jar");
            }

            if (selectedJar.exists()) {
                this.getClass().classLoader.rootLoader.addURL(selectedJar.toURL());
                String connstr = getConnectionString();
                sql = Sql.newInstance(connstr, selectedConnection.user?:'', 
                                        selectedConnection.password?:'', selectedConnection.driver);
                if(showDetails) print (showDatabaseInfo(null));
            } else {
                println "The jar file ${selectedJar.getAbsolutePath()} for the selected connection ${connectionName} does not exist.";
                selectedJar = null;
            }
        } else {
            println "The selected connection ${connectionName} does not exist.";
        }
        sql == null?false: true;
    }

    public String showDatabaseInfo(def connection) {
        String databaseInfo = "";
        if(connection != null) {
            databaseInfo <<= "Name      : ${connection.name}\n";
            databaseInfo <<= "Connection: ${connection.connection}\n";
            databaseInfo <<= "Driver    : ${connection.driver}\n";
        }
        else if(sql != null) {
            databaseInfo <<= "Name      : ${selectedConnection.name}\n";
            databaseInfo <<= "Connection: ${getConnectionString()}\n";
            databaseInfo <<= "Driver    : ${selectedConnection.driver}\n";
            if( selectedJar != null) {
                def version = null;
                if( selectedConnection.jar == null ) {
                    def javacall = ["java","-jar",selectedJar.getAbsolutePath(),"--version"];
                    version = javacall.execute().text.readLines()[1];
                }
                else version = "Third-Party JDBC Driver\n";
                databaseInfo <<= "Jar       : ${selectedJar.getAbsolutePath()}\n";
                databaseInfo <<= "Version   : ${version}\n\n";
            }
            return databaseInfo;
        }
    }

    public void showDatabases() {
        println "---------------------------------";
        jsonObj.connections.each { d ->
            print showDatabaseInfo(d);
            println "---------------------------------";
        }
    }
    
    private String getConnectionString() {
        String connstr = selectedConnection.connection;
        if(selectedConnection.jar == null) { // Jar files are specified for Third-Party drivers
            if(!connstr.endsWith(";")) connstr += ";";
            if(jsonObj.config.logdir != null) connstr += "Logfile=${jsonObj.config.logdir}" + File.separator + "${selectedConnection.driver}.txt;";
            if(jsonObj.config.cacheconnection?.trim()) {
                connstr += "CacheConnection=${jsonObj.config.cacheconnection};";
                connstr += "CacheDriver=${jsonObj.config.cachedriver};";
            }
            if(jsonObj.config.verbosity != null && jsonObj.config.verbosity.isInteger()) connstr += "Verbosity=${jsonObj.config.verbosity}";
        }
        return connstr;
    }
}


