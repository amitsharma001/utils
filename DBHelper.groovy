import groovy.sql.Sql
import groovy.json.*
import groovy.time.*

public class DBHelper {
  
  static showResults(resultSet,cols, pagesize = 200) { 
    int count = 0
    def totalLength = 0
    def maxWidth = 180
    def border = ''
    def header = ''
    def pagesizeInt = 200
    if(pagesize != null) pagesizeInt = pagesize as int

    cols.each { k, v -> totalLength += v }
    if( totalLength > 180 ) maxWidth = 180/cols.size() //
    cols.each { k, v ->
        def width = v < maxWidth ? v : maxWidth
        def cname = k.length() > width? k[0..(width-2)] + ".." :k
        header <<= sprintf "|%-${width+2}s", cname
        border <<= "+"+"-"*(width+2)
    }

    header <<= "|\n"
    border <<= "+\n"

      print border + header + border
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
    return count
  }

  static showTables(sql) {
    def meta = sql.connection.metaData 
      def tables = meta.getTables(null, null, null, null)  
      def metadataCols = ["Table_Name":50]
      showResults(tables, metadataCols)
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

  static pickColumns(sql, metadata, showKeys = false) {
    def selectedCols = [:]
    def allCols = [:]
    def pk = []
    for(int i=1; i<=metadata.getColumnCount(); i++) {
      if(i == 1 && showKeys) pk = getPK(sql,metadata.getTableName(i))
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
}





