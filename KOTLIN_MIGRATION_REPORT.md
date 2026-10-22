# 🚀 The Great Java-to-Kotlin Migration Chronicles ☕➡️🟣

> *"There are two types of developers: those who migrate to Kotlin, and those who spend their weekends debugging `NullPointerException`."* — *Ancient Android Proverb*

---

## 📊 1. The High-Level Battle Ground

We ran the scouters over the codebase, and here is the pulse check of our crusade against semicolon tyranny:

```
Total Files Analyzed : 535
Java Holdouts (☕)    : 486 files (90.8%)
Kotlin Chads  (🟣)   :  49 files  (9.2%)
```

### 🥧 The Codebase Pie Chart

```mermaid
pie title Java vs Kotlin Territory Breakdown
    "Java (Boilerplate Kingdom)" : 486
    "Kotlin (Concise & Null-Safe)" : 49
```

---

## 🗺️ 2. Domain-by-Domain Progress: *The Conquest of CashLedger*

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'fontFamily': 'monospace' }}}%%
flowchart TD
    classDef liberated fill:#2e7d32,stroke:#a5d6a7,stroke-width:2px,color:#ffffff;
    classDef siege fill:#f57f17,stroke:#fff59d,stroke-width:2px,color:#ffffff;
    classDef occupied fill:#b71c1c,stroke:#ef9a9a,stroke-width:2px,color:#ffffff;
    classDef portal fill:#4a148c,stroke:#ce93d8,stroke-width:2px,color:#ffffff;

    Start((🟣 Kotlin Vanguard)):::portal --> Castle
    Start --> Valley
    Start --> Dungeon
    Start --> Arena

    subgraph Castle["🏰 Realm I: The Citadel of Activities"]
        direction TB
        A_Won["🏆 32 Bastions Liberated<br/><i>About, Calc, Search, etc.</i>"]:::liberated
        A_Siege["⚔️ 22 Bastions Under Siege<br/><i>Boss: MainActivity.java</i>"]:::siege
        A_Won -.-> A_Siege
    end

    subgraph Valley["🧩 Realm II: The Fragment Outposts"]
        direction TB
        F_Scout["🏹 4 Forward Camps Established<br/><i>TransactionList, AboutFragment</i>"]:::siege
        F_Wild["🌲 82 Forts in the Fog of War<br/><i>Java Monoliths Looming</i>"]:::occupied
        F_Scout -.-> F_Wild
    end

    subgraph Dungeon["⚙️ Realm III: The Catacombs of Data"]
        direction TB
        D_Curse["💀 100% Java Necromancy<br/><i>77 DAOs & DB Helpers Guarding SQLite</i>"]:::occupied
    end

    subgraph Arena["🧪 Realm IV: The Proving Grounds"]
        direction TB
        T_Trial["🛡️ 75 Untamed Test Suites<br/><i>Awaiting Kotest & MockK Reinforcements</i>"]:::occupied
    end
```

### ⚔️ Realm Status Log & Boss Encounters

#### 🏰 Realm I: The Citadel of Activities (UI Frontline)
* **Liberation Progress**: `[████████████░░░░░░░░]` **59.3%**
* **Status**: ⚔️ **HEATED SIEGE**
* **Garrison**: `32` Kotlin Knights vs `22` Java Defenders
* **Current Boss Fight**: `MainActivity.java` *(HP: 950 Lines | Special Attack: Intent Spam)*
* **Loot Upon Victory**: `+50 Null Safety`, `-420 Semicolons`

#### 🧩 Realm II: The Fragment Outposts (Component Layer)
* **Liberation Progress**: `[█░░░░░░░░░░░░░░░░░░░]` **4.7%**
* **Status**: 🏹 **SCOUTING PARTY ADVANCING**
* **Garrison**: `4` Kotlin Scouts
* **Objective**: Infiltrate primary balance & summary tabs without triggering `IllegalStateException: Fragment not attached`

#### 💀 Realm III: The Catacombs of Data & DAOs (Storage Depths)
* **Liberation Progress**: `[░░░░░░░░░░░░░░░░░░░░]` **0.0%**
* **Status**: 🔒 **ANCIENT JAVA CITADEL**
* **Threat Level**: 🌋 *Extreme*
* **Dungeon Bosses**: `WalletDAO.java`, `TransactionDatabase.java`, `MWBackupImporter.java`
* **Debuff**: Every query incurs +5 verbose cursors and manual index mappings

#### 🧪 Realm IV: The Proving Grounds (Test Suite)
* **Liberation Progress**: `[██░░░░░░░░░░░░░░░░░░]` **10.7%**
* **Status**: ⏳ **PEACEFUL STALEMATE**
* **Roster**: `67` Unit Tests + `8` Android Instrumented Tests
* **Strategy**: Deploy modern Kotlin DSL assertions & coroutine test scopes once the battle moves deeper

---

## 🔥 3. Kotlin Migration Heat Map

Visual progress status across different modules:

| Module / Package | Kotlin | Java | Progress | Risk Level | Status Heatmap |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **`ui/activity`** | 32 | 22 | **59.3%** | 🟡 Medium | `🟩🟩🟩🟩🟩🟨🟥🟥🟥🟥` *Civil War in Progress* |
| **`ui/fragment`** | 4 | 82 | **4.7%** | 🟠 High | `🟨🟥🟥🟥🟥🟥🟥🟥🟥🟥` *Reconnaissance Mission* |
| **`updater`** | 2 | 0 | **100.0%** | 🟢 Safe | `🟩🟩🟩🟩🟩🟩🟩🟩🟩🟩` *Liberated Territory* 🏆 |
| **`model`** | 1 | 60 | **1.6%** | 🔴 Extreme | `🟨🟥🟥🟥🟥🟥🟥🟥🟥🟥` *Getters & Setters Everywhere* |
| **`storage/database`**| 0 | 77 | **0.0%** | 💀 Critical | `🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥` *The Semicolon Citadel* |
| **`utils`** | 0 | 11 | **0.0%** | 🔴 High | `🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥` *Static Class Graveyard* |
| **`service & background`**| 0 | 18 | **0.0%** | 🟠 High | `🟥🟥🟥🟥🟥🟥🟥🟥🟥🟥` *Background Boilerplate* |
| **`tests (src/test)`** | 8 | 67 | **10.7%** | 🟡 Medium | `🟩🟨🟥🟥🟥🟥🟥🟥🟥🟥` *Legacy Assertions* |

---

## 🏆 4. Hall of Fame (Converted Kotlin Files)

These brave `49` files threw away the Java syntax baggage:

### 👑 Activities
* [AboutActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/AboutActivity.kt)
* [BackendExplorerActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/BackendExplorerActivity.kt)
* [BackupListActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/BackupListActivity.kt)
* [CalculatorActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CalculatorActivity.kt)
* [CalendarActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CalendarActivity.kt)
* [CategoryPickerActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CategoryPickerActivity.kt)
* [CategoryRuleListActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CategoryRuleListActivity.kt)
* [CategoryRuleSortActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CategoryRuleSortActivity.kt)
* [CategorySortActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/CategorySortActivity.kt)
* [ImportExportActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/ImportExportActivity.kt)
* [LockActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/LockActivity.kt)
* [MapActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/MapActivity.kt)
* [NewEditCategoryActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditCategoryActivity.kt)
* [NewEditCategoryRuleActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditCategoryRuleActivity.kt)
* [NewEditEventActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditEventActivity.kt)
* [NewEditPersonActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditPersonActivity.kt)
* [NewEditRecurrentTransactionActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditRecurrentTransactionActivity.kt)
* [NewEditRecurrentTransferActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditRecurrentTransferActivity.kt)
* [NewEditTransactionActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransactionActivity.kt)
* [NewEditTransactionModelActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransactionModelActivity.kt)
* [NewEditTransferActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransferActivity.kt)
* [NewEditTransferModelActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransferModelActivity.kt)
* [NewEditWalletActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditWalletActivity.kt)
* [PeriodDetailActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/PeriodDetailActivity.kt)
* [SearchActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/SearchActivity.kt)
* [TransactionListActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/TransactionListActivity.kt)
* [WalletListActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/WalletListActivity.kt)
* [WalletSortActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/WalletSortActivity.kt)
* [WalletWidgetConfigureActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/widget/WalletWidgetConfigureActivity.kt)
* [UpdaterActivity.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/updater/UpdaterActivity.kt)

### 🎨 Screens, ViewModels & Helpers
* [NewEditTransactionExt.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransactionExt.kt)
* [NewEditTransactionScreen.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransactionScreen.kt)
* [NewEditTransferExt.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransferExt.kt)
* [NewEditTransferScreen.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/activity/NewEditTransferScreen.kt)
* [TransactionListScreen.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/fragment/multipanel/TransactionListScreen.kt)
* [TransactionListViewModel.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/fragment/multipanel/TransactionListViewModel.kt)
* [TransactionMultiPanelFragment.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/fragment/multipanel/TransactionMultiPanelFragment.kt)
* [AboutFragment.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/fragment/single/AboutFragment.kt)
* [WalletWidgetConfigureViewModel.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/ui/widget/WalletWidgetConfigureViewModel.kt)
* [UpdaterViewModel.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/updater/UpdaterViewModel.kt)

### 📦 Models & Domain
* [GithubRelease.kt](https://github.com/solomonrajan/cashledger/blob/javatokotlin/app/src/main/java/com/oriondev/moneywallet/model/GithubRelease.kt)

---

## 🎯 5. Top Bounty Targets (Most Wanted Java Files)

```
================================================================================
 WANTED FOR UNLAWFUL VERBOSITY & WANTON ALLOCATION OF GETTERS/SETTERS
================================================================================
 [1] BaseActivity.java                   - Awaiting liberation ⚔️
 [2] CurrencyListActivity.java           - Awaiting liberation ⚔️
 [3] DrawerController.java               - Awaiting liberation ⚔️
 [4] IconListActivity.java               - Awaiting liberation ⚔️
 [5] LauncherActivity.java               - Awaiting liberation ⚔️
 [6] MainActivity.java                   - Awaiting liberation ⚔️
 [7] MultiPanelActivity.java             - Awaiting liberation ⚔️
 [8] NewEditBudgetActivity.java          - Awaiting liberation ⚔️
================================================================================
 REWARD: Infinite peace of mind & 0.00 NullPointerExceptions!
================================================================================
```

---

## 🎭 6. Funny Codes: Java PTSD vs Kotlin Zen

### Case 1: Checking a nested wallet balance

#### ☕ Java's Rite of Passage (Defensive Programming Hell)
```java
// Java: "Please, Mr. Compiler, don't crash the entire app today"
public String getBalanceFormatted(User user) {
    if (user != null) {
        Wallet wallet = user.getDefaultWallet();
        if (wallet != null) {
            Balance balance = wallet.getBalance();
            if (balance != null) {
                Currency currency = balance.getCurrency();
                if (currency != null) {
                    return currency.getSymbol() + " " + balance.getAmount();
                }
            }
        }
    }
    return "$ 0.00"; // 6 nested ifs just to buy a coffee
}
```

#### 🟣 Kotlin's Chad One-Liner
```kotlin
// Kotlin: "Look at what they need to mimic a fraction of our power."
fun getBalanceFormatted(user: User?): String = 
    "${user?.defaultWallet?.balance?.currency?.symbol ?: "$"} ${user?.defaultWallet?.balance?.amount ?: "0.00"}"
```

---

### Case 2: Model POJO Declaration

#### ☕ Java: 120 lines of ceremonial boilerplate
```java
public class TransactionTag {
    private long id;
    private String name;
    private int colorHex;

    public TransactionTag(long id, String name, int colorHex) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getColorHex() { return colorHex; }
    public void setColorHex(int colorHex) { this.colorHex = colorHex; }

    @Override
    public boolean equals(Object o) { /* another 20 lines generated by IDE */ }
    @Override
    public int hashCode() { /* another 10 lines */ }
    @Override
    public String toString() { /* another 15 lines */ }
}
```

#### 🟣 Kotlin: Blink and you miss it
```kotlin
data class TransactionTag(val id: Long, val name: String, val colorHex: Int)
```

---

### Case 3: Casting & Instance Checks

#### ☕ Java (Pre-16)
```java
if (event instanceof TransactionEvent) {
    TransactionEvent transactionEvent = (TransactionEvent) event;
    transactionEvent.triggerSoundEffects();
}
```

#### 🟣 Kotlin Smart Casts
```kotlin
if (event is TransactionEvent) event.triggerSoundEffects() // The compiler isn't blind!
```

---

## 🔮 7. Strategic Battle Plan

```mermaid
gantt
    title Kotlin Conquest Timeline
    dateFormat  YYYY-MM
    section UI Layer
    Finish Activities (22 remaining) :active, act1, 2026-10, 2026-11
    Convert Primary Fragments        :crit, act2, 2026-11, 2026-12
    section Data & Core
    Convert Models to Data Classes   :act3, 2026-12, 2027-01
    Database DAOs & Repository Room  :act4, 2027-01, 2027-02
    section Utilities & Tests
    Utils to Kotlin Extensions       :act5, 2027-02, 2027-03
    Migrate 75 Test Suites           :act6, 2027-03, 2027-04
```

> **Pro-tip:** Every converted Java file eliminates an average of 42 semicolons and reduces line count by 35%. Long live `val` and `?.`! 🚀

---

## 📜 8. Chronicles of the Crusade (Recent Commits & Dispatches)

> *A live dispatch of recent engineering battles, architecture refactors, and UI modernization:*

<details open>
<summary><b><code>5bbefe8</code></b> — <b>feat(metadata): migrate person and event activities to Kotlin and Jetpack Compose</b> <i>(2026-10-22 by Solomon Rajan)</i></summary>

* **Commit Link**: [`5bbefe8`](https://github.com/solomonrajan/cashledger/commit/5bbefe8)
* **Author**: Solomon Rajan • **Date**: 2026-10-22
* **Description / Scope**:

  > - Converted NewEditPersonActivity to Kotlin and Jetpack Compose Material 3
  > - Converted NewEditEventActivity to Kotlin and Jetpack Compose Material 3
  > - Removed legacy NewEditPersonActivity.java and NewEditEventActivity.java
  > - Added unit test suites NewEditPersonActivityTest and NewEditEventActivityTest
  > - Preserved legacy intent contract constants, Mode objects, and database column contracts

</details>

<details open>
<summary><b><code>95144f2</code></b> — <b>test(recurrence): align expected Contract column names with database schema definitions</b> <i>(2026-10-22 by Solomon Rajan)</i></summary>

* **Commit Link**: [`95144f2`](https://github.com/solomonrajan/cashledger/commit/95144f2)
* **Author**: Solomon Rajan • **Date**: 2026-10-22
* **Description / Scope**:

  > - Update testContractColumnNames in NewEditRecurrentTransactionActivityTest to assert
  >   against the exact schema column names prefixed with 'recurrent_transaction_'.
  > - Update testContractColumnNames in NewEditRecurrentTransferActivityTest to assert
  >   against the exact schema column names prefixed with 'recurrent_transfer_'.
  > - Resolves test failures during testFlossOsmDebugUnitTest on CI.

</details>

<details open>
<summary><b><code>19acd96</code></b> — <b>fix(recurrence): resolve unresolved references and detekt issues in recurrent activities</b> <i>(2026-10-22 by Solomon Rajan)</i></summary>

* **Commit Link**: [`19acd96`](https://github.com/solomonrajan/cashledger/commit/19acd96)
* **Author**: Solomon Rajan • **Date**: 2026-10-22
* **Description / Scope**:

  > - Change taxPicker.setCurrentMoney(0L) to taxPicker.setMoney(0L) in NewEditRecurrentTransferActivity.
  > - Simplify save condition checks in both recurrent activities to prevent ComplexCondition detekt failures.
  > - Add ComplexCondition to @file:Suppress annotations.

</details>

<details open>
<summary><b><code>d8debea</code></b> — <b>feat(recurrence): migrate recurrent transaction and transfer activities to Kotlin and Jetpack Compose</b> <i>(2026-10-22 by Solomon Rajan)</i></summary>

* **Commit Link**: [`d8debea`](https://github.com/solomonrajan/cashledger/commit/d8debea)
* **Author**: Solomon Rajan • **Date**: 2026-10-22
* **Description / Scope**:

  > - Convert NewEditRecurrentTransactionActivity to Kotlin and Jetpack Compose (Material 3):
  >   - Implement edge-to-edge layout with TopAppBar, save action, and edit-mode delete confirmation dialog.
  >   - Interactive amount card displaying formatted money and currency symbol with MoneyPicker integration.
  >   - Category, wallet, and recurrence schedule picker cards with IconLoader and RecurrencePicker integrations.
  >   - Optional place/event cards, note inputs, and checkboxes for confirmed and countInTotal state.
  >   - Trigger RecurrenceBroadcastReceiver.scheduleRecurrenceTask upon save and delete.
  >   - Delete legacy NewEditRecurrentTransactionActivity.java.
  >
  > - Convert NewEditRecurrentTransferActivity to Kotlin and Jetpack Compose (Material 3):
  >   - Transfer amount header with source wallet currency resolution.
  >   - Dual wallet cards for source and destination with 1-tap swap action.
  >   - Tax amount picker card with clear action.
  >   - Recurrence schedule card with RecurrencePicker integration.
  >   - Optional place/event cards, note input, and checkboxes.
  >   - Trigger RecurrenceBroadcastReceiver.scheduleRecurrenceTask upon save and delete.
  >   - Delete legacy NewEditRecurrentTransferActivity.java.
  >
  > - Add Robolectric unit test suites:
  >   - Add NewEditRecurrentTransactionActivityTest.kt covering companion constants, category/wallet/recurrence assignment, and contract column definitions.
  >   - Add NewEditRecurrentTransferActivityTest.kt covering companion constants, wallet swapping logic, recurrence settings, and database column mappings.
  >
  > - Update KOTLIN_MIGRATION_REPORT.md:
  >   - Advance Realm I: Citadel of Activities liberation progress to 55.6% (30 Kotlin vs 24 Java).
  >   - Update codebase totals to 45 Kotlin files (8.4%).

</details>

<details open>
<summary><b><code>4cb509f</code></b> — <b>test(model): align expected Contract column names with database schema definitions</b> <i>(2026-10-20 by Solomon Rajan)</i></summary>

* **Commit Link**: [`4cb509f`](https://github.com/solomonrajan/cashledger/commit/4cb509f)
* **Author**: Solomon Rajan • **Date**: 2026-10-20
* **Description / Scope**:

  > - Update testContractColumnNames in NewEditTransactionModelActivityTest to assert
  >   against the exact schema column names prefixed with "model_transaction_"
  >   (model_transaction_money, model_transaction_description, model_transaction_category,
  >   model_transaction_wallet, model_transaction_confirmed, model_transaction_count_in_total).
  > - Update testContractColumnNames in NewEditTransferModelActivityTest to assert
  >   against the exact schema column names prefixed with "model_transfer_"
  >   (model_transfer_description, model_transfer_from_wallet, model_transfer_to_wallet,
  >   model_transfer_from_money, model_transfer_to_money, model_transfer_tax_money,
  >   model_transfer_confirmed, model_transfer_count_in_total).
  > - Resolves test failures during testFlossOsmDebugUnitTest on CI.

</details>

<details open>
<summary><b><code>e8d2386</code></b> — <b>fix(model): resolve unresolved references and detekt issues in model activities</b> <i>(2026-10-20 by Solomon Rajan)</i></summary>

* **Commit Link**: [`e8d2386`](https://github.com/solomonrajan/cashledger/commit/e8d2386)
* **Author**: Solomon Rajan • **Date**: 2026-10-20
* **Description / Scope**:

  > - Correct save button string resource reference from R.string.action_save_changes
  >   to R.string.menu_action_save_changes in both NewEditTransactionModelActivity and
  >   NewEditTransferModelActivity.
  > - Replace unresolved wallet.icon references with native Compose
  >   Icons.Default.AccountBalanceWallet within circular background containers.
  > - Fix MoneyPicker and WalletPicker method calls in NewEditTransferModelActivity:
  >   - Replace non-existent taxPicker.setCurrentMoney() with taxPicker.setMoney().
  >   - Streamline wallet swap action by updating state and picker currencies directly
  >     without non-existent setCurrentWallet calls.
  > - Resolve Detekt static analysis issues:
  >   - Extract parsePlaceFromCursor and parseEventFromCursor helper functions to flatten
  >     cursor reading logic and satisfy NestedBlockDepth limits.
  >   - Consolidate validation guard statements in saveTransactionModel and
  >     saveTransferModel to satisfy ReturnCount constraints.
  >   - Add NestedBlockDepth and ReturnCount to @file:Suppress annotations.

</details>

<details open>
<summary><b><code>19e3d65</code></b> — <b>feat(model): migrate transaction and transfer model activities to Kotlin and Jetpack Compose</b> <i>(2026-10-20 by Solomon Rajan)</i></summary>

* **Commit Link**: [`19e3d65`](https://github.com/solomonrajan/cashledger/commit/19e3d65)
* **Author**: Solomon Rajan • **Date**: 2026-10-20
* **Description / Scope**:

  > - Convert NewEditTransactionModelActivity to Kotlin and Jetpack Compose (Material 3):
  >   - Implement edge-to-edge layout with TopAppBar, save action, and edit-mode delete confirmation dialog.
  >   - Interactive amount card displaying formatted money and currency symbol with MoneyPicker integration.
  >   - Category and wallet selection cards with AndroidView IconLoader integration and validation error styling.
  >   - Event, place, description, note inputs, and checkboxes for confirmed and countInTotal state.
  >   - Maintain backward compatibility for Intent extras (MODE, ID) and nested Mode object.
  >   - Delete legacy NewEditTransactionModelActivity.java.
  >
  > - Convert NewEditTransferModelActivity to Kotlin and Jetpack Compose (Material 3):
  >   - Transfer amount header with source wallet currency resolution.
  >   - Dual wallet cards for source and destination with 1-tap swap action.
  >   - Optional tax amount picker, event/place cards, description, note, and checkboxes.
  >   - Validate that both source and destination wallets are selected before saving.
  >   - Delete legacy NewEditTransferModelActivity.java.
  >
  > - Add Robolectric unit test suites:
  >   - Add NewEditTransactionModelActivityTest.kt covering companion constants, mode mappings, model entity assignment, and contract column definitions.
  >   - Add NewEditTransferModelActivityTest.kt covering companion constants, wallet swapping logic, and database column mappings.
  >
  > - Update KOTLIN_MIGRATION_REPORT.md:
  >   - Cross 50% milestone in Realm I: Citadel of Activities (28 Kotlin vs 26 Java, 51.9%).
  >   - Update codebase totals to 41 Kotlin files (7.7%).

</details>

<details open>
<summary><b><code>2c9350b</code></b> — <b>fix(test): configure Robolectric runner for activity tests and suppress SwallowedException</b> <i>(2026-10-20 by Solomon Rajan)</i></summary>

* **Commit Link**: [`2c9350b`](https://github.com/solomonrajan/cashledger/commit/2c9350b)
* **Author**: Solomon Rajan • **Date**: 2026-10-20
* **Description / Scope**:

  > - Annotate CategoryRuleActivityTest and NewEditTransferActivityTest with @RunWith(RobolectricTestRunner::class) to provide Android Uri/framework stubs during unit tests.
  > - Add SwallowedException to @file:Suppress in NewEditTransferActivity.kt to resolve Detekt static analysis error.
  > - Fixes CI test and Detekt failures to restore automated pre-release generation.

</details>
