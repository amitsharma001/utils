#!/usr/bin/env python3
"""
MetaDataHelper - Python equivalent of MetaDataHelper.groovy
Handles database metadata operations
"""

import pandas as pd
from typing import List, Dict, Any
from result_set_helper import ResultSetHelper


class MetaDataHelper:
    def __init__(self, connection, connection_manager=None):
        """Initialize with a database connection and optional connection manager for debug settings."""
        self.connection = connection
        self.connection_manager = connection_manager
    
    def _execute_query_to_dataframe(self, query: str) -> pd.DataFrame:
        """Execute a query and return results as a DataFrame.
        
        Args:
            query: SQL query to execute
            
        Returns:
            DataFrame containing the query results
        """
        cursor = self.connection.cursor()
        cursor.execute(query)
        results = cursor.fetchall()
        columns = [desc[0] for desc in cursor.description]
        df = pd.DataFrame(results, columns=columns)
        cursor.close()
        return df
    
    def show_tables(self):
        """Show all tables in the database."""
        try:
            # Get table information
            query = """
            SELECT CATALOGNAME, SCHEMANAME, TABLENAME, DESCRIPTION
            FROM SYS_TABLES 
            """
            
            # Execute query and convert to DataFrame
            df = self._execute_query_to_dataframe(query)
            
            # Display results
            helper = ResultSetHelper(df)
            helper.show_results()
            
        except Exception as e:
            print(f"Error getting tables: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
    
    def show_columns(self, table_name: str):
        """Show columns for a specific table."""
        print(f"Showing columns for {table_name}")
        
        try:
            query = f"""
            SELECT COLUMNNAME, DATATYPENAME, DISPLAYSIZE, LENGTH, ISNULLABLE
            FROM SYS_TABLECOLUMNS 
            WHERE TABLENAME = '{table_name}'
            """
            
            # Execute query and convert to DataFrame
            df = self._execute_query_to_dataframe(query)
            
            # Display results
            helper = ResultSetHelper(df)
            helper.show_results()
            
        except Exception as e:
            print(f"Error getting columns for {table_name}: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
    
    def show_keys(self, table_name: str):
        """Show primary keys for a specific table."""
        try:
            query = f"""
            SELECT COLUMNNAME, KEYNAME
            FROM SYS_PRIMARYKEYS
            WHERE TABLENAME = '{table_name}'
            """
            
            # Execute query and convert to DataFrame
            df = self._execute_query_to_dataframe(query)
            
            if df.empty:
                print(f"No primary keys found for table {table_name}")
            else:
                helper = ResultSetHelper(df)
                helper.show_results()
                
        except Exception as e:
            print(f"Error getting primary keys for {table_name}: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
    
    def show_imported_keys(self, table_name: str):
        """Show foreign keys for a specific table."""
        try:
            query = f"""
            SELECT TABLENAME, COLUMNNAME, REFERENCEDTABLENAME, REFERENCEDCOLUMNNAME
            FROM SYS_FOREIGNKEYS
            WHERE TABLENAME = '{table_name}'
            """
            
            # Execute query and convert to DataFrame
            df = self._execute_query_to_dataframe(query)
            
            if df.empty:
                print(f"No foreign keys found for table {table_name}")
            else:
                helper = ResultSetHelper(df)
                helper.show_results()
                
        except Exception as e:
            print(f"Error getting foreign keys for {table_name}: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
    
    def show_table_stats(self, table_pattern: str = '%'):
        """Show table statistics including row counts and column information."""
        try:
            # Get all tables matching the pattern
            query = f"""
            SELECT TABLENAME 
            FROM SYS_TABLES 
            WHERE TABLENAME LIKE '{table_pattern}'
            """
            
            # Execute query and convert to DataFrame
            tables_df = self._execute_query_to_dataframe(query)
            
            count = 0
            for _, row in tables_df.iterrows():
                table_name = row['TABLENAME']
                count += 1
                
                print(f"\n\n{count}) {table_name}")
                
                try:
                    # Get column information
                    cols_query = f"""
                    SELECT COLUMNNAME, DATATYPENAME 
                    FROM SYS_TABLECOLUMNS 
                    WHERE TABLENAME = '{table_name}'
                    """
                    # Execute query and convert to DataFrame
                    cols_df = self._execute_query_to_dataframe(cols_query)
                    
                    # Get row count
                    count_query = f"SELECT COUNT(*) as CNT FROM [{table_name}]"
                    # Execute query and convert to DataFrame
                    count_df = self._execute_query_to_dataframe(count_query)
                    row_count = count_df.iloc[0]['CNT']
                    
                    print(f"{row_count} rows")
                    print("Columns: ", end="")
                    
                    for _, col_row in cols_df.iterrows():
                        col_name = col_row['COLUMNNAME']
                        col_type = col_row['DATATYPENAME']
                        print(f"{col_name} ({col_type}), ", end="")
                    print()
                    
                except Exception as e:
                    print(f"Could not get metadata for table {table_name}: {e}")
                    if self.connection_manager and self.connection_manager.is_debug_enabled():
                        import traceback
                        traceback.print_exc()
                
                # Ask if user wants to see more every 10 tables
                if count % 10 == 0:
                    response = input("\n\nDo you want to see more tables [Y/N]? ").lower()
                    if response == 'n':
                        break
            
            print("\n")
            
        except Exception as e:
            print(f"Error getting table statistics: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
    
    def write_metadata_to_file(self):
        """Write metadata to CSV files."""
        try:
            # Write table information
            tables_query = """
            SELECT 
                CATALOGNAME as TABLE_CAT,
                SCHEMANAME as TABLE_SCHEM,
                TABLENAME as TABLE_NAME
            FROM SYS_TABLES 
            ORDER BY TABLENAME
            """
            
            # Execute query and convert to DataFrame
            tables_df = self._execute_query_to_dataframe(tables_query)
            tables_df.to_csv('Tables.csv', index=False)
            print("Written Tables.csv, now writing column information.")
            
            # Write column information
            columns_query = """
            SELECT 
                TABLENAME as TABLE_NAME,
                COLUMNNAME as COLUMN_NAME,
                DATATYPENAME as DATA_TYPE,
                LENGTH as COLUMN_SIZE
            FROM SYS_TABLECOLUMNS 
            ORDER BY TABLENAME
            """
            
            # Execute query and convert to DataFrame
            columns_df = self._execute_query_to_dataframe(columns_query)
            columns_df.to_csv('Columns.csv', index=False)
            print("Written Columns.csv")
            
        except Exception as e:
            print(f"Error writing metadata to file: {e}")
            if self.connection_manager and self.connection_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
