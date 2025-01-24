package JDBCExplorer;
import groovy.sql.Sql

class MetaDataHelper {
    Sql sql;

    public MetaDataHelper(Sql sql) {
        this.sql = sql;
    }

    public showTables() {
        def meta = sql.connection.metaData;
        def tables = meta.getTables(null,null, null, null);
        def metadataCols = ["TABLE_CAT":20,"TABLE_SCHEM":20,"TABLE_NAME":50];
        new ResultSetHelper(tables).showResults(metadataCols);
    }

    public showColumns(table) {
        print "Showing columns for $table\n";
        def meta = sql.connection.metaData;
        def cols = meta.getColumns(null, null, table, null);
        def metadataCols = ["COLUMN_NAME":40,"TYPE_NAME":20,"COLUMN_SIZE":20,"IS_NULLABLE":20];
        new ResultSetHelper(cols).showResults(metadataCols);
    }

    public showKeys(table) {
        def meta = sql.connection.metaData;
        def cols = meta.getPrimaryKeys(null, null, table);
        def metadataCols = ["Column_Name":40,"PK_Name":20];
        new ResultSetHelper(cols).showResults(metadataCols);
    }

    public showImportedKeys(table) {
        def meta = sql.connection.metaData;
        def cols = meta.getImportedKeys(null, null, table);
        def metadataCols = ["PKColumn_Name":15,"PKTable_Name":15,"FKTable_Name":15,"FKColumn_Name":15,"PK_Name":20,"FK_Name":20];
        new ResultSetHelper(cols).showResults(metadataCols);
    }

    public showTableStats(tablePattern='%') {
        def meta = sql.connection.metaData;
        def tables = meta.getTables(null, null, tablePattern, null);
        def count = 0;
        while(tables.next()) {
            def table = tables.getObject("Table_Name").toString();
            try {
                count++;
                def cols = meta.getColumns(null, null, table, null);
                def cquery = "SELECT count(*) as CNT from ["+table+"]";
                def rows = sql.rows(cquery)["CNT"][0];
                printf("\n\n%d) %s: %d rows\n Columns: ",count,table, rows);
                while(cols.next()) {
                    printf("%s (%s), ",cols.getObject("Column_Name").toString(), cols.getObject("Type_Name").toString());
                }
                if(count % 10 == 0) {
                    print "\n\nDo you want to see more tables [Y/N]? ";
                    if(System.console().readLine().toLowerCase() == 'n') break;
                }
            } catch (Exception e){
                printf("Could not get metadata for table %s\n",table);
            }
        }
        println "\n";
    }

    public writeMetaDataToFile() {
        def meta = sql.connection.metaData;
        def tables = meta.getTables(null, null,'%', null);
        def tableColumns = ["TABLE_CAT","TABLE_SCHEM","TABLE_NAME"];
        writeResultSetToFile(tables,"Tables.csv","Metadata",tableColumns);
        printf("Written Tables.csv, now writing column information.\n");
        tables = meta.getTables(null, null,'%', null);
        while(tables.next()) {
            def table = tables.getObject("Table_Name").toString();
            try {
                def cols = meta.getColumns(null, null, table, null);
                tableColumns = ["TABLE_NAME","COLUMN_NAME","DATA_TYPE","TYPE_NAME","COLUMN_SIZE"];
                writeResultSetToFile(cols,"Columns.csv",table,tableColumns);
                printf("Written columns for %s\n",table);
            } catch(Exception e) {
                printf("Could not get metadata for %s due to error: %s\n",table,e.Message)
            }
        }
    }
}

