import groovy.sql.Sql
import groovy.json.*
import groovy.time.*
import groovy.cli.commons.CliBuilder
import java.util.concurrent.atomic.AtomicInteger
import static groovyx.gpars.GParsPool.withPool

static showResults(resultSet, cols, pagesize = 500, options = "") { 
  def totalLength = 0
  def maxWidth = 25
  def border = ''
  def header = ''
  def pagesizeInt = 200
  if(pagesize != null) pagesizeInt = pagesize as int

  cols.each { k, v -> totalLength += v }
  if( totalLength < 300 ) maxWidth = 50
  cols.each { k, v ->
    def width = v < maxWidth ? v : maxWidth
    def cname = k.length() > width? k[0..(width-2)] + ".." :k
    header <<= sprintf "|%-${width+2}s", cname
    border <<= "+"+"-"*(width+2)
  }

  header <<= "|\n"
  border <<= "+\n"
  
  print border + header + border
  int count = 0
  def readToEnd = true;

  while(resultSet.next()) {
    count++
    cols.each { k, v ->
      def value = resultSet.getObject(k) == null? "NULL" : resultSet.getObject(k).toString()
      def width = v < maxWidth ? v : maxWidth
      value = value.length() > width ? value[0..(width-3)] + ".." : value
      printf "| %-${width+1}s", value // We had an extra space in the header
    }
    print "|\n"
    if(pagesizeInt != -1 && count % pagesizeInt == 0) {
      print border
      print "$count rows. Do you want to see more [Y/N]? "
      if(System.console().readLine().toLowerCase() == 'y') {
        print border + header + border
      } else {
        readToEnd = false;
        break;
      }
    }
  }
  if(readToEnd) {
    print border
    println "Total Rows: $count."
  }
}

static showTables(sql) {
  def meta = sql.connection.metaData 
  def tables = meta.getTables(null, null, null, null)  
  def metadataCols = ["TABLE_CAT":20,"TABLE_SCHEM":20,"TABLE_NAME":50]
  showResults(tables, metadataCols)
}

static writeResultSetToFile(resultSet,filename,table='',columns) {
  if (!resultSet) {
    println "Result set is empty. Nothing to write."
    return
  }
  boolean hasLastModifiedDate = false
  def f = new File(filename)
  def headers = ""
  if(!f.exists()) headers = columns.join(", ")   
  
  new File(filename).withWriterAppend { writer ->
    if (headers != "") writer.writeLine(headers)
    while(resultSet.next()) {
      def rowvalues = ""
      def first = true
      for(column in columns) {
        def value = resultSet.getString(column)
        if (value.equals("lastmodifieddate")) hasLastModifiedDate = true
        if (first) first = false
        else rowvalues += ", "
        rowvalues += value
      }
      writer.writeLine(rowvalues)
    }
  }
  //if(hasLastModifiedDate) printf("Table %s has LastModifiedDate.\n",table)
  //else printf("Table %s does not have LastModifiedDate.\n",table)
}

static writeMetaData(sql) {
  def meta = sql.connection.metaData
  def tables = meta.getTables(null, null,'%', null)
  def tableColumns = ["TABLE_CAT","TABLE_SCHEM","TABLE_NAME"] 
  writeResultSetToFile(tables,"Tables.csv","Metadata",tableColumns)
  printf("Written Tables.csv, now writing column information.\n")
  tables = meta.getTables(null, null,'%', null)  
  while(tables.next()) {
    def table = tables.getObject("Table_Name").toString()
    try {
      def cols = meta.getColumns(null, null, table, null)
      writeResultSetToFile(cols,"Columns.csv",table,["TABLE_NAME","COLUMN_NAME","DATA_TYPE","TYPE_NAME","COLUMN_SIZE"])
      printf("Written %s - metadata for %s\n",table,table)
    } catch(Exception e) {
      printf("Could not get metadata for %s due to error: %s\n",table,e.Message)
    }
  }
}

static showMetadata(sql, tablePattern='%') {
  def meta = sql.connection.metaData 
  def tables = meta.getTables(null, null, tablePattern, null)  
  def count = 0;
  while(tables.next()) {
    def table = tables.getObject("Table_Name").toString()
    try {
      count++
      def cols = meta.getColumns(null, null, table, null)
      def cquery = "SELECT count(*) as CNT from ["+table+"]"
      def rows = sql.rows(cquery)["CNT"][0]
      printf("\n\n%d) %s: %d rows\n Columns: ",count,table, rows)
      while(cols.next()) {
        printf("%s (%s), ",cols.getObject("Column_Name").toString(), cols.getObject("Type_Name").toString())
      }
      if(count % 10 == 0) {
        print "\n\nDo you want to see more tables [Y/N]? "
        if(System.console().readLine().toLowerCase() == 'n') break;
      }
    } catch (Exception e){
      printf("Could not get metadata for table %s\n",table)
    }
  }
  println "\n"
}

static showColumns(sql, table) {
  print "Showing columns for $table"
  def meta = sql.connection.metaData
  def cols = meta.getColumns(null, null, table, null)
  def metadataCols = ["COLUMN_NAME":40,"TYPE_NAME":20,"COLUMN_SIZE":20,"IS_NULLABLE":20]
  showResults(cols, metadataCols);
}

static showKeys(sql, table) {
  def meta = sql.connection.metaData
  def cols = meta.getPrimaryKeys(null, null, table)
  def metadataCols = ["Column_Name":40,"PK_Name":20]
  showResults(cols, metadataCols);
}

static showImportedKeys(sql, table) {
  def meta = sql.connection.metaData
  def cols = meta.getImportedKeys(null, null, table)
  def metadataCols = ["PKColumn_Name":15,"PKTable_Name":15,"FKTable_Name":15,"FKColumn_Name":15,"PK_Name":20,"FK_Name":20]
  showResults(cols, metadataCols);
}


static pickColumns(sql, metadata) {
  def selectedCols = [:]
  def allCols = [:]
  def pk = []
  for(int i=1; i<=metadata.getColumnCount(); i++) {
    def size = metadata.getPrecision(i)
    def type = metadata.getColumnTypeName(i);
    if(type == "TIMESTAMP") size = 22
    else if (type == "DATE") size = 10
    else if (type == "INT") size = 10
    else if (type == "VARCHAR") size > 5? size: 10
    allCols[metadata.getColumnLabel(i)] = size
  }
  // get anything called Id, AccountID etc.
  allCols.each {k, v -> if (pk.contains(k)) selectedCols[k] = v}
  
  // get anything that matches name or number
  allCols.each { k, v ->
    if (selectedCols.size() >= 5) return;
    if (k =~ /(name|number)/ && !selectedCols.containsKey(k)) selectedCols[k] = v
  }
    // 
  allCols.each { k, v ->
    if (selectedCols.size() >= 5) return;
    if (!selectedCols.containsKey(k)) selectedCols[k] = v
  }
  return selectedCols
}

static getJarFile(driver, connJ, jarlocation="local") {
  File df = new File("")
  if(driver != null) {
    if (driver.jar != null) {
      df = new File(driver.jar)
    }
    else if(jarlocation.toLowerCase() == "j7") 
      df = new File(connJ.config.driverlocal + File.separator + "cdata.jdbc."+driver.driver.tokenize('.')[2]+".jar")
    else if(jarlocation.toLowerCase() == "local") {
      def objname = driver.driver.tokenize('.')[2]
      df = new File(connJ.config.driverLocal + File.separator + "cdata.jdbc." + objname + ".jar")
    }
    else {
      println "Invalid choice ${jarlocation}. Choose between local or local."
    }
  }
  return df
}

static getSQLInstance(driver, connJ, classLoader=null, showDatabaseDetails=false, jarlocation="local") {
  def sql = null
  if(driver != null) {
    File df = getJarFile(driver, connJ, jarlocation)
    if (df.exists()) {
      def connstr = getConnection(driver,connJ.config)
      if(showDatabaseDetails) print (showDataBase(driver, df, connstr))
      if(classLoader != null) classLoader.rootLoader.addURL(df.toURL())
      sql = Sql.newInstance(connstr, driver.user?:'', driver.password?:'', driver.driver)
    } else {
      println "The selected jar ${df.getAbsolutePath()} does not exist."
    }
  } 
  return sql
}

static writeIntervalInformation(rows, tid, timeStart, lastIntervalTime) {
  def now = new Date()
  def runtime = TimeCategory.minus(now, timeStart)
  def runtimeInterval = TimeCategory.minus(now, lastIntervalTime)
  printf "Thread: %d Rows: %8d Time: %6d ms Interval Time: %6d\n", tid, rows, runtime.toMilliseconds(), runtimeInterval.toMilliseconds()
  return now
}

static performanceTest(driver, connJ, pquery, runs, threads=1, rowInterval = 25000) {
  withPool(threads) {
    (1..runs).collectParallel() {
      def timeStart = new Date()
      def lastIntervalTime = timeStart, intervalTime = null;
      def tid = Thread.currentThread().getId()
      def rows=0, cols=0
      printf "\n*** Starting Run %d Thread %d ***\n", it, tid
      TimeDuration runtime = null
      def sql = null
      try {
        sql = getSQLInstance(driver, connJ)
        sql.query(pquery) { resultset -> 
          cols = resultset.getMetaData().getColumnCount()
          while(resultset.next()) { 
            cols.times { resultset.getObject(it+1) }; 
            if(rows++ == 0 || (rows % rowInterval) == 0) {
              lastIntervalTime = writeIntervalInformation(rows, tid, timeStart, lastIntervalTime)
            }
          } // read all the rows
        }
        lastIntervalTime = writeIntervalInformation(rows, tid, timeStart, lastIntervalTime)
        runtime = TimeCategory.minus(lastIntervalTime,timeStart)
        printf "\n*** Run ${it} Completed Thread $tid *** Total Time: $runtime\n"
      } catch (Exception ex) {
        println "Thread: $tid Error at row $rows: ${ex.message}"
        runtime = new TimeDuration(0, 0, 0, 0)
      } finally {
        sql.close()
      }
      return [runtime, rows, cols]
    }
  }
}

static showDataBase(d, jarfile=null, connstr=null) {
  if(connstr == null) connstr = d.connection
  def databaseInfo = ""
  databaseInfo <<= "Name      : ${d.name}\n"
  databaseInfo <<= "Connection: ${connstr}\n"
  databaseInfo <<= "Driver    : ${d.driver}\n"
  if( jarfile != null) {
    def version = null
    if( d.jar == null ) {
      def javacall = ["java","-jar",jarfile.getAbsolutePath(),"--version"]
      version = javacall.execute().text.readLines()[1]
    }
    else version = "Third-Party JDBC Driver\n"
    databaseInfo <<= "Jar       : ${jarfile.getAbsolutePath()}\n"
    databaseInfo <<= "Version   : ${version}\n"
  }
  return databaseInfo
}

static getConnection(driver, config) {
  def connstr = driver.connection
  if(driver.jar == null) { // Jar files are specified for Third-Party drivers
    if(!connstr.endsWith(";")) connstr += ";"
    if(config.logdir != null) connstr += "Logfile=${config.logdir}" + File.separator + "${driver.name}.txt;"
    if(config.cacheconnection?.trim()) {
      connstr += "CacheConnection=${config.cacheconnection};"
      connstr += "CacheDriver=${config.cachedriver};"
    }
    if(config.verbosity != null && config.verbosity.isInteger()) connstr += "Verbosity=${config.verbosity}"
  }
  return connstr
}

static showDataBases(connJ) {
  println "---------------------------------"
  connJ.connections.each { d ->
    print showDataBase(d)
    println "---------------------------------"
  }
}

static main(String[] args) {
  //selectedCols = []

  def cli = new CliBuilder(usage:'jdbc')
  cli.f(longOpt:'file',args:1,'The connections file. If none is specified connections.json is read.')
  cli.c(longOpt:'connection',args:1,'The name of the connection in the connections file.')
  cli.h(longOpt:'help', 'Show usage.')
  def options = cli.parse(args)

  if(options == null) {
    System.exit(0);
  }
  
  if(options.h) {
    cli.usage()
    System.exit(0);
  }
  
  String cFile = 'connections.json'
  if(options.f) cFile = options.f
  File f = new File(cFile)
  
  if(!f.exists()) {
    println "The connection file [${f.getAbsolutePath()}] does not exist."
    System.exit(0);
  }

  def connJ = new JsonSlurper().parse(f)
  String sqlPrompt = "sql >"
  def prompt = sqlPrompt, sql = null, command = null, driver = null

  if(connJ.config.cachejar != null) {
    this.getClass().classLoader.rootLoader.addURL((new File(connJ.config.cachejar).toURL()))
  }

  if(options.c) command = "use ${options.c}"
  
  AtomicInteger idleSeconds = new AtomicInteger();
 
  Thread.startDaemon {
    while(true) {
      idleSeconds.set(0);
      try {
        if(command == null || command == '' ) command = System.console().readLine prompt
        if(command == null || command == '' ) continue
        if(command.endsWith(";")) command = command[0..-2]
        def commandA = command.toLowerCase().tokenize(' ')
        def commandC = command.tokenize(' ') // for case-sensitive databases
        
        if(sql != null && commandA[0] == "select" ) {
          def params = [:]
          command.findAll(/:(\w*)/) { match, name ->
            def pvalue = System.console().readLine "${name}: "
            params[name] = pvalue
          }
          def timeStart = new Date()
          def selectOptions = ""
          if(commandA[-1] =~ /\[.*\]/) {
            selectOptions = commandA[-1]
            command = commandA[0..-2].join(" ")
          }
          sql.query(command, params) { resultSet ->  
            showResults(resultSet, pickColumns(sql, resultSet.getMetaData()), connJ.config.pagesize)
          } 
          TimeDuration duration = TimeCategory.minus(new Date(), timeStart)
          println "Total Time: $duration"
        } else if(sql != null && (commandA[0] == "insert" || commandA[0] == "update" || commandA[0] == "delete" || commandA[0] == "cache" || commandA[0] == "replicate")) {
            def params = [:]
            command.findAll(/:(\w*)/) { match, name ->
              def pvalue = System.console().readLine "${name}: "
              params[name] = pvalue
            }
            if(commandA[0] == "insert") {
              def keys = sql.executeInsert(command, params)
              println "Generated Keys: ${keys[0]}"
            } else {
              sql.execute(command, params)
            }
        } else if(sql != null && (commandA[0] == "start" && commandA[1] == "batch")) {
            println "Enter batch command, type [end] to finish and commit the batch."
            def results = sql.withBatch() { stmt ->
              while(true) {
                idleSeconds.set(0)
                command = System.console().readLine "batchcmd >"
                if(command == null || command == "") continue
                if(command.toLowerCase().equals("end")) break;
                stmt.addBatch(command)
              }
            }
            println "Generated Keys: ${results}"
        } else if(sql != null && (commandA[0] == "perf" || commandA[0] == "performance")) {
            def pquery = System.console().readLine "Query: "
            def runs = (System.console().readLine("Runs Default(3): ")?:3) as int
            def threads = (System.console().readLine("Threads Default(1): ")?:1) as int
            def rint = (System.console().readLine("Row Progress Interval Default(25000): ")?:25000) as int
            def timeStart = new Date() 
            if(!pquery.toLowerCase().startsWith("select")) pquery = "SELECT * from "+pquery
            def results = performanceTest(driver, connJ, pquery, runs, threads, rint)
            totalRuntime = TimeCategory.minus(new Date(), timeStart)
            def success = 0, totalTime = 0, rows = 0, cols =0
            results.each { result ->
              def ms = result[0].toMilliseconds()
              if(ms != 0) {
                rows = result[1]
                cols = result[2]
                success++
                totalTime += ms
              }
            }
            if(success > 0) {
              def avg = new TimeDuration(0,0,0,(int)(totalTime/success)) //
              def perfResults = "\n\n********* Performance Results: ${new Date()} **************\n"
              perfResults <<= showDataBase(driver, getJarFile(driver, connJ))
              perfResults <<= "\nQuery: $pquery, Rows: $rows, Cols: $cols"
              perfResults <<= "\nRuns: $runs Threads: $threads"
              perfResults <<= "\nAverage time: ${totalTime/success} ms ($avg) per thread."
              perfResults <<= "\nTotal time taken for $runs runs: ${totalRuntime.toMilliseconds()}ms ($totalRuntime)\n" 
              println perfResults
              File fpr = new File(connJ.config.logdir + File.separator + driver.driver+".perf.txt")
              fpr.append(perfResults)
            }
            //avgTime = new TimeDuration(0,0,0,(int)(totalTime.toMilliseconds()/runs)) //Highlight properly/
            //println "Total Time: ${totalTime.toMilliseconds()} ms Runs: ${runs} Average Time: ${avgTime.toMilliseconds()} ms ($avgTime)"
        } else if(sql != null && (commandA[0] == "show" && commandA[1] == "tables")) {
            showTables(sql)
        } else if(sql != null && (commandA[0] == "write" && commandA[1] == "metadata")) {
	  writeMetaData(sql)
        } else if(sql !=null && commandA[0].startsWith("desc")) {
            showColumns(sql, commandC[1])
        } else if(sql !=null && commandA[0].startsWith("show") && commandA[1].startsWith("meta")) {
            def tablePattern = '%'
            if( commandA.size() > 2) tablePattern = commandA[2]
            showMetadata(sql, tablePattern)
        } else if(sql !=null && commandA[0].startsWith("keys")) {
            showKeys(sql, commandA[1])
        } else if(sql !=null && commandA[0].startsWith("fkeys")) {
            showImportedKeys(sql, commandA[1])
        } else if(commandA[0] == "show" && commandA[1] == "databases") {
          showDataBases(connJ)
        } else if(commandA[0] == "use") {
          driver = connJ.connections.find {k -> k.name == commandA[1];}
          def jarlocation = "local"
          if(commandA.size() > 2 && commandA[2] != null) jarlocation = commandA[2]
          if(driver != null) {
              sql = getSQLInstance(driver, connJ, this.getClass().classLoader, true, jarlocation)
              prompt = "${driver.name} >"
          } else {
            println "Database ${commandA[1]} not found."
          }
        } else if(commandA[0] == "config") {
          if( commandA.size() != 3 ) println "Config must be specified as: config [name] [value]"
          connJ.config[commandA[1]] = commandA[2]
        } else if(commandA[0] == "q" || commandA[0] == "exit" || commandA[0] == "quit" ) {
          if( sql != null ) { prompt = sqlPrompt; sql.close(); sql = null }
          System.exit(0)
        } else if(commandA[0] == "close") {
          if( sql != null) { prompt = sqlPrompt; sql.close(); sql = null }
        } else if(command == "") {
          // do nothing
        } else if(command == "help") {
          println "The following commands are supported:"
          println ""
          println "show databases;         Lists the drivers available in connections.json."
          println "use [db] [#driver] [connection];"
          println "                        Connects to the chosen database. The optional driver input specifies which driver to pick."
          println "                        Additional connection properties can be specified. Helpful for Offline mode."   
          println "keys [table];           Lists the primary keys in the table."
          println "fkeys [table];          Lists the foreign keys in the table."
          println "describe [table];       Lists the columns in the table."
          println "start batch;            Start a batch command to insert/update/delete."
          println "performance;            Start a performance test."
          println "help;                   This help."
          println "config {name} {value}   Change a config setting read from the connections.json."
          println "exit;                   Exits this program."
          println "SELECT/INSERT/UPDATE/DELETE statements"
          println ""
          println "Notes:"
          println "1) For parameterized statements, use :name as the parameter placeholder."
          println "   For example: INSERT into Account (Name,City) values (:name,:city)"
          println "2) Parameters are not allowed in batch commands."
        } else {
          println "Unsupported command: [${command}]. You must select a database before running database commands."
        }
      } catch(Exception ex) {
        println "Error: ${ex.getMessage()}"
        //ex.printStackTrace()
        println ""
      }
      command = null
    }
  }
  
  int timeoutSecs = 3000
  int timeElapsed = 0
  while(timeElapsed < timeoutSecs) {
    Thread.sleep(1000)
    timeElapsed = idleSeconds.addAndGet(1);
  }
  if( sql != null ) sql.close()
  println "\nExiting due to no activity for $timeElapsed seconds."
}


 


