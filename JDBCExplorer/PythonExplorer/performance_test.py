#!/usr/bin/env python3
"""
PerformanceTest - Python equivalent of PerformanceTest.groovy
Handles performance testing of queries
"""

import time
import threading
from typing import List, Dict, Any
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed


class PerformanceTest:
    def __init__(self, conn_manager, query: str, runs: int = 3, threads: int = 1, row_interval: int = 25000):
        """Initialize performance test with connection manager, query, and test parameters."""
        self.conn_manager = conn_manager
        self.query = query
        self.runs = runs
        self.threads = threads
        self.row_interval = row_interval
        self.results = []
        self.test_run_time = None
        self.total_runtime = None
    
    def write_interval_information(self, rows: int, tid: int, time_start: float, last_interval_time: float) -> float:
        """Write interval information for progress tracking."""
        now = time.time()
        runtime = now - time_start
        runtime_interval = now - last_interval_time
        
        print(f"Thread: {tid} Rows: {rows:8d} Time: {runtime*1000:6.0f} ms Interval Time: {runtime_interval*1000:6.0f}")
        return now
    
    def run_single_test(self, run_num: int) -> Dict[str, Any]:
        """Run a single performance test."""
        time_start = time.time()
        last_interval_time = time_start
        tid = threading.current_thread().ident
        rows = 0
        cols = 0
        
        print(f"\n*** Starting Run {run_num} Thread {tid} ***")
        
        try:
            # Execute query and iterate through cursor for real-time progress reporting
            connection = self.conn_manager.get_connection()
            cursor = connection.cursor()
            cursor.execute(self.query)
            
            # Get column count from cursor description
            cols = len(cursor.description)
            
            # Iterate through results and report progress
            while True:
                row = cursor.fetchone()
                if row is None:
                    break
                rows += 1  
                # Report progress at intervals
                if rows % self.row_interval == 0:
                    last_interval_time = self.write_interval_information(rows, tid, time_start, last_interval_time)
            
            cursor.close()
            
            # Final interval report
            last_interval_time = self.write_interval_information(rows, tid, time_start, last_interval_time)
            runtime = last_interval_time - time_start
            
            print(f"\n*** Run {run_num} Completed Thread {tid} *** Total Time: {runtime:.3f}s")
            
            return {
                'runtime': runtime,
                'rows': rows,
                'cols': cols,
                'success': True
            }
            
        except Exception as ex:
            print(f"Thread: {tid} Error at row {rows}: {ex}")
            if self.conn_manager.is_debug_enabled():
                import traceback
                traceback.print_exc()
            return {
                'runtime': 0,
                'rows': rows,
                'cols': cols,
                'success': False,
                'error': str(ex)
            }
    
    def run_test(self):
        """Run the performance test with multiple threads and runs."""
        self.test_run_time = time.time()
        
        with ThreadPoolExecutor(max_workers=self.threads) as executor:
            # Submit all test runs
            future_to_run = {
                executor.submit(self.run_single_test, i): i 
                for i in range(1, self.runs + 1)
            }
            
            # Collect results
            for future in as_completed(future_to_run):
                result = future.result()
                self.results.append(result)
        
        self.total_runtime = time.time() - self.test_run_time
    
    def show_results(self, append_to_file: bool = True):
        """Display performance test results."""
        success_count = 0
        total_time = 0
        rows = 0
        cols = 0
        
        for result in self.results:
            if result['success']:
                success_count += 1
                total_time += result['runtime']
                rows = result['rows']
                cols = result['cols']
        
        if success_count > 0:
            avg_time = total_time / success_count
            
            perf_results = f"\n\n********* Performance Results: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(self.test_run_time))} **************\n"
            perf_results += self.conn_manager.show_database_info()
            perf_results += f"\nQuery: {self.query}, Rows: {rows}, Cols: {cols}"
            perf_results += f"\nRuns: {self.runs} Threads: {self.threads}"
            perf_results += f"\nAverage time: {avg_time*1000:.0f} ms ({avg_time:.3f}s) per thread."
            perf_results += f"\nTotal time taken for {self.runs} runs: {self.total_runtime*1000:.0f}ms ({self.total_runtime:.3f}s)\n"
            
            print(perf_results)
            
            # Write to log file if configured
            if append_to_file and hasattr(self.conn_manager, 'config') and 'logdir' in self.conn_manager.config:
                import os
                log_dir = self.conn_manager.config['logdir']
                if os.path.exists(log_dir):
                    log_file = os.path.join(log_dir, f"{self.conn_manager.selected_connection['driver']}.perf.txt")
                    with open(log_file, 'a') as f:
                        f.write(perf_results)
        else:
            print("No successful test runs to report.")
