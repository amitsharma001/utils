#!/usr/bin/env python3
"""
ResultSetHelper - Python equivalent of ResultSetHelper.groovy
Handles result set display and formatting
"""

import pandas as pd
from typing import List, Dict, Any, Optional
from tabulate import tabulate


class ResultSetHelper:
    def __init__(self, data):
        """Initialize with either a pandas DataFrame or a list of dictionaries."""
        if isinstance(data, pd.DataFrame):
            self.df = data
        else:
            # Convert list of dictionaries to DataFrame
            self.df = pd.DataFrame(data)
    
    def pick_columns(self) -> Dict[str, int]:
        """Select columns to display based on smart column selection."""
        if self.df.empty:
            return {}
        
        all_cols = {}
        selected_cols = {}
        
        # Calculate column widths based on data types and content
        for col in self.df.columns:
            col_type = str(self.df[col].dtype)
            if 'datetime' in col_type:
                width = 22
            elif 'int' in col_type:
                width = 10
            elif 'float' in col_type:
                width = 15
            else:
                # For string columns, use max length of data or column name
                max_len = max(
                    len(str(col)),
                    self.df[col].astype(str).str.len().max() if not self.df[col].empty else 0
                )
                width = min(max_len, 50)  # Cap at 50 characters
        
            all_cols[col] = width
        
        # Smart column selection: prefer id, name, date columns
        selected = False
        for col in all_cols:
            col_lower = col.lower()
            
            # Select ID columns
            if 'id' in col_lower and not selected:
                selected_cols[col] = all_cols[col]
                selected = True
            
            # Select name columns
            if 'name' in col_lower and col not in selected_cols:
                selected_cols[col] = all_cols[col]
            
            # Select date columns
            if 'date' in col_lower and col not in selected_cols:
                selected_cols[col] = all_cols[col]
        
        # Fill up to 5 columns
        for col in all_cols:
            if len(selected_cols) >= 5:
                break
            if col not in selected_cols:
                selected_cols[col] = all_cols[col]
        
        return selected_cols
    
    def show_results(self, cols: Optional[Dict[str, int]] = None, page_size: int = 200):
        """Display results in a formatted table."""
        if self.df.empty:
            print("No results to display.")
            return
        
        if cols is None:
            cols = self.pick_columns()
        
        # Select only the columns we want to display
        display_df = self.df[list(cols.keys())]
        
        # Format the display
        total_rows = len(display_df)
        start_row = 0
        
        while start_row < total_rows:
            end_row = min(start_row + int(page_size), total_rows)
            page_df = display_df.iloc[start_row:end_row]
            
            # Display the page
            print(f"\nRows {start_row + 1}-{end_row} of {total_rows}:")
            print(tabulate(page_df, headers='keys', tablefmt='grid', showindex=False))
            
            # Ask if user wants to see more
            if end_row < total_rows and page_size != -1:
                response = input(f"\n{end_row} rows. Do you want to see more [Y/N]? ").lower()
                if response != 'y':
                    break
            
            start_row = end_row
        
        print(f"\nTotal Rows: {total_rows}")
    
    def write_result_set_to_file(self, filename: str, columns: List[str]):
        """Write results to a CSV file."""
        try:
            # Select only the specified columns
            if columns:
                write_df = self.df[columns]
            else:
                write_df = self.df
            
            # Write to CSV
            write_df.to_csv(filename, index=False, mode='a', header=not self._file_exists(filename))
            print(f"Data written to {filename}")
            
        except Exception as e:
            print(f"Error writing to file {filename}: {e}")
    
    def _file_exists(self, filename: str) -> bool:
        """Check if file exists."""
        import os
        return os.path.exists(filename)
