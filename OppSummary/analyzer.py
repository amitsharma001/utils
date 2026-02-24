#!/usr/bin/env python3
"""
Analyzer — builds the Claude prompt from collected data and calls the Claude API.
"""

import os
from tabulate import tabulate


class Analyzer:
    def __init__(self, config: dict):
        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError(
                "ANTHROPIC_API_KEY is not set. "
                "Add 'export ANTHROPIC_API_KEY=<your-key>' to ~/.zshrc or ~/.bashrc and restart your shell."
            )
        import anthropic
        self._client = anthropic.Anthropic(api_key=api_key)
        self._model = config.get("claude_model", "claude-sonnet-4-6")
        self._config = config

    # ------------------------------------------------------------------
    # Truncation helper
    # ------------------------------------------------------------------

    @staticmethod
    def _trunc(text, max_chars: int) -> str:
        if text is None:
            return ""
        text = str(text)
        if len(text) <= max_chars:
            return text
        cut = len(text) - max_chars
        return text[:max_chars] + f"... [truncated {cut} chars]"

    # ------------------------------------------------------------------
    # Instructions template loader
    # ------------------------------------------------------------------

    def _load_instructions_template(self) -> str:
        path = os.path.join(os.path.dirname(__file__), "analysis_instructions.md")
        with open(path, "r") as f:
            return f.read()

    # ------------------------------------------------------------------
    # Prompt builder
    # ------------------------------------------------------------------

    def build_prompt(self, data: dict) -> str:
        cfg = self._config
        opp = data["opp"]
        opp_type = data.get("opp_type", "Closed Lost")
        lines = []

        template = self._load_instructions_template()
        marker = "\n=== ANALYSIS INSTRUCTIONS ==="
        intro_block, _, instructions_block = template.partition(marker)
        intro_text = intro_block.strip().replace("{OPP_TYPE}", opp_type)
        instructions_text = (marker + instructions_block).replace("{OPP_TYPE}", opp_type)

        lines.append(intro_text + "\n")

        # --- Opportunity ---
        lines.append("=== OPPORTUNITY ===")
        lines.append(f"Name      : {opp.get('Name', '')}")
        lines.append(f"Close Date: {opp.get('CloseDate', '')}")
        amount = opp.get("Amount")
        lines.append(f"Amount    : {f'${amount:,.2f}' if amount else 'N/A'}")
        lines.append(f"Description:\n{self._trunc(opp.get('Description'), 2000)}")
        lines.append("")

        # --- Products ---
        lines.append("=== PRODUCTS ===")
        products = data.get("products", [])
        if products:
            prod_rows = [
                [
                    p.get("Name", ""),
                    p.get("Product_Group__c", ""),
                    p.get("Edition__c", ""),
                    p.get("Application__c", ""),
                    p.get("Quantity", ""),
                    p.get("ACV__c", ""),
                    p.get("NewBusiness__c", ""),
                ]
                for p in products
            ]
            lines.append(tabulate(prod_rows, headers=["Name", "Group", "Edition", "Application", "Qty", "ACV", "NewBiz"], tablefmt="simple"))
        else:
            lines.append("No product line items found.")
        lines.append("")

        # --- Cases ---
        total_found = data.get("total_cases_found", 0)
        cases = data.get("cases", [])
        max_ep = cfg.get("max_emails_per_case", 5)
        max_cp = cfg.get("max_comments_per_case", 5)

        lines.append(f"=== SUPPORT CASES ({len(cases)} shown of {total_found} found via account-level join) ===")

        # Build per-case email/comment maps
        emails_by_case: dict[str, list] = {}
        for em in data.get("emails", []):
            parent = em.get("ParentId") or em.get("parentid") or ""
            # EmailMessage.ParentId is the case Id
            emails_by_case.setdefault(parent, []).append(em)

        comments_by_case: dict[str, list] = {}
        for cc in data.get("comments", []):
            parent = cc.get("ParentId") or cc.get("parentid") or ""
            comments_by_case.setdefault(parent, []).append(cc)

        if cases:
            for case in cases:
                cid = case.get("CaseId", "")
                lines.append(f"\n  Case {case.get('CaseNumber', '')} — {case.get('Subject', '')} [{case.get('Status', '')}]")
                jira_ref = case.get("JIRA_Reference__c")
                if jira_ref:
                    lines.append(f"    Jira: {jira_ref}")

                case_emails = emails_by_case.get(cid, [])[:max_ep]
                if case_emails:
                    lines.append("    Emails:")
                    for em in case_emails:
                        direction = "IN" if em.get("Incoming") else "OUT"
                        md = em.get("MessageDate", "")
                        md_str = md.date().isoformat() if hasattr(md, "date") else str(md)[:10]
                        lines.append(
                            f"      [{md_str}] {direction} from {em.get('FromName', '')} | "
                            f"{em.get('Subject', '')} | {self._trunc(em.get('TextBody'), 1000)}"
                        )

                case_comments = comments_by_case.get(cid, [])[:max_cp]
                if case_comments:
                    lines.append("    Comments:")
                    for cc in case_comments:
                        vis = "Public" if cc.get("IsPublished") else "Internal"
                        lines.append(
                            f"      [{cc.get('CreatedDate', '')}] {vis} | {self._trunc(cc.get('CommentBody'), 500)}"
                        )
        else:
            lines.append("No cases found.")
        lines.append("")

        # --- Jira ---
        lines.append("=== JIRA ISSUES ===")
        jira_issues = data.get("jira_issues", [])
        max_jc = cfg.get("max_jira_comments", 20)

        jira_comments_by_key: dict[str, list] = {}
        for jc in data.get("jira_comments", []):
            key = jc.get("IssueKey") or jc.get("issuekey") or ""
            jira_comments_by_key.setdefault(key, []).append(jc)

        if jira_issues:
            for issue in jira_issues:
                key = issue.get("Key", "")
                lines.append(
                    f"\n  {key} [{issue.get('IssueTypeName', '')}] {issue.get('StatusName', '')} / {issue.get('PriorityName', '')} — {issue.get('Summary', '')}"
                )
                resolution = issue.get("ResolutionName")
                if resolution:
                    lines.append(f"    Resolution: {resolution} on {issue.get('ResolutionDate', '')}")
                desc = self._trunc(issue.get("Description"), 1500)
                if desc:
                    lines.append(f"    Description: {desc}")
                components = issue.get("ComponentsAggregate")
                if components:
                    lines.append(f"    Components: {components}")
                fix_versions = issue.get("FixVersionsAggregate")
                if fix_versions:
                    lines.append(f"    Fix Versions: {fix_versions}")

                issue_comments = jira_comments_by_key.get(key, [])
                all_jira_comments_count = sum(len(v) for v in jira_comments_by_key.values())
                # Apply global max across all issues
                shown_jira = []
                total_so_far = 0
                for iss in jira_issues:
                    k = iss.get("Key", "")
                    for jc in jira_comments_by_key.get(k, []):
                        if total_so_far >= max_jc:
                            break
                        shown_jira.append((k, jc))
                        total_so_far += 1

                issue_shown = [jc for k, jc in shown_jira if k == key]
                if issue_shown:
                    lines.append("    Comments:")
                    for jc in issue_shown:
                        lines.append(
                            f"      [{jc.get('Created', '')}] {jc.get('AuthorDisplayName', '')} | "
                            f"{self._trunc(jc.get('Body'), 800)}"
                        )
        else:
            lines.append("No Jira tickets found for this opportunity.")
        lines.append("")

        # --- Sync Trials ---
        lines.append("=== TRIAL TELEMETRY — SYNC ===")
        sync_trials = data.get("sync_trials", [])[: cfg.get("max_trial_rows", 10)]
        if sync_trials:
            sync_rows = [
                [
                    t.get("Serial__c", ""),
                    t.get("Product__c", ""),
                    t.get("DataSource__c", ""),
                    t.get("Destination__c", ""),
                    t.get("TrialDate__c", ""),
                    t.get("LastQueryDate__c", ""),
                    t.get("TotalSuccesses__c", ""),
                    t.get("TotalFailures__c", ""),
                    t.get("TotalRecordCount__c", ""),
                    t.get("IsExpired__c", ""),
                ]
                for t in sync_trials
            ]
            lines.append(
                tabulate(
                    sync_rows,
                    headers=["Serial", "Product", "DataSource", "Destination", "TrialDate", "LastQuery", "Successes", "Failures", "Records", "Expired"],
                    tablefmt="simple",
                )
            )
        else:
            lines.append("No Sync trial data.")
        lines.append("")

        # --- Cloud Trials ---
        lines.append("=== TRIAL TELEMETRY — CONNECT CLOUD ===")
        cloud_trials = data.get("cloud_trials", [])[: cfg.get("max_trial_rows", 10)]
        if cloud_trials:
            cloud_rows = [
                [
                    t.get("Serial__c", ""),
                    t.get("DataSource__c", ""),
                    t.get("Destination__c", ""),
                    t.get("TrialDate__c", ""),
                    t.get("LastQueryDate__c", ""),
                    t.get("TotalSuccesses__c", ""),
                    t.get("TotalFailures__c", ""),
                    t.get("TotalRecordCount__c", ""),
                    t.get("IsExpired__c", ""),
                ]
                for t in cloud_trials
            ]
            lines.append(
                tabulate(
                    cloud_rows,
                    headers=["Serial", "DataSource", "Destination", "TrialDate", "LastQuery", "Successes", "Failures", "Records", "Expired"],
                    tablefmt="simple",
                )
            )
        else:
            lines.append("No Connect Cloud trial data.")
        lines.append("")

        # --- Gong Calls ---
        lines.append("=== GONG CALLS ===")
        gong_calls = data.get("gong_calls", [])
        max_gong = cfg.get("max_gong_calls", 5)
        max_transcript = cfg.get("max_transcript_chars", 3000)

        if gong_calls:
            for call in gong_calls[:max_gong]:
                call_date = call.get("Gong__Call_Start__c", "")
                date_str = call_date.date().isoformat() if hasattr(call_date, "date") else str(call_date)[:10]
                duration = call.get("Gong__Call_Duration__c")
                primary = " [Primary Opportunity]" if call.get("IsPrimaryOpportunity") else ""
                lines.append(f"\n  Call: {call.get('Name', '')} — {date_str}{primary}")
                if duration:
                    lines.append(f"    Duration: {duration} min")
                participants = call.get("Gong__Participants_Emails__c")
                if participants:
                    lines.append(f"    Participants: {participants}")
                url = call.get("Gong__View_call__c")
                if url:
                    lines.append(f"    Gong URL: {url}")
                brief = self._trunc(call.get("Gong__Call_Brief__c"), 1000)
                if brief:
                    lines.append(f"    Brief: {brief}")
                key_points = self._trunc(call.get("Gong__Call_Key_Points__c"), 1000)
                if key_points:
                    lines.append(f"    Key Points: {key_points}")
                next_steps = self._trunc(call.get("Gong__Call_Highlights_Next_Steps__c"), 800)
                if next_steps:
                    lines.append(f"    Next Steps: {next_steps}")
                transcript = self._trunc(call.get("Call_Transcript__c"), max_transcript)
                if transcript:
                    lines.append(f"    Transcript:\n{transcript}")
        else:
            lines.append("No Gong calls found for this opportunity.")
        lines.append("")

        # --- Analysis instructions (loaded from analysis_instructions.md) ---
        lines.append(instructions_text)

        return "\n".join(lines)

    # ------------------------------------------------------------------
    # API call
    # ------------------------------------------------------------------

    def analyze(self, data: dict) -> str:
        prompt = self.build_prompt(data)
        message = self._client.messages.create(
            model=self._model,
            max_tokens=4096,
            messages=[{"role": "user", "content": prompt}],
        )
        return message.content[0].text
