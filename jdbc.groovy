import groovy.sql.Sql
import groovy.json.*

static showTables(sql) {
  def meta = sql.connection.metaData 
  def tables = meta.getTables(null, null, null, null)  
  def metadataCols = ["Table_Name":50]
  showResults(tables, metadataCols)
}

static showResults(resultSet,cols) { 
  def totalLength = 0
  def maxWidth = 25
  def border = ''
  def header = ''

  cols.each { k, v -> totalLength += v }
  if( totalLength < 300 ) maxWidth = 50
  cols.each { k, v ->
    def width = v < maxWidth ? v : maxWidth
    def cname = k.length() > width? k[0..(width-2)] + ".." :k
    header <<= sprintf "|%-${width+1}s", cname
    border <<= "+"+"-"*(width+1)
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
      printf "|%-${width+1}s", value // We had an extra space in the header
    }
    print "|\n"
    if(count%200 == 0) {
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

static showColumns(sql, table) {
  def meta = sql.connection.metaData
  def cols = meta.getColumns(null, null, table, null)
  def metadataCols = ["Column_Name":40,"Type_Name":20,"Column_Size":20,"Is_Nullable":20]
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


static getPK(sql, table) {
  def meta = sql.connection.metaData
  def keys = meta.getPrimaryKeys(null, null, table)
  def pk = []
  while(keys.next()) {
    pk.add(keys.getString("COLUMN_NAME"))
  }
  return pk
}

static pickColumns(sql, metadata) {
  def selectedCols = [:]
  def allCols = [:]
  def pk = []
  for(int i=1; i<=metadata.getColumnCount(); i++) {
    if(i == 1) pk = getPK(sql,metadata.getTableName(i))
    def size = metadata.getPrecision(i)
    allCols[metadata.getColumnName(i)] = size > 5? size: 5
  }
  // get anything called Id, AccountID etc.
  allCols.each {k, v -> if (pk.contains(k)) selectedCols[k] = v}
  
  // get anything that matche name or number
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

static showDataBase(d) {
  def javacall = ["java","-jar",d.jar,"--version"]
  def response = javacall.execute().text.readLines()
  println "Name      : ${d.name}"
  println "Connection: ${d.connection}"
  println "Driver    : ${d.driver}"
  println "Jar       : ${d.jar}"
  println "Version   : ${response[1]}"
}

static showDataBases(connJ) {
  println "---------------------------------"
  connJ.each { d ->
    showDataBase(d)
    println "---------------------------------"
  }
}

static main(String[] args) {
  selectedCols = []

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
  def prompt = "sql >"
  def sql = null
  def command = null

  if(options.c) command = "use ${options.c}"

  while(true) {
    try {
      if(command == null) command = System.console().readLine prompt

      if(command.endsWith(";")) command = command[0..-2]
      def commandA = command.toLowerCase().tokenize(' ')
      
      if(sql != null && commandA[0] == "select") {
        def params = [:]
        command.findAll(/:(\w*)/) { match, name ->
          def pvalue = System.console().readLine "${name}: "
          params[name] = pvalue
        }
        sql.query(command, params) { resultSet ->  
          showResults(resultSet, pickColumns(sql, resultSet.getMetaData()))
        }
      } else if(sql != null && (commandA[0] == "insert" || commandA[0] == "update" || commandA[0] == "delete")) {
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
          sql.withBatch() { stmt ->
            while(true) {
              command = System.console().readLine "batchcmd >"
              if(command == null || command == "") continue
              if(command.toLowerCase().equals("end")) break;
              stmt.addBatch(command)
            }
            stmt.executeBatch()
          }
      } else if(sql != null && (commandA[0] == "show" && commandA[1] == "tables")) {
          showTables(sql)
      } else if(sql !=null && commandA[0].startsWith("desc")) {
          showColumns(sql, commandA[1])
      } else if(sql !=null && commandA[0].startsWith("keys")) {
          showKeys(sql, commandA[1])
      } else if(sql !=null && commandA[0].startsWith("fkeys")) {
          showImportedKeys(sql, commandA[1])
      } else if(commandA[0] == "show" && commandA[1] == "databases") {
        showDataBases(connJ)
      } else if(commandA[0] == "use") {
        def driver = connJ.find {k -> k.name == commandA[1]}
        if(driver != null) {
          showDataBase(driver)
          this.getClass().classLoader.rootLoader.addURL((new File(driver.jar)).toURL())
          sql = Sql.newInstance(driver.connection, '', '', driver.driver)
          prompt = "${driver.name} >"
        } else {
          println "Database ${commandA[1]} not found."
        }
      } else if(commandA[0] == "q" || commandA[0] == "exit" || commandA[0] == "quit" ) {
        System.exit(0)
      } else if(command == "") {
        // do nothing
      } else if(command == "help") {
        println "The following commands are supported:"
        println ""
        println "show databases;         Lists the drivers available in connections.json."
        println "use [db];               Connects to the chosen database."
        println "keys [table];           Lists the primary keys in the table."
        println "fkeys [table];          Lists the foreign keys in the table."
        println "describe [table];       Lists the columns in the table."
        println "start batch;            Start a batch command to insert/update/delete."
        println "help;                   This help."
        println "exit;                   Exits this program."
        println "SELECT/INSERT/UPDATE/DELETE statements"
        println ""
        println "For parameterized statements, use :name as the parameter placeholder."
        println "For example: INSERT into Account (Name,City) values (:name,:city)"
      } else {
        println "Unsupported command: [${command}]. You must select a database before running database commands."
      }
    } catch(Exception ex) {
      println "Error: ${ex.getMessage()}"
    }
    command = null
  }
}


 


