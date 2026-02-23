#!/usr/bin/env python3
"""
DataCollector — fetches Salesforce and Jira data for a Closed Lost opportunity.
"""

import os
import re
import sys


def _bootstrap_connection_manager():
    """Inject PythonExplorer into sys.path so ConnectionManager can be imported."""
    python_explorer_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "PythonExplorer")
    )
    if python_explorer_path not in sys.path:
        sys.path.insert(0, python_explorer_path)


_bootstrap_connection_manager()
from connection_manager import ConnectionManager  # noqa: E402


class DataCollector:
    def __init__(self, config: dict):
        self._config = config

        self._sf_cm = ConnectionManager(config_file=config["connections_file"])
        success = self._sf_cm.select_connection(config["sf_connection"], show_details=False)
        if not success:
            raise RuntimeError(f"Failed to connect to Salesforce ('{config['sf_connection']}')")

        self._jira_cm = ConnectionManager(config_file=config["connections_file"])
        success = self._jira_cm.select_connection(config["jira_connection"], show_details=False)
        if not success:
            raise RuntimeError(f"Failed to connect to Jira ('{config['jira_connection']}')")

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _execute_sf(self, sql: str) -> list[dict]:
        conn = self._sf_cm.get_connection()
        cursor = conn.cursor()
        try:
            cursor.execute(sql)
            columns = [desc[0] for desc in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]
        finally:
            cursor.close()

    def _execute_jira(self, sql: str) -> list[dict]:
        conn = self._jira_cm.get_connection()
        cursor = conn.cursor()
        try:
            cursor.execute(sql)
            columns = [desc[0] for desc in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]
        finally:
            cursor.close()

    @staticmethod
    def _in_clause(ids: list[str]) -> str:
        escaped = ", ".join(f"'{str(i).replace(chr(39), chr(39)*2)}'" for i in ids)
        return f"({escaped})"

    @staticmethod
    def _extract_jira_keys(cases: list[dict]) -> list[str]:
        """Pull Jira issue keys from JIRA_Reference__c URLs; deduplicate; ignore nulls."""
        keys = []
        for case in cases:
            ref = case.get("JIRA_Reference__c")
            if not ref:
                continue
            # Last path segment, e.g. https://jira.cdata.com/browse/SUP-1234 → SUP-1234
            match = re.search(r"/([A-Z]+-\d+)(?:[/?#].*)?$", str(ref))
            if match:
                key = match.group(1)
                if key not in keys:
                    keys.append(key)
        return keys

    # ------------------------------------------------------------------
    # Query methods
    # ------------------------------------------------------------------

    def get_closed_lost_opps(self) -> list[dict]:
        since = self._config["closed_lost_since"]
        sql = f"""
SELECT DISTINCT o.[Id], o.[Name], o.[CloseDate], o.[Amount], o.[Description]
FROM [Opportunity] o
INNER JOIN [OpportunityLineItem] oli ON oli.[OpportunityId] = o.[Id]
WHERE o.[StageName] = 'Closed Lost'
  AND oli.[Product_Group__c] IN ('Sync', 'Connect Cloud')
  AND o.[CloseDate] >= '{since}'
ORDER BY o.[CloseDate] DESC
"""
        return self._execute_sf(sql.strip())

    def get_opp_products(self, opp_id: str) -> list[dict]:
        sql = f"""
SELECT oli.[Name], oli.[ProductCode], oli.[Application__c], oli.[Product_Group__c],
       oli.[Edition__c], oli.[Product_Lifecycle__c], oli.[Quantity],
       oli.[UnitPrice], oli.[TotalPrice], oli.[ACV__c], oli.[NewBusiness__c]
FROM [OpportunityLineItem] oli
WHERE oli.[OpportunityId] = '{opp_id}'
"""
        return self._execute_sf(sql.strip())

    def get_opp_cases(self, opp_id: str) -> list[dict]:
        sql = f"""
SELECT c.[Id] AS CaseId, c.[CaseNumber], c.[Subject], c.[Status], c.[JIRA_Reference__c]
FROM [Opportunity] o
INNER JOIN [Account] a ON o.[AccountId] = a.[Id]
INNER JOIN [Case] c    ON c.[AccountId] = a.[Id]
WHERE o.[Id] = '{opp_id}'
ORDER BY c.[CreatedDate] DESC
"""
        return self._execute_sf(sql.strip())

    def get_case_emails(self, case_ids: list[str]) -> list[dict]:
        sql = f"""
SELECT em.[MessageDate], em.[FromName], em.[Subject], em.[Incoming], em.[TextBody]
FROM [EmailMessage] em
WHERE em.[ParentId] IN {self._in_clause(case_ids)}
ORDER BY em.[MessageDate] ASC
"""
        return self._execute_sf(sql.strip())

    def get_case_comments(self, case_ids: list[str]) -> list[dict]:
        sql = f"""
SELECT cc.[CreatedDate], cc.[IsPublished], cc.[CommentBody], cc.[ParentId]
FROM [CaseComment] cc
WHERE cc.[ParentId] IN {self._in_clause(case_ids)}
ORDER BY cc.[CreatedDate] ASC
"""
        return self._execute_sf(sql.strip())

    def get_jira_issues(self, keys: list[str]) -> list[dict]:
        sql = f"""
SELECT [Key], [Summary], [Description], [StatusName], [PriorityName],
       [IssueTypeName], [AssigneeDisplayName], [ReporterDisplayName],
       [Created], [Updated], [ResolutionDate], [ResolutionName],
       [Environment], [Labels], [ComponentsAggregate], [FixVersionsAggregate],
       [TimeSpent], [ItemURL]
FROM [Issues]
WHERE [Key] IN {self._in_clause(keys)}
"""
        return self._execute_jira(sql.strip())

    def get_jira_comments(self, keys: list[str]) -> list[dict]:
        sql = f"""
SELECT [IssueKey], [AuthorDisplayName], [Created], [Body]
FROM [Comments]
WHERE [IssueKey] IN {self._in_clause(keys)}
ORDER BY [IssueKey], [Created] ASC
"""
        return self._execute_jira(sql.strip())

    def get_sync_trials(self, opp_id: str) -> list[dict]:
        sql = f"""
SELECT t.[Serial__c], t.[Product__c], t.[DataSource__c], t.[Destination__c],
       t.[JobType__c], t.[TrialDate__c], t.[LicExpiration__c], t.[LastQueryDate__c],
       t.[TotalSuccesses__c], t.[TotalFailures__c], t.[TotalRecordCount__c],
       t.[ConnectorCount__c], t.[Connectors__c], t.[IsExpired__c], t.[Platform__c]
FROM [Trial__c] t
INNER JOIN [Contact] c ON t.[Contact__c] = c.[Id]
INNER JOIN [Opportunity] o ON o.[AccountId] = c.[AccountId]
WHERE o.[Id] = '{opp_id}'
  AND t.[Serial__c] IS NOT NULL
  AND t.[Product__c] <> 'cloud'
ORDER BY t.[TrialDate__c] DESC
"""
        return self._execute_sf(sql.strip())

    def get_cloud_trials(self, opp_id: str) -> list[dict]:
        sql = f"""
SELECT t.[Serial__c], t.[Cloud_AccountId__c], t.[DataSource__c], t.[Destination__c],
       t.[TrialDate__c], t.[LicExpiration__c], t.[LastQueryDate__c],
       t.[TotalSuccesses__c], t.[TotalFailures__c], t.[TotalRecordCount__c],
       t.[ConnectorCount__c], t.[Connectors__c], t.[IsExpired__c]
FROM [Trial__c] t
INNER JOIN [Contact] c ON t.[Contact__c] = c.[Id]
INNER JOIN [Opportunity] o ON o.[AccountId] = c.[AccountId]
WHERE o.[Id] = '{opp_id}'
  AND t.[Product__c] = 'cloud'
ORDER BY t.[TrialDate__c] DESC
"""
        return self._execute_sf(sql.strip())

    # ------------------------------------------------------------------
    # Orchestrator
    # ------------------------------------------------------------------

    def collect_all(self, opp: dict) -> dict:
        opp_id = opp["Id"]
        max_cases = self._config.get("max_cases_per_opp", 10)

        result = {
            "opp": opp,
            "products": [],
            "cases": [],
            "total_cases_found": 0,
            "emails": [],
            "comments": [],
            "jira_issues": [],
            "jira_comments": [],
            "sync_trials": [],
            "cloud_trials": [],
            "errors": [],
        }

        try:
            result["products"] = self.get_opp_products(opp_id)
        except Exception as e:
            result["errors"].append(f"products: {e}")

        try:
            all_cases = self.get_opp_cases(opp_id)
            result["total_cases_found"] = len(all_cases)
            result["cases"] = all_cases[:max_cases]
        except Exception as e:
            result["errors"].append(f"cases: {e}")

        case_ids = [c["CaseId"] for c in result["cases"] if c.get("CaseId")]

        if case_ids:
            try:
                result["emails"] = self.get_case_emails(case_ids)
            except Exception as e:
                result["errors"].append(f"emails: {e}")

            try:
                result["comments"] = self.get_case_comments(case_ids)
            except Exception as e:
                result["errors"].append(f"case comments: {e}")

        jira_keys = self._extract_jira_keys(result["cases"])

        if jira_keys:
            try:
                result["jira_issues"] = self.get_jira_issues(jira_keys)
            except Exception as e:
                result["errors"].append(f"jira issues: {e}")

            try:
                result["jira_comments"] = self.get_jira_comments(jira_keys)
            except Exception as e:
                result["errors"].append(f"jira comments: {e}")

        try:
            result["sync_trials"] = self.get_sync_trials(opp_id)
        except Exception as e:
            result["errors"].append(f"sync trials: {e}")

        try:
            result["cloud_trials"] = self.get_cloud_trials(opp_id)
        except Exception as e:
            result["errors"].append(f"cloud trials: {e}")

        return result
