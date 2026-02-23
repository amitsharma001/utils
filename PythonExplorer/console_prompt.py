#!/usr/bin/env python3
"""
ConsolePrompt - Python equivalent of ConsolePrompt.groovy
Main entry point for the JDBCExplorer application
"""

import argparse
import sys
import time
from typing import Optional

from connection_manager import ConnectionManager
from sql_command import SQLCommand
from metadata_helper import MetaDataHelper
from performance_test import PerformanceTest


class ConsolePrompt:
    def __init__(self):
        """Initialize the console prompt."""
        self.conn_manager = None
        self.sql_prompt = "sql >"
        self.current_prompt = self.sql_prompt
    
    def parse_arguments(self):
        """Parse command line arguments."""
        parser = argparse.ArgumentParser(description='PythonExplorer - Database exploration tool')
        parser.add_argument('-f', '--file', default='../connections.json', 
                          help='The connections file. If none is specified connections.json is read.')
        parser.add_argument('-c', '--connection', 
                          help='The name of the connection in the connections file.')
        
        return parser.parse_args()
    
    def show_help(self):
        """Display help information."""
        print("The following commands are supported:")
        print("")
        print("show databases;         Lists the drivers available in connections.json.")
        print("use [db] [#driver] [connection];")
        print("                        Connects to the chosen database. The optional driver input specifies which driver to pick.")
        print("                        Additional connection properties can be specified. Helpful for Offline mode.")
        print("keys [table];           Lists the primary keys in the table.")
        print("fkeys [table];          Lists the foreign keys in the table.")
        print("describe [table];       Lists the columns in the table.")
        print("show tablestats;        Show the row count and column names for each table.")
        print("start batch;            Start a batch command to insert/update/delete.")
        print("performance;            Start a performance test.")
        print("write metadata;         Write metadata in CSV files Tables.csv and Columns.csv.")
        print("help;                   This help.")
        print("config {name} {value}   Change a config setting read from the connections.json.")
        print("exit;                   Exits this program.")
        print("SELECT/INSERT/UPDATE/DELETE statements")
        print("")
        print("Notes:")
        print("1) For parameterized statements, use :name as the parameter placeholder.")
        print("   For example: INSERT into Account (Name,City) values (:name,:city)")
        print("2) Parameters are not allowed in batch commands.")
    
    def process_command(self, command: str):
        """Process a single command."""
        if not command or command.strip() == "":
            return
        
        # Remove trailing semicolon
        if command.endswith(';'):
            command = command[:-1]
        
        # Split command into parts
        command_parts = command.lower().split()
        command_original = command.split()  # Keep original case for case-sensitive databases
        
        if not command_parts:
            return
        
        try:
            # SELECT queries
            if (self.conn_manager.has_valid_connection() and 
                command_parts[0] == "select"):
                cmd = SQLCommand(self.conn_manager.get_connection(), command, 
                               self.conn_manager.config.get('pagesize', 100), self.conn_manager)
                cmd.run_select()
            
            # INSERT, UPDATE, DELETE, CACHE, REPLICATE commands
            elif (self.conn_manager.has_valid_connection() and 
                  command_parts[0] in ["insert", "update", "delete", "cache", "replicate"]):
                cmd = SQLCommand(self.conn_manager.get_connection(), command, 
                               self.conn_manager.config.get('pagesize', 100), self.conn_manager)
                cmd.run_command(is_insert=(command_parts[0] == "insert"))
            
            # Batch commands
            elif (self.conn_manager.has_valid_connection() and 
                  len(command_parts) >= 2 and command_parts[0] == "start" and command_parts[1] == "batch"):
                cmd = SQLCommand(self.conn_manager.get_connection(), command, 
                               self.conn_manager.config.get('pagesize', 100), self.conn_manager)
                cmd.run_batch()
            
            # Performance testing
            elif (self.conn_manager.has_valid_connection() and 
                  command_parts[0] in ["perf", "performance"]):
                pquery = input("Query: ")
                runs_input = input("Runs Default(3): ")
                runs = int(runs_input) if runs_input.strip() else 3
                threads_input = input("Threads Default(1): ")
                threads = int(threads_input) if threads_input.strip() else 1
                rint_input = input("Row Progress Interval Default(25000): ")
                rint = int(rint_input) if rint_input.strip() else 25000
                
                performance = PerformanceTest(self.conn_manager, pquery, runs, threads, rint)
                performance.run_test()
                performance.show_results()
            
            # Show tables
            elif (self.conn_manager.has_valid_connection() and 
                  len(command_parts) >= 2 and command_parts[0] == "show" and command_parts[1] == "tables"):
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).show_tables()
            
            # Write metadata
            elif (self.conn_manager.has_valid_connection() and 
                  len(command_parts) >= 2 and command_parts[0] == "write" and command_parts[1] == "metadata"):
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).write_metadata_to_file()
            
            # Describe table
            elif (self.conn_manager.has_valid_connection() and 
                  command_parts[0].startswith("desc") and len(command_original) > 1):
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).show_columns(command_original[1])
            
            # Show table stats
            elif (self.conn_manager.has_valid_connection() and 
                  len(command_parts) >= 2 and command_parts[0] == "show" and command_parts[1].startswith("tablestats")):
                table_pattern = '%'
                if len(command_parts) > 2:
                    table_pattern = command_parts[2]
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).show_table_stats(table_pattern)
            
            # Show keys
            elif (self.conn_manager.has_valid_connection() and 
                  command_parts[0].startswith("keys") and len(command_original) > 1):
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).show_keys(command_original[1])
            
            # Show foreign keys
            elif (self.conn_manager.has_valid_connection() and 
                  command_parts[0].startswith("fkeys") and len(command_original) > 1):
                MetaDataHelper(self.conn_manager.get_connection(), self.conn_manager).show_imported_keys(command_original[1])
            
            # Show databases
            elif len(command_parts) >= 2 and command_parts[0] == "show" and command_parts[1] == "databases":
                self.conn_manager.show_databases()
            
            # Use database
            elif command_parts[0] == "use" and len(command_original) > 1:
                if self.conn_manager.select_connection(command_original[1]):
                    self.current_prompt = f"{self.conn_manager.selected_connection['name']} >"
            
            # Config command
            elif command_parts[0] == "config" and len(command_parts) == 3:
                self.conn_manager.config[command_parts[1]] = command_parts[2]
                print(f"Config {command_parts[1]} set to {command_parts[2]}")
            
            # Exit commands
            elif command_parts[0] in ["q", "exit", "quit"]:
                self.conn_manager.close_connection()
                print("Goodbye!")
                sys.exit(0)
            
            # Close connection
            elif command_parts[0] == "close":
                self.current_prompt = self.sql_prompt
                self.conn_manager.close_connection()
            
            # Help command
            elif command_parts[0] == "help":
                self.show_help()
            
            # Empty command
            elif command.strip() == "":
                pass
            
            else:
                print(f"Unsupported command: [{command}]. You must select a database before running database commands.")
        
        except Exception as ex:
            print(f"Error: {ex}")
            print("")
    
    def run(self):
        """Run the main application loop."""
        args = self.parse_arguments()
        
        # Initialize connection manager
        try:
            # If no file specified, let ConnectionManager find it automatically
            config_file = args.file if args.file != 'connections.json' else None
            self.conn_manager = ConnectionManager(config_file)
        except Exception as e:
            print(f"Error initializing connection manager: {e}")
            sys.exit(1)
        
        # Auto-connect if specified
        if args.connection:
            self.process_command(f"use {args.connection}")
        
        print("JDBCExplorer Python Edition")
        print("Type 'help' for available commands")
        print("Type 'exit' to quit")
        print()
        
        # Main command loop
        try:
            while True:
                try:
                    command = input(self.current_prompt)
                    self.process_command(command)
                except KeyboardInterrupt:
                    print("\nUse 'exit' to quit the application")
                except EOFError:
                    print("\nGoodbye!")
                    break
        except KeyboardInterrupt:
            print("\nGoodbye!")
        finally:
            if self.conn_manager:
                self.conn_manager.close_connection()


def main():
    """Main entry point."""
    console = ConsolePrompt()
    console.run()


if __name__ == "__main__":
    main()
