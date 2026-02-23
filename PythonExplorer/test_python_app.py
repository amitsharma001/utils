#!/usr/bin/env python3
"""
Test script for JDBCExplorer Python Edition
Tests basic functionality without requiring actual database connections
"""

import sys
import os

def test_imports():
    """Test that all modules can be imported."""
    print("Testing imports...")
    
    try:
        from connection_manager import ConnectionManager
        print("✓ ConnectionManager imported successfully")
    except ImportError as e:
        print(f"✗ Failed to import ConnectionManager: {e}")
        return False
    
    try:
        from sql_command import SQLCommand
        print("✓ SQLCommand imported successfully")
    except ImportError as e:
        print(f"✗ Failed to import SQLCommand: {e}")
        return False
    
    try:
        from result_set_helper import ResultSetHelper
        print("✓ ResultSetHelper imported successfully")
    except ImportError as e:
        print(f"✗ Failed to import ResultSetHelper: {e}")
        return False
    
    try:
        from metadata_helper import MetaDataHelper
        print("✓ MetaDataHelper imported successfully")
    except ImportError as e:
        print(f"✗ Failed to import MetaDataHelper: {e}")
        return False
    
    try:
        from performance_test import PerformanceTest
        print("✓ PerformanceTest imported successfully")
    except ImportError as e:
        print(f"✗ Failed to import PerformanceTest: {e}")
        return False
    
    return True

def test_connection_manager():
    """Test ConnectionManager functionality."""
    print("\nTesting ConnectionManager...")
    
    try:
        from connection_manager import ConnectionManager
        
        # Test JDBC string parsing - this should now find the parent directory file
        cm = ConnectionManager()
        
        # Test parsing a sample JDBC string
        jdbc_string = "jdbc:salesforce:CredentialsLocation='';InitiateOAuth=GETANDREFRESH;OAuthSettingsLocation='/path/to/oauth.txt';"
        props = cm.parse_jdbc_connection_string(jdbc_string)
        
        expected_props = {
            'salesforce:CredentialsLocation': "''",
            'InitiateOAuth': 'GETANDREFRESH',
            'OAuthSettingsLocation': "'/path/to/oauth.txt'"
        }
        
        if props == expected_props:
            print("✓ JDBC string parsing works correctly")
        else:
            print(f"✗ JDBC string parsing failed. Expected: {expected_props}, Got: {props}")
            return False
        
        # Test driver mapping
        driver_name = "cdata.jdbc.salesforce.SalesforceDriver"
        try:
            module = cm.get_python_connector_class(driver_name)
            print("✓ Driver mapping works correctly")
        except ImportError:
            print("⚠ Driver mapping works but CData libraries not installed (expected)")
        except Exception as e:
            print(f"✗ Driver mapping failed: {e}")
            return False
        
        return True
        
    except Exception as e:
        print(f"✗ ConnectionManager test failed: {e}")
        return False

def test_result_set_helper():
    """Test ResultSetHelper functionality."""
    print("\nTesting ResultSetHelper...")
    
    try:
        import pandas as pd
        from result_set_helper import ResultSetHelper
        
        # Create test data
        test_data = {
            'id': [1, 2, 3],
            'name': ['Alice', 'Bob', 'Charlie'],
            'email': ['alice@example.com', 'bob@example.com', 'charlie@example.com'],
            'created_date': ['2023-01-01', '2023-01-02', '2023-01-03']
        }
        df = pd.DataFrame(test_data)
        
        # Test ResultSetHelper
        helper = ResultSetHelper(df)
        cols = helper.pick_columns()
        
        if len(cols) > 0:
            print("✓ ResultSetHelper column selection works")
        else:
            print("✗ ResultSetHelper column selection failed")
            return False
        
        return True
        
    except Exception as e:
        print(f"✗ ResultSetHelper test failed: {e}")
        return False

def test_config_file():
    """Test that connections.json exists and is valid."""
    print("\nTesting configuration file...")
    
    if not os.path.exists('../connections.json'):
        print("✗ connections.json not found")
        return False
    
    try:
        import json
        with open('../connections.json', 'r') as f:
            config = json.load(f)
        
        if 'connections' in config and 'config' in config:
            print("✓ connections.json is valid")
            print(f"  - Found {len(config['connections'])} connections")
            return True
        else:
            print("✗ connections.json missing required sections")
            return False
            
    except json.JSONDecodeError as e:
        print(f"✗ connections.json is not valid JSON: {e}")
        return False
    except Exception as e:
        print(f"✗ Error reading connections.json: {e}")
        return False

def main():
    """Run all tests."""
    print("JDBCExplorer Python Edition - Test Suite")
    print("=" * 50)
    
    tests = [
        test_imports,
        test_connection_manager,
        test_result_set_helper,
        test_config_file
    ]
    
    passed = 0
    total = len(tests)
    
    for test in tests:
        if test():
            passed += 1
    
    print("\n" + "=" * 50)
    print(f"Test Results: {passed}/{total} tests passed")
    
    if passed == total:
        print("✓ All tests passed! The Python application is ready to use.")
        print("\nTo run the application:")
        print("  python console_prompt.py")
        print("\nTo see help:")
        print("  python console_prompt.py --help")
    else:
        print("✗ Some tests failed. Please check the errors above.")
        sys.exit(1)

if __name__ == "__main__":
    main()
