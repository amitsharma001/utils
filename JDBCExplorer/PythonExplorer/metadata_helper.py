#!/usr/bin/env python3
"""
MetaDataHelper - Python equivalent of MetaDataHelper.groovy
Handles database metadata operations
"""

import pandas as pd
from typing import List, Dict, Any
from result_set_helper import ResultSetHelper


class MetaDataHelper:
    def __init__(self, connection):
        """Initialize with a database connection."""
        self.connection = connection
    
    def show_tables(self):
        """Show all tables in the database."""
        try:
            # Get table information
            query = """
            SELECT 
                TABLE_CATALOG as TABLE_CAT,
                TABLE_SCHEMA as TABLE_SCHEM,
                TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
            """
            
            df = pd.read_sql(query, self.connection)
            
            # Display results
            helper = ResultSetHelper(df)
            helper.show_results()
            
        except Exception as e:
            print(f"Error getting tables: {e}")
    
    def show_columns(self, table_name: str):
        """Show columns for a specific table."""
        print(f"Showing columns for {table_name}")
        
        try:
            query = f"""
            SELECT 
                COLUMN_NAME,
                DATA_TYPE as TYPE_NAME,
                CHARACTER_MAXIMUM_LENGTH as COLUMN_SIZE,
                IS_NULLABLE
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_NAME = '{table_name}'
            ORDER BY ORDINAL_POSITION
            """
            
            df = pd.read_sql(query, self.connection)
            
            # Display results
            helper = ResultSetHelper(df)
            helper.show_results()
            
        except Exception as e:
            print(f"Error getting columns for {table_name}: {e}")
    
    def show_keys(self, table_name: str):
        """Show primary keys for a specific table."""
        try:
            query = f"""
            SELECT 
                COLUMN_NAME,
                CONSTRAINT_NAME as PK_Name
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
            WHERE TABLE_NAME = '{table_name}' 
            AND CONSTRAINT_NAME LIKE '%PRIMARY%'
            ORDER BY ORDINAL_POSITION
            """
            
            df = pd.read_sql(query, self.connection)
            
            if df.empty:
                print(f"No primary keys found for table {table_name}")
            else:
                helper = ResultSetHelper(df)
                helper.show_results()
                
        except Exception as e:
            print(f"Error getting primary keys for {table_name}: {e}")
    
    def show_imported_keys(self, table_name: str):
        """Show foreign keys for a specific table."""
        try:
            query = f"""
            SELECT 
                PK.COLUMN_NAME as PKColumn_Name,
                PK.TABLE_NAME as PKTable_Name,
                FK.TABLE_NAME as FKTable_Name,
                FK.COLUMN_NAME as FKColumn_Name,
                PK.CONSTRAINT_NAME as PK_Name,
                FK.CONSTRAINT_NAME as FK_Name
            FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS RC
            JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE PK 
                ON RC.UNIQUE_CONSTRAINT_NAME = PK.CONSTRAINT_NAME
            JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE FK 
                ON RC.CONSTRAINT_NAME = FK.CONSTRAINT_NAME
            WHERE FK.TABLE_NAME = '{table_name}'
            ORDER BY FK.COLUMN_NAME
            """
            
            df = pd.read_sql(query, self.connection)
            
            if df.empty:
                print(f"No foreign keys found for table {table_name}")
            else:
                helper = ResultSetHelper(df)
                helper.show_results()
                
        except Exception as e:
            print(f"Error getting foreign keys for {table_name}: {e}")
    
    def show_table_stats(self, table_pattern: str = '%'):
        """Show table statistics including row counts and column information."""
        try:
            # Get all tables matching the pattern
            query = f"""
            SELECT TABLE_NAME 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_TYPE = 'BASE TABLE' 
            AND TABLE_NAME LIKE '{table_pattern}'
            ORDER BY TABLE_NAME
            """
            
            tables_df = pd.read_sql(query, self.connection)
            
            count = 0
            for _, row in tables_df.iterrows():
                table_name = row['TABLE_NAME']
                count += 1
                
                print(f"\n\n{count}) {table_name}")
                
                try:
                    # Get column information
                    cols_query = f"""
                    SELECT COLUMN_NAME, DATA_TYPE 
                    FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_NAME = '{table_name}'
                    ORDER BY ORDINAL_POSITION
                    """
                    cols_df = pd.read_sql(cols_query, self.connection)
                    
                    # Get row count
                    count_query = f"SELECT COUNT(*) as CNT FROM [{table_name}]"
                    count_df = pd.read_sql(count_query, self.connection)
                    row_count = count_df.iloc[0]['CNT']
                    
                    print(f"{row_count} rows")
                    print("Columns: ", end="")
                    
                    for _, col_row in cols_df.iterrows():
                        col_name = col_row['COLUMN_NAME']
                        col_type = col_row['DATA_TYPE']
                        print(f"{col_name} ({col_type}), ", end="")
                    print()
                    
                except Exception as e:
                    print(f"Could not get metadata for table {table_name}: {e}")
                
                # Ask if user wants to see more every 10 tables
                if count % 10 == 0:
                    response = input("\n\nDo you want to see more tables [Y/N]? ").lower()
                    if response == 'n':
                        break
            
            print("\n")
            
        except Exception as e:
            print(f"Error getting table statistics: {e}")
    
    def write_metadata_to_file(self):
        """Write metadata to CSV files."""
        try:
            # Write table information
            tables_query = """
            SELECT 
                TABLE_CATALOG as TABLE_CAT,
                TABLE_SCHEMA as TABLE_SCHEM,
                TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
            """
            
            tables_df = pd.read_sql(tables_query, self.connection)
            tables_df.to_csv('Tables.csv', index=False)
            print("Written Tables.csv, now writing column information.")
            
            # Write column information
            columns_query = """
            SELECT 
                TABLE_NAME,
                COLUMN_NAME,
                DATA_TYPE,
                CHARACTER_MAXIMUM_LENGTH as COLUMN_SIZE
            FROM INFORMATION_SCHEMA.COLUMNS 
            ORDER BY TABLE_NAME, ORDINAL_POSITION
            """
            
            columns_df = pd.read_sql(columns_query, self.connection)
            columns_df.to_csv('Columns.csv', index=False)
            print("Written Columns.csv")
            
        except Exception as e:
            print(f"Error writing metadata to file: {e}")
