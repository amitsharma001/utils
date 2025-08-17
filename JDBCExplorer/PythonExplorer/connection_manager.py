#!/usr/bin/env python3
"""
ConnectionManager - Python equivalent of ConnectionManager.groovy
Handles database connections using CData Python libraries
"""

import json
import os
import re
from typing import Dict, Any, Optional
import importlib


class ConnectionManager:
    def __init__(self, config_file: str = None):
        """Initialize the connection manager with a configuration file."""
        if config_file is None:
            # Try to find connections.json in parent directory first, then current directory
            parent_config = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'connections.json')
            
            if os.path.exists(parent_config):
                config_file = parent_config
            else:
                raise FileNotFoundError(f"connections.json not found in parent directory ({parent_config}) or current directory ({current_config})")
        
        if not os.path.exists(config_file):
            raise FileNotFoundError(f"The connection file chosen {os.path.abspath(config_file)} does not exist.")
        
        with open(config_file, 'r') as f:
            self.json_obj = json.load(f)
        
        self.selected_connection = None
        self.connection = None
        self.config = self.json_obj.get('config', {})
    
    def get_connection(self):
        """Get the current database connection."""
        if self.connection is None:
            raise Exception("No connection has been selected. Select a valid connection before executing SQL queries.")
        return self.connection
    
    def has_valid_connection(self) -> bool:
        """Check if there's a valid connection."""
        return self.connection is not None
    
    def close_connection(self):
        """Close the current connection."""
        if self.connection:
            self.connection.close()
            self.connection = None
    
    def parse_jdbc_connection_string(self, jdbc_string: str) -> Dict[str, str]:
        """Parse JDBC connection string and convert to Python connector properties."""
        properties = {}
        
        # Remove jdbc: prefix
        if jdbc_string.startswith('jdbc:'):
            jdbc_string = jdbc_string[5:]
        
        # Split by semicolon and parse key-value pairs
        parts = jdbc_string.split(';')
        for part in parts:
            if '=' in part:
                key, value = part.split('=', 1)
                key = key.strip()
                # Only remove database type prefix from property keys, not the connection string itself
                if ':' in key and not key.startswith('salesforce') and not key.startswith('jira') and not key.startswith('snowflake') and not key.startswith('googlebigquery') and not key.startswith('netsuite'):
                    key = key.split(':', 1)[1]
                properties[key] = value.strip()
        
        return properties
    
    def get_python_connector_class(self, driver_name: str):
        """Get the appropriate Python connector class based on the JDBC driver."""
        # Split the driver name by periods
        parts = driver_name.split('.')
        
        # Check if this is a CData driver
        if len(parts) < 3 or parts[0] != 'cdata':
            raise ValueError(f"Unsupported driver: {driver_name}. This program only supports CData connectors for now.")
        
        # Extract the module name: cdata.{third_part}
        # Example: cdata.jdbc.salesforce.SalesforceDriver -> cdata.salesforce
        if len(parts) >= 3:
            module_name = f"{parts[0]}.{parts[2]}"
        else:
            raise ValueError(f"Invalid CData driver format: {driver_name}")
        
        try:
            module = importlib.import_module(module_name)
            return module
        except ImportError as e:
            raise ImportError(f"Could not import {module_name}: {e}")
    
    def select_connection(self, connection_name: str, show_details: bool = True) -> bool:
        """Select and establish a connection to the specified database."""
        # Find the connection configuration
        self.selected_connection = None
        for conn in self.json_obj['connections']:
            if conn['name'] == connection_name:
                self.selected_connection = conn
                break
        
        if self.selected_connection is None:
            print(f"The selected connection {connection_name} does not exist.")
            return False
        
        try:
            # Parse JDBC connection string into individual properties
            jdbc_props = self.parse_jdbc_connection_string(self.selected_connection['connection'])
            
            # Get Python connector module
            connector_module = self.get_python_connector_class(self.selected_connection['driver'])
            
            # For CData Python connectors, we need to pass the connection string directly
            # Remove the 'jdbc:' prefix and the database type prefix
            connection_string = self.selected_connection['connection']
            if connection_string.startswith('jdbc:'):
                connection_string = connection_string[5:]
            
            # Remove the database type prefix (e.g., 'salesforce:', 'jira:', etc.)
            if ':' in connection_string:
                connection_string = connection_string.split(':', 1)[1]
            
            # Create connection with the cleaned connection string
            self.connection = connector_module.connect(connection_string)
            
            if show_details:
                print(self.show_database_info())
            
            return True
            
        except Exception as e:
            print(f"Error connecting to {connection_name}: {e}")
            self.connection = None
            return False
    
    def show_database_info(self, connection=None) -> str:
        """Show database connection information."""
        if connection is None:
            connection = self.selected_connection
        
        if connection is None:
            return ""
        
        info = f"Name      : {connection['name']}\n"
        info += f"Connection: {connection['connection']}\n"
        info += f"Driver    : {connection['driver']}\n"
        
        if self.connection:
            info += f"Status    : Connected\n"
        
        return info
    
    def show_databases(self):
        """Show all available database connections."""
        print("-" * 33)
        for conn in self.json_obj['connections']:
            print(self.show_database_info(conn))
            print("-" * 33)
