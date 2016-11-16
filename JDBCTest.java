package cdata.test;
import java.util.Date;
import java.sql.*;
import java.util.Vector;

public class JDBCTest {
	
	public static void preparedStatement(String query, Connection conn) throws Exception {
		ResultSet r = null;
		try {
			PreparedStatement stat = conn.prepareStatement(query);
			stat.execute();
			r = stat.getResultSet();
			processResults(r);
		} finally {
			if(r != null) r.close();
		}
	}
	
	public static void processResults(ResultSet rs) throws Exception {
		int count = 0;
		ResultSetMetaData metadata =  rs.getMetaData();
		int columnCount = metadata.getColumnCount();
	    int rowsPrinted = 0;
	    
	    String header = "";
   	 	for (int i = 1; i <= columnCount; i++) {
   	 	  header += ((i==1? "":", ")+ metadata.getColumnName(i));
   	 	}
   	 	header += "\n--------------------";
	    
	    while (rs.next()) { 
	    	if(rowsPrinted % 10 == 0) {
	    		if(rowsPrinted != 1) System.out.println(); 
	    		System.out.print(header);
	    	}
	    	rowsPrinted++;
	    	System.out.println();
	    	System.out.print(rowsPrinted+") ");
	    	for (int i = 1; i <= columnCount; i++) {
	    		System.out.print((i==1? "":", ")+(rs.getString(i) == null ? "NULL" : rs.getString(i)));
	    	} 
	    }
	}
	
	public static void batchInsertWithTemp(Connection conn) throws Exception {
		String query = "INSERT INTO Account#TEMP (Name, Custom_Date_Time__c) VALUES(?,?)";
		String query2 = "INSERT INTO Account (Name, Custom_Date_Time__c) SELECT Name, Custom_Date_Time__c FROM Account#Temp";
		PreparedStatement pstmt = conn.prepareStatement(query);
		 
		pstmt.setString(1, "Jon Doe1");
		long time = (new Date()).getTime()+ 1000L*60*60*8;
		pstmt.setTimestamp(2,new Timestamp(time));
		System.out.println(pstmt.executeUpdate());
		 
		pstmt.setString(1, "Jon Doe2");
		pstmt.setTimestamp(2,new Timestamp(time));
		System.out.println(pstmt.executeUpdate());
		
		PreparedStatement pstmt2 = conn.prepareStatement(query2);
		System.out.println(pstmt2.executeUpdate());
	}
	
	public static void batchInsertTest(Connection conn) throws Exception {
		String query = "INSERT INTO Account (Name, Custom_Date_Time__c) VALUES(?,?)";
		PreparedStatement pstmt = conn.prepareStatement(query);
		 
		pstmt.setString(1, "Jon Doe1");
		long time = (new Date()).getTime()+ 1000L*60*60*8;
		pstmt.setTimestamp(2,new Timestamp(time));
		pstmt.addBatch();
		 
		pstmt.setString(1, "Jon Doe2");
		pstmt.setTimestamp(2,new Timestamp(time));
		pstmt.addBatch();
		 
		int[] r = pstmt.executeBatch();
		for(int i: r) System.out.println(i);
		
		ResultSet rs = pstmt.getGeneratedKeys();
		processResults(rs);
	}
	
	public static void main(String[] args) throws Exception {
		Class.forName("cdata.jdbc.salesforce.SalesforceDriver");
		Connection conn = DriverManager.getConnection("jdbc:salesforce:user=support@nsoftware.com;password=!rssbus;Logfile=D:\\Logs\\JDBCSF.txt;Verbosity=3");
		batchInsertWithTemp(conn);
		//preparedStatement("select Id from Account where OwnerID in (SELECT Id from User Where FirstName = 'Support')", conn);
	}
}
