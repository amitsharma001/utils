package JDBCExplorer
import java.sql.*;

public class ResultSetHelper {
    ResultSet results;

    public ResultSetHelper(ResultSet results) {
        this.results = results;
    }

    private Map pickColumns() {
        Map selectedCols = [:], allCols = [:];
        
        ResultSetMetaData metadata = results.getMetaData();

        for(int i=1; i<=metadata.getColumnCount(); i++) {
            def size = metadata.getPrecision(i);
            def type = metadata.getColumnTypeName(i);
            if(type == "TIMESTAMP") size = 22;
            else if (type == "DATE") size = 10;
            else if (type == "INT") size = 10;
            else if (type == "VARCHAR") size > 5? size: 10;
            allCols[metadata.getColumnLabel(i)] = size;
        }
        // get one column each that matches name, id, and, date
        boolean selId = false, selName = false, selDate = false;
        allCols.each {k, v -> 
            if (k =~/id/ && !selId) {
                selectedCols[k] = v;
                selId = true;
            }
            if (k =~/name/ && !selName) {
                selectedCols[k] = v;
                selName = true;
            }
            if (k =~/date/ && !selDate) {
                selectedCols[k] = v;
                selDate = true;
            }
        }

        // fill upto 5 columns
        allCols.each { k, v ->
            if (selectedCols.size() >= 5) return;
            if (!selectedCols.containsKey(k)) selectedCols[k] = v;
        }
        return selectedCols
    }

    public void showResults(Map cols = pickColumns(), int pagesize = 200) { 
        int totalLength = 0;
        int maxWidth = 25;
        String border = "";
        String header = "";

        if( cols == null) cols = pickColumns();

        cols.each { k, v -> totalLength += v }
        if( totalLength < 300 ) maxWidth = 50;
        cols.each { k, v ->
            int width = v < maxWidth ? v : maxWidth;
            String cname = k.length() > width? k[0..(width-2)] + ".." :k;
            header <<= sprintf "|%-${width+2}s", cname;
            border <<= "+"+"-"*(width+2);
        }

        header <<= "|\n";
        border <<= "+\n";

        print border + header + border;
        int count = 0;
        boolean readToEnd = true;

        while(results.next()) {
            count++;
            cols.each { k, v ->
                String value = results.getObject(k) == null? "NULL" : results.getObject(k).toString();
                int width = v < maxWidth ? v : maxWidth;
                value = value.length() > width ? value[0..(width-3)] + ".." : value;
                printf "| %-${width+1}s", value; // We had an extra space in the header
            }
            print "|\n";
            if(pagesize != -1 && count % pagesize == 0) {
                print border;
                print "$count rows. Do you want to see more [Y/N]? ";
                if(System.console().readLine().toLowerCase() == 'y') {
                    print border + header + border;
                } else {
                    readToEnd = false;
                    break;
                }
            }
        }
        if(readToEnd) {
            print border;
            println "Total Rows: $count.";
        }
    }

    public void writeResultSetToFile(String filename,List columns) {
        File resultFile = new File(filename);
        String headers = "";
        if(!f.exists()) headers = columns.join(", ")   ;

        resultFile.withWriterAppend { writer ->
            if (headers != "") writer.writeLine(headers);
            while(resultSet.next()) {
                String rowvalues = "";
                boolean first = true;
                for(column in columns) {
                    String value = resultSet.getString(column);
                    if (first) first = false;
                    else rowvalues += ", ";
                    rowvalues += value;
                }
                writer.writeLine(rowvalues);
            }
        }
    }
}
