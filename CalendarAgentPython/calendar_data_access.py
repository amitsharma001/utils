import json
import os
import sys
from typing import Any


class CalendarDataAccess:
    def __init__(self, config_path: str = None):
        if config_path is None:
            config_path = os.path.join(os.path.dirname(__file__), "config.json")

        with open(config_path, "r") as f:
            self.config = json.load(f)

        # Add PythonExplorer to path so ConnectionManager can be imported
        python_explorer_path = os.path.join(
            os.path.dirname(__file__), "..", "JDBCExplorer", "PythonExplorer"
        )
        python_explorer_path = os.path.abspath(python_explorer_path)
        if python_explorer_path not in sys.path:
            sys.path.insert(0, python_explorer_path)

        from connection_manager import ConnectionManager

        connections_file = self.config["connectionsFile"]
        cm = ConnectionManager(config_file=connections_file)

        connection_name = self.config["exchangeConnectionName"]
        success = cm.select_connection(connection_name)
        if not success:
            raise RuntimeError(f"Failed to connect to '{connection_name}'")

        self._cm = cm
        self._connection = cm.get_connection()

    def get_cursor(self):
        return self._connection.cursor()

    def close(self):
        self._cm.close_connection()
