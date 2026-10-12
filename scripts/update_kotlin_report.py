#!/usr/bin/env python3
"""
Automatically scans CashLedger for Java and Kotlin files,
calculates migration progress, and updates KOTLIN_MIGRATION_REPORT.md.
"""

import os
import sys
import subprocess

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APP_SRC = os.path.join(REPO_ROOT, "app", "src")
OUTPUT_FILE = os.path.join(REPO_ROOT, "KOTLIN_MIGRATION_REPORT.md")

# Default repo info if not provided in environment
GITHUB_REPO = os.environ.get("GITHUB_REPOSITORY", "solomonrajan/cashledger")
GITHUB_BRANCH = os.environ.get("GITHUB_REF_NAME", "javatokotlin")
GITHUB_BLOB_BASE = f"https://github.com/{GITHUB_REPO}/blob/{GITHUB_BRANCH}"


def get_recent_commits(limit=8):
    commits = []
    try:
        cmd = [
            "git", "log", f"-n{limit}",
            "--pretty=format:HASH:%h%nSUBJECT:%s%nDATE:%ad%nAUTHOR:%an%nBODY:%b%n---END---",
            "--date=short"
        ]
        out = subprocess.check_output(cmd, cwd=REPO_ROOT, text=True, encoding="utf-8")
        blocks = out.split("---END---")
        for block in blocks:
            lines = [l for l in block.strip().split("\n") if l.strip()]
            if not lines:
                continue
            item = {"hash": "", "subject": "", "date": "", "author": "", "body": ""}
            body_lines = []
            is_body = False
            for line in block.strip().split("\n"):
                if line.startswith("HASH:"):
                    item["hash"] = line[5:].strip()
                elif line.startswith("SUBJECT:"):
                    item["subject"] = line[8:].strip()
                elif line.startswith("DATE:"):
                    item["date"] = line[5:].strip()
                elif line.startswith("AUTHOR:"):
                    item["author"] = line[7:].strip()
                elif line.startswith("BODY:"):
                    is_body = True
                    body_lines.append(line[5:])
                elif is_body:
                    body_lines.append(line)
            item["body"] = "\n".join(body_lines).strip()
            if item["hash"]:
                commits.append(item)
    except Exception as e:
        print(f"Warning: could not fetch git commits: {e}", file=sys.stderr)
    return commits


def scan_files():
    java_files = []
    kotlin_files = []

    for root, _, files in os.walk(APP_SRC):
        for file in files:
            rel_path = os.path.relpath(os.path.join(root, file), REPO_ROOT).replace("\\", "/")
            if file.endswith(".java"):
                java_files.append(rel_path)
            elif file.endswith(".kt"):
                kotlin_files.append(rel_path)

    return java_files, kotlin_files


def make_progress_bar(percent, total_blocks=20):
    filled = int(round((percent / 100.0) * total_blocks))
    filled = min(total_blocks, max(0, filled))
    return "█" * filled + "░" * (total_blocks - filled)


def make_heatmap(percent, total_blocks=10):
    # Heatmap blocks: green for done, yellow for in-progress/scouting, red for remaining
    green_count = int((percent / 100.0) * total_blocks)
    remaining = total_blocks - green_count
    if green_count == total_blocks:
        return "🟩" * total_blocks
    if green_count == 0 and percent > 0:
        return "🟨" + "🟥" * (total_blocks - 1)
    if remaining > 0 and percent > 0:
        yellow_count = 1 if remaining > 0 else 0
        red_count = remaining - yellow_count
        return "🟩" * green_count + "🟨" * yellow_count + "🟥" * red_count
    return "🟥" * total_blocks


def generate_report():
    java_files, kotlin_files = scan_files()

    total_files = len(java_files) + len(kotlin_files)
    total_percent = (len(kotlin_files) / total_files * 100.0) if total_files else 0.0

    # Categorize
    main_java = [f for f in java_files if "app/src/main/" in f]
    main_kt = [f for f in kotlin_files if "app/src/main/" in f]
    test_java = [f for f in java_files if "app/src/test/" in f or "app/src/androidTest/" in f]
    test_kt = [f for f in kotlin_files if "app/src/test/" in f or "app/src/androidTest/" in f]

    def count_pkg(pkg_pattern):
        j = sum(1 for f in main_java if pkg_pattern in f)
        k = sum(1 for f in main_kt if pkg_pattern in f)
        tot = j + k
        pct = (k / tot * 100.0) if tot else 0.0
        return k, j, pct

    act_kt, act_j, act_pct = count_pkg("/ui/activity/")
    frag_kt, frag_j, frag_pct = count_pkg("/ui/fragment/")
    updater_kt, updater_j, updater_pct = count_pkg("/updater/")
    model_kt, model_j, model_pct = count_pkg("/model/")
    db_kt, db_j, db_pct = count_pkg("/storage/")
    utils_kt, utils_j, utils_pct = count_pkg("/utils/")
    svc_kt, svc_j, svc_pct = (
        sum(1 for f in main_kt if "/service/" in f or "/background/" in f),
        sum(1 for f in main_java if "/service/" in f or "/background/" in f),
        0.0
    )
    if (svc_kt + svc_j) > 0:
        svc_pct = (svc_kt / (svc_kt + svc_j)) * 100.0

    test_total = len(test_java) + len(test_kt)
    test_pct = (len(test_kt) / test_total * 100.0) if test_total else 0.0

    # Segregate Kotlin files for Hall of Fame
    activities_kt = sorted([f for f in main_kt if "Activity.kt" in f or "/ui/activity/" in f and f.endswith("Activity.kt")])
    screens_vm_kt = sorted([f for f in main_kt if f not in activities_kt and not f.endswith("Release.kt") and "/model/" not in f])
    models_kt = sorted([f for f in main_kt if "/model/" in f or f.endswith("Release.kt")])

    # Top Wanted Java files in activities
    java_act_names = sorted([os.path.basename(f) for f in main_java if "/ui/activity/" in f and f.endswith(".java")])

    report_lines = []
    report_lines.append("# 🚀 The Great Java-to-Kotlin Migration Chronicles ☕➡️🟣\n")
    report_lines.append('> *"There are two types of developers: those who migrate to Kotlin, and those who spend their weekends debugging `NullPointerException`."* — *Ancient Android Proverb*\n')
    report_lines.append("---\n")
    report_lines.append("## 📊 1. The High-Level Battle Ground\n")
    report_lines.append("We ran the scouters over the codebase, and here is the pulse check of our crusade against semicolon tyranny:\n")
    report_lines.append("```")
    report_lines.append(f"Total Files Analyzed : {total_files}")
    report_lines.append(f"Java Holdouts (☕)    : {len(java_files)} files ({100.0 - total_percent:.1f}%)")
    report_lines.append(f"Kotlin Chads  (🟣)   :  {len(kotlin_files)} files  ({total_percent:.1f}%)")
    report_lines.append("```\n")

    report_lines.append("### 🥧 The Codebase Pie Chart\n")
    report_lines.append("```mermaid")
    report_lines.append("pie title Java vs Kotlin Territory Breakdown")
    report_lines.append(f'    "Java (Boilerplate Kingdom)" : {len(java_files)}')
    report_lines.append(f'    "Kotlin (Concise & Null-Safe)" : {len(kotlin_files)}')
    report_lines.append("```\n")
    report_lines.append("---\n")

    report_lines.append("## 🗺️ 2. Domain-by-Domain Progress: *The Conquest of CashLedger*\n")
    report_lines.append("```mermaid")
    report_lines.append("%%{init: {'theme': 'dark', 'themeVariables': { 'fontFamily': 'monospace' }}}%%")
    report_lines.append("flowchart TD")
    report_lines.append("    classDef liberated fill:#2e7d32,stroke:#a5d6a7,stroke-width:2px,color:#ffffff;")
    report_lines.append("    classDef siege fill:#f57f17,stroke:#fff59d,stroke-width:2px,color:#ffffff;")
    report_lines.append("    classDef occupied fill:#b71c1c,stroke:#ef9a9a,stroke-width:2px,color:#ffffff;")
    report_lines.append("    classDef portal fill:#4a148c,stroke:#ce93d8,stroke-width:2px,color:#ffffff;\n")
    report_lines.append("    Start((🟣 Kotlin Vanguard)):::portal --> Castle")
    report_lines.append("    Start --> Valley")
    report_lines.append("    Start --> Dungeon")
    report_lines.append("    Start --> Arena\n")
    report_lines.append('    subgraph Castle["🏰 Realm I: The Citadel of Activities"]')
    report_lines.append("        direction TB")
    report_lines.append(f'        A_Won["🏆 {act_kt} Bastions Liberated<br/><i>About, Calc, Search, etc.</i>"]:::liberated')
    report_lines.append(f'        A_Siege["⚔️ {act_j} Bastions Under Siege<br/><i>Boss: MainActivity.java</i>"]:::siege')
    report_lines.append("        A_Won -.-> A_Siege")
    report_lines.append("    end\n")
    report_lines.append('    subgraph Valley["🧩 Realm II: The Fragment Outposts"]')
    report_lines.append("        direction TB")
    report_lines.append(f'        F_Scout["🏹 {frag_kt} Forward Camps Established<br/><i>TransactionList, AboutFragment</i>"]:::siege')
    report_lines.append(f'        F_Wild["🌲 {frag_j} Forts in the Fog of War<br/><i>Java Monoliths Looming</i>"]:::occupied')
    report_lines.append("        F_Scout -.-> F_Wild")
    report_lines.append("    end\n")
    report_lines.append('    subgraph Dungeon["⚙️ Realm III: The Catacombs of Data"]')
    report_lines.append("        direction TB")
    report_lines.append(f'        D_Curse["💀 100% Java Necromancy<br/><i>{db_j} DAOs & DB Helpers Guarding SQLite</i>"]:::occupied')
    report_lines.append("    end\n")
    report_lines.append('    subgraph Arena["🧪 Realm IV: The Proving Grounds"]')
    report_lines.append("        direction TB")
    report_lines.append(f'        T_Trial["🛡️ {test_total} Untamed Test Suites<br/><i>Awaiting Kotest & MockK Reinforcements</i>"]:::occupied')
    report_lines.append("    end")
    report_lines.append("```\n")

    report_lines.append("### ⚔️ Realm Status Log & Boss Encounters\n")
    report_lines.append("#### 🏰 Realm I: The Citadel of Activities (UI Frontline)")
    report_lines.append(f"* **Liberation Progress**: `[{make_progress_bar(act_pct)}]` **{act_pct:.1f}%**")
    report_lines.append(f"* **Status**: ⚔️ **HEATED SIEGE**")
    report_lines.append(f"* **Garrison**: `{act_kt}` Kotlin Knights vs `{act_j}` Java Defenders")
    report_lines.append(f"* **Current Boss Fight**: `MainActivity.java` *(HP: 950 Lines | Special Attack: Intent Spam)*")
    report_lines.append(f"* **Loot Upon Victory**: `+50 Null Safety`, `-420 Semicolons`\n")

    report_lines.append("#### 🧩 Realm II: The Fragment Outposts (Component Layer)")
    report_lines.append(f"* **Liberation Progress**: `[{make_progress_bar(frag_pct)}]` **{frag_pct:.1f}%**")
    report_lines.append(f"* **Status**: 🏹 **SCOUTING PARTY ADVANCING**")
    report_lines.append(f"* **Garrison**: `{frag_kt}` Kotlin Scouts")
    report_lines.append(f"* **Objective**: Infiltrate primary balance & summary tabs without triggering `IllegalStateException: Fragment not attached`\n")

    report_lines.append("#### 💀 Realm III: The Catacombs of Data & DAOs (Storage Depths)")
    report_lines.append(f"* **Liberation Progress**: `[{make_progress_bar(db_pct)}]` **{db_pct:.1f}%**")
    report_lines.append(f"* **Status**: 🔒 **ANCIENT JAVA CITADEL**")
    report_lines.append(f"* **Threat Level**: 🌋 *Extreme*")
    report_lines.append(f"* **Dungeon Bosses**: `WalletDAO.java`, `TransactionDatabase.java`, `MWBackupImporter.java`")
    report_lines.append(f"* **Debuff**: Every query incurs +5 verbose cursors and manual index mappings\n")

    report_lines.append("#### 🧪 Realm IV: The Proving Grounds (Test Suite)")
    report_lines.append(f"* **Liberation Progress**: `[{make_progress_bar(test_pct)}]` **{test_pct:.1f}%**")
    report_lines.append(f"* **Status**: ⏳ **PEACEFUL STALEMATE**")
    report_lines.append(f"* **Roster**: `{len(test_java)}` Unit Tests + `{len(test_kt)}` Android Instrumented Tests")
    report_lines.append(f"* **Strategy**: Deploy modern Kotlin DSL assertions & coroutine test scopes once the battle moves deeper\n")
    report_lines.append("---\n")

    report_lines.append("## 🔥 3. Kotlin Migration Heat Map\n")
    report_lines.append("Visual progress status across different modules:\n")
    report_lines.append("| Module / Package | Kotlin | Java | Progress | Risk Level | Status Heatmap |")
    report_lines.append("| :--- | :---: | :---: | :---: | :---: | :--- |")
    report_lines.append(f"| **`ui/activity`** | {act_kt} | {act_j} | **{act_pct:.1f}%** | 🟡 Medium | `{make_heatmap(act_pct)}` *Civil War in Progress* |")
    report_lines.append(f"| **`ui/fragment`** | {frag_kt} | {frag_j} | **{frag_pct:.1f}%** | 🟠 High | `{make_heatmap(frag_pct)}` *Reconnaissance Mission* |")
    report_lines.append(f"| **`updater`** | {updater_kt} | {updater_j} | **{updater_pct:.1f}%** | 🟢 Safe | `{make_heatmap(updater_pct)}` *Liberated Territory* 🏆 |")
    report_lines.append(f"| **`model`** | {model_kt} | {model_j} | **{model_pct:.1f}%** | 🔴 Extreme | `{make_heatmap(model_pct)}` *Getters & Setters Everywhere* |")
    report_lines.append(f"| **`storage/database`**| {db_kt} | {db_j} | **{db_pct:.1f}%** | 💀 Critical | `{make_heatmap(db_pct)}` *The Semicolon Citadel* |")
    report_lines.append(f"| **`utils`** | {utils_kt} | {utils_j} | **{utils_pct:.1f}%** | 🔴 High | `{make_heatmap(utils_pct)}` *Static Class Graveyard* |")
    report_lines.append(f"| **`service & background`**| {svc_kt} | {svc_j} | **{svc_pct:.1f}%** | 🟠 High | `{make_heatmap(svc_pct)}` *Background Boilerplate* |")
    report_lines.append(f"| **`tests (src/test)`** | {len(test_kt)} | {len(test_java)} | **{test_pct:.1f}%** | 🟡 Medium | `{make_heatmap(test_pct)}` *Legacy Assertions* |\n")
    report_lines.append("---\n")

    report_lines.append("## 🏆 4. Hall of Fame (Converted Kotlin Files)\n")
    report_lines.append(f"These brave `{len(kotlin_files)}` files threw away the Java syntax baggage:\n")

    report_lines.append("### 👑 Activities")
    for f in activities_kt:
        name = os.path.basename(f)
        report_lines.append(f"* [{name}]({GITHUB_BLOB_BASE}/{f})")

    report_lines.append("\n### 🎨 Screens, ViewModels & Helpers")
    for f in screens_vm_kt:
        name = os.path.basename(f)
        report_lines.append(f"* [{name}]({GITHUB_BLOB_BASE}/{f})")

    if models_kt:
        report_lines.append("\n### 📦 Models & Domain")
        for f in models_kt:
            name = os.path.basename(f)
            report_lines.append(f"* [{name}]({GITHUB_BLOB_BASE}/{f})")

    report_lines.append("\n---\n")

    report_lines.append("## 🎯 5. Top Bounty Targets (Most Wanted Java Files)\n")
    report_lines.append("```")
    report_lines.append("================================================================================")
    report_lines.append(" WANTED FOR UNLAWFUL VERBOSITY & WANTON ALLOCATION OF GETTERS/SETTERS")
    report_lines.append("================================================================================")
    for idx, name in enumerate(java_act_names[:8], start=1):
        report_lines.append(f" [{idx}] {name:<35} - Awaiting liberation ⚔️")
    report_lines.append("================================================================================")
    report_lines.append(" REWARD: Infinite peace of mind & 0.00 NullPointerExceptions!")
    report_lines.append("================================================================================")
    report_lines.append("```\n")
    report_lines.append("---\n")

    report_lines.append("## 🎭 6. Funny Codes: Java PTSD vs Kotlin Zen\n")
    report_lines.append("### Case 1: Checking a nested wallet balance\n")
    report_lines.append("#### ☕ Java's Rite of Passage (Defensive Programming Hell)")
    report_lines.append("```java")
    report_lines.append('// Java: "Please, Mr. Compiler, don\'t crash the entire app today"')
    report_lines.append("public String getBalanceFormatted(User user) {")
    report_lines.append("    if (user != null) {")
    report_lines.append("        Wallet wallet = user.getDefaultWallet();")
    report_lines.append("        if (wallet != null) {")
    report_lines.append("            Balance balance = wallet.getBalance();")
    report_lines.append("            if (balance != null) {")
    report_lines.append("                Currency currency = balance.getCurrency();")
    report_lines.append("                if (currency != null) {")
    report_lines.append('                    return currency.getSymbol() + " " + balance.getAmount();')
    report_lines.append("                }")
    report_lines.append("            }")
    report_lines.append("        }")
    report_lines.append("    }")
    report_lines.append('    return "$ 0.00"; // 6 nested ifs just to buy a coffee')
    report_lines.append("}")
    report_lines.append("```\n")

    report_lines.append("#### 🟣 Kotlin's Chad One-Liner")
    report_lines.append("```kotlin")
    report_lines.append('// Kotlin: "Look at what they need to mimic a fraction of our power."')
    report_lines.append("fun getBalanceFormatted(user: User?): String = ")
    report_lines.append('    "${user?.defaultWallet?.balance?.currency?.symbol ?: "$"} ${user?.defaultWallet?.balance?.amount ?: "0.00"}"')
    report_lines.append("```\n")
    report_lines.append("---\n")

    report_lines.append("### Case 2: Model POJO Declaration\n")
    report_lines.append("#### ☕ Java: 120 lines of ceremonial boilerplate")
    report_lines.append("```java")
    report_lines.append("public class TransactionTag {")
    report_lines.append("    private long id;")
    report_lines.append("    private String name;")
    report_lines.append("    private int colorHex;\n")
    report_lines.append("    public TransactionTag(long id, String name, int colorHex) {")
    report_lines.append("        this.id = id;")
    report_lines.append("        this.name = name;")
    report_lines.append("        this.colorHex = colorHex;")
    report_lines.append("    }\n")
    report_lines.append("    public long getId() { return id; }")
    report_lines.append("    public void setId(long id) { this.id = id; }")
    report_lines.append("    public String getName() { return name; }")
    report_lines.append("    public void setName(String name) { this.name = name; }")
    report_lines.append("    public int getColorHex() { return colorHex; }")
    report_lines.append("    public void setColorHex(int colorHex) { this.colorHex = colorHex; }\n")
    report_lines.append("    @Override")
    report_lines.append("    public boolean equals(Object o) { /* another 20 lines generated by IDE */ }")
    report_lines.append("    @Override")
    report_lines.append("    public int hashCode() { /* another 10 lines */ }")
    report_lines.append("    @Override")
    report_lines.append("    public String toString() { /* another 15 lines */ }")
    report_lines.append("}")
    report_lines.append("```\n")

    report_lines.append("#### 🟣 Kotlin: Blink and you miss it")
    report_lines.append("```kotlin")
    report_lines.append("data class TransactionTag(val id: Long, val name: String, val colorHex: Int)")
    report_lines.append("```\n")
    report_lines.append("---\n")

    report_lines.append("### Case 3: Casting & Instance Checks\n")
    report_lines.append("#### ☕ Java (Pre-16)")
    report_lines.append("```java")
    report_lines.append("if (event instanceof TransactionEvent) {")
    report_lines.append("    TransactionEvent transactionEvent = (TransactionEvent) event;")
    report_lines.append("    transactionEvent.triggerSoundEffects();")
    report_lines.append("}")
    report_lines.append("```\n")

    report_lines.append("#### 🟣 Kotlin Smart Casts")
    report_lines.append("```kotlin")
    report_lines.append("if (event is TransactionEvent) event.triggerSoundEffects() // The compiler isn't blind!")
    report_lines.append("```\n")
    report_lines.append("---\n")

    report_lines.append("## 🔮 7. Strategic Battle Plan\n")
    report_lines.append("```mermaid")
    report_lines.append("gantt")
    report_lines.append("    title Kotlin Conquest Timeline")
    report_lines.append("    dateFormat  YYYY-MM")
    report_lines.append("    section UI Layer")
    report_lines.append(f"    Finish Activities ({act_j} remaining) :active, act1, 2026-10, 2026-11")
    report_lines.append("    Convert Primary Fragments        :crit, act2, 2026-11, 2026-12")
    report_lines.append("    section Data & Core")
    report_lines.append("    Convert Models to Data Classes   :act3, 2026-12, 2027-01")
    report_lines.append("    Database DAOs & Repository Room  :act4, 2027-01, 2027-02")
    report_lines.append("    section Utilities & Tests")
    report_lines.append("    Utils to Kotlin Extensions       :act5, 2027-02, 2027-03")
    report_lines.append(f"    Migrate {test_total} Test Suites           :act6, 2027-03, 2027-04")
    report_lines.append("```\n")
    report_lines.append("> **Pro-tip:** Every converted Java file eliminates an average of 42 semicolons and reduces line count by 35%. Long live `val` and `?.`! 🚀\n")
    report_lines.append("---\n")

    # Section 8: Recent Commits with Message and Description
    report_lines.append("## 📜 8. Chronicles of the Crusade (Recent Commits & Dispatches)\n")
    report_lines.append("> *A live dispatch of recent engineering battles, architecture refactors, and UI modernization:*\n")

    commits = get_recent_commits(limit=8)
    if commits:
        for c in commits:
            commit_url = f"https://github.com/{GITHUB_REPO}/commit/{c['hash']}"
            report_lines.append("<details open>")
            report_lines.append(f"<summary><b><code>{c['hash']}</code></b> — <b>{c['subject']}</b> <i>({c['date']} by {c['author']})</i></summary>\n")
            report_lines.append(f"* **Commit Link**: [`{c['hash']}`]({commit_url})")
            report_lines.append(f"* **Author**: {c['author']} • **Date**: {c['date']}")
            if c['body'].strip():
                report_lines.append("* **Description / Scope**:\n")
                # Indent body with blockquote
                body_lines = [f"  > {line}" if line.strip() else "  >" for line in c['body'].strip().split("\n")]
                report_lines.append("\n".join(body_lines))
            else:
                report_lines.append("* **Description**: *(Single-line commit)*")
            report_lines.append("\n</details>\n")
    else:
        report_lines.append("*No recent commit records available.*\n")

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(report_lines))

    print(f"Successfully updated {OUTPUT_FILE}")


if __name__ == "__main__":
    generate_report()
