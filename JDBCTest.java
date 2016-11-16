package cdata.test;
import java.sql.*;

public class JDBCTest {
	public static void main(String[] args) throws Exception {
		Class.forName("cdata.jdbc.salesforce.SalesforceDriver");
		Connection conn = DriverManager.getConnection("jdbc:salesforce:user=support@nsoftware.com;password=!rssbus");
		PreparedStatement stat = conn.prepareStatement("SELECT Industry,COUNT(*) FROM Account GROUP BY Industry",Statement.RETURN_GENERATED_KEYS);
		stat.execute();
		ResultSet r = stat.getResultSet();
		int count = 0;
		while(r.next()) {
			count++;
		}
		r.close();
		System.out.println("Count "+count);
		count = 0;
		stat.execute();
		r = stat.getResultSet();
		while(r.next()) {
			count++;
		}
		System.out.println("Count "+count);
	}
}
