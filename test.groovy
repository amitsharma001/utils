import groovy.sql.Sql



static showTables(sql) {
  def meta = sql.connection.metaData 
  def tables = meta.getTables(null, null, null, null)  
  while (tables.next()) println tables.getString('table_name') 
}

static showResults(row,cols) { 
  def total = 0
  def columnWidth = 20
  if(row == null) {
    cols.each { k ->
      printf "| %-${columnWidth}s", k
      total += (columnWidth+2)
    }
    print "|"
    println "\n"+"-"*(total+1)
  }
  else {
    cols.each { k ->
      def value = "NULL"
      if(row[k] != null) value = row[k].toString().length() > (columnWidth+1) ? row[k][0..(columnWidth-2)] + ".." : row[k]
      printf "|%-${columnWidth+1}s", value // We had an extra space in the header
    }
    print "|"
    print "\n"
  }
}

static showColumns(sql, table) {
  def meta = sql.connection.metaData
  println "CAlling calls" 
  def cols = meta.getColumns(null, null, table, null) 
  println "Found cols $cols"
  while (cols.next()) println "Name: ${cols.getString('column_name')} Type: ${cols.getString('data_type')}" 
}




static main(String[] args) {

  selectedCols = []
  def pickCols = { metadata ->
    // get the primary keys
    (1..metadata.columnCount).each {
      def colname = metadata.getColumnName(it)
       if (colname ==~ /(I|i)d/) selectedCols.add(metadata.getColumnName(it))
    }
    // get anything that matches name or number
    (1..metadata.columnCount).each {
      def colname = metadata.getColumnName(it).toLowerCase()
      if (selectedCols.size() >= 5) return;
      if (colname =~ /(name|number)/ && !selectedCols.contains(colname)) selectedCols.add(metadata.getColumnName(it))
    }
    // 
    (1..metadata.columnCount).each {
      def colname = metadata.getColumnName(it)
      if (selectedCols.size() >= 5) return;
      if (!selectedCols.contains(colname)) selectedCols.add(colname)
    }
    showResults( null, selectedCols)
  }

  def cli = new CliBuilder(usage:'jdbc')
  cli.n(args:1, argName:'name'/*,required:true*/, 'The name of the jdbc driver. This is used as the prefix.')
  def options = cli.parse(args)

  def durl = "jdbc:salesforce:"
  if(options.n) durl = 'jdbc:' + options.n + ":"

  def sql = Sql.newInstance(durl)
  sql.eachRow("SELECT * from SYS_CONNECTION_PROPS") {row ->
    def console = System.console()
    if( row.category == 'Authentication' && console != null) {
      def prop = System.console().readLine "$row.name :"
      if (prop != "") durl += ";$row.name=$prop"
    }
  }
  println durl = 'jdbc:salesforce:User=support@nsoftware.com;password=!rssbus;Security Token=ui3yfuZXJNwNXYb9U0cDE4kQd;'
  sql = Sql.newInstance(durl, '', '', 'cdata.jdbc.salesforce.SalesforceDriver')
  //showTables(sql)
  //showColumns(sql,"Account")

  sql.eachRow("SELECT * from Account LIMIT 10", pickCols) { row ->
    showResults( row, selectedCols)
  }
  selectedCols = []
  sql.eachRow("SELECT * from Lead LIMIT 10", pickCols) { row ->
    showResults( row, selectedCols)
  }
  // sql.call("{call GetUserInformation}") { row ->
  //   println row
  // }

}


 


