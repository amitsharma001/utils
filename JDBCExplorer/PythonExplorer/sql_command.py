#!/usr/bin/env python3
"""
SQLCommand - Python equivalent of SQLCommand.groovy
Handles SQL query execution and parameter handling
"""

import time
import re
from typing import Dict, Any, List
import pandas as pd


class SQLCommand:
    def __init__(self, connection, query: str, page_size: int = 100):
        """Initialize SQL command with connection, query, and page size."""
        self.connection = connection
        self.query = query
        self.page_size = page_size
        self.params = {}
    
    def get_params_from_console(self):
        """Get parameter values from console input for parameterized queries."""
        # Find all parameter placeholders like :name
        param_pattern = r':(\w+)'
        params = re.findall(param_pattern, self.query)
        
        for param in params:
            if param not in self.params:
                value = input(f"{param}: ")
                self.params[param] = value
    
    def replace_params_in_query(self, query: str, params: Dict[str, str]) -> str:
        """Replace parameter placeholders with actual values."""
        for param, value in params.items():
            query = query.replace(f':{param}', f"'{value}'")
        return query
    
    def run_select(self):
        """Execute a SELECT query and display results."""
        self.get_params_from_console()
        
        start_time = time.time()
        
        try:
            # Replace parameters in query
            final_query = self.replace_params_in_query(self.query, self.params)
            
            # Execute query and get results as DataFrame
            df = pd.read_sql(final_query, self.connection)
            
            # Display results using ResultSetHelper
            from result_set_helper import ResultSetHelper
            helper = ResultSetHelper(df)
            helper.show_results(page_size=self.page_size)
            
            duration = time.time() - start_time
            print(f"Total Time: {duration:.3f} seconds")
            
        except Exception as e:
            print(f"Error executing query: {e}")
    
    def run_command(self, is_insert: bool = False):
        """Execute INSERT, UPDATE, DELETE, or other non-SELECT commands."""
        self.get_params_from_console()
        
        start_time = time.time()
        
        try:
            # Replace parameters in query
            final_query = self.replace_params_in_query(self.query, self.params)
            
            cursor = self.connection.cursor()
            cursor.execute(final_query)
            
            if is_insert:
                # For INSERT statements, get generated keys if available
                try:
                    generated_keys = cursor.fetchall()
                    if generated_keys:
                        print(f"Generated Keys: {generated_keys}")
                except:
                    pass
            
            self.connection.commit()
            cursor.close()
            
            duration = time.time() - start_time
            print(f"Total Time: {duration:.3f} seconds")
            
        except Exception as e:
            print(f"Error executing command: {e}")
            self.connection.rollback()
    
    def run_batch(self):
        """Execute batch commands."""
        print("Enter batch command, type [end] to finish and commit the batch.")
        
        cursor = self.connection.cursor()
        commands = []
        
        try:
            while True:
                command = input("batchcmd > ")
                if not command:
                    continue
                if command.lower() == "end":
                    break
                commands.append(command)
            
            # Execute all commands in batch
            for command in commands:
                cursor.execute(command)
            
            self.connection.commit()
            print(f"Executed {len(commands)} commands successfully")
            
        except Exception as e:
            print(f"Error in batch execution: {e}")
            self.connection.rollback()
        finally:
            cursor.close()
