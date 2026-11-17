# Kotlin Migration Guidelines & Code Standards

This document establishes the technical rules, quality guardrails, and PR workflows for contributing to Cash Ledger's pure Kotlin migration.

---

## 🛡️ Guardrails & Static Analysis (Detekt + Ktlint)

All Kotlin code submitted to the repository must pass **Detekt** and **Ktlint** static analysis checks.

### Running Checks Locally

```bash
# Run detekt and formatting rules
./gradlew detekt

# Auto-correct formatting and style violations
./gradlew detekt --auto-correct
```

CI will automatically run `./gradlew detekt` on every pull request and push to main branches.

---

## 🚫 The Zero-Force-Unwrap (`!!`) Rule

The double-bang operator (`!!`) is **strictly forbidden** in PRs (enforced by Detekt rule `UnsafeCallOnNullableType`).

### Idiomatic Alternatives:

```kotlin
// ❌ FORBIDDEN:
val name = user!!.name

// ✅ Safe call with Elvis fallback:
val name = user?.name ?: "Unknown"

// ✅ Explicit precondition with clear message:
val validUser = requireNotNull(user) { "User cannot be null when rendering header" }

// ✅ Scoped block:
user?.let { nonNullUser ->
    renderUser(nonNullUser)
}

// ✅ Smart cast:
if (user != null) {
    user.render() // Automatically non-null
}
```

---

## 🔄 The 2-Commit PR Workflow

To keep code review transparent and git history bisectable:

1. **Commit 1 (`refactor: mechanical conversion of <Class>`):**
   - Run the automated converter or syntax port.
   - Fix compilation errors only.
   - Do **not** re-architect or introduce new paradigms in this commit.
2. **Commit 2 (`refactor: idiomatic Kotlin polish of <Class>`):**
   - Replace POJO boilerplate with `data class`.
   - Remove force-unwraps (`!!`).
   - Use default argument values instead of constructor chaining.
   - Introduce extension functions and property delegates (`by lazy`, `by viewModels()`).

> **Rule:** *Never mix functional redesigns, UI rework, or unrelated library updates into a migration PR.*

---

## ☕ Java $\leftrightarrow$ Kotlin Interoperability Conventions

While parts of the codebase remain in Java during intermediate phases:

1. **Top-Level Singletons:** Mark methods in `object` singletons with `@JvmStatic` so remaining Java callers can call `Urls.isAcceptableUrl(...)` without `.INSTANCE`.
2. **Testing Access:** If a package-private field is accessed by existing Java unit tests (e.g. `EquationSolverTest.java`), mark it with:
   ```kotlin
   @JvmField
   @VisibleForTesting
   internal var mProperty: Type? = null
   ```
3. **Thread Safety:** When converting classes managing shared state (e.g., `CurrencyManager`), preserve `@Volatile` annotations and explicit lock objects.
4. **Pre-Annotate Java:** When a Java class interacts with code you are converting, add AndroidX `@NonNull` / `@Nullable` annotations to its signatures in Java first.

---

## 💎 Idiomatic Kotlin Best Practices

### 1. Immutability by Default
Always declare variables with `val`. Only use `var` when mutation is strictly necessary (Detekt will flag `VarCouldBeVal`).

### 2. Data Classes
Replace Java POJOs (getters, setters, `equals`, `hashCode`, `toString`) with concise Kotlin data classes:
```kotlin
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Int = 0
)
```

### 3. Extension Functions
Prefer Kotlin extension functions over static utility methods:
```kotlin
// View extension
fun View.showIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}
```

### 4. Automatic Resource Closing
Replace `try ... finally { cursor.close() }` with Kotlin's standard library `.use { }`:
```kotlin
cursor?.use { c ->
    while (c.moveToNext()) {
        // Safe reading
    }
}
```

---

## 📋 Pre-PR Checklist

Before opening a pull request for any migration phase:
- [ ] `./gradlew test` passes all unit tests.
- [ ] `./gradlew detekt` reports zero issues.
- [ ] No `!!` operators exist in the new or modified code.
- [ ] Existing copyright and GPLv3 license headers are preserved.
- [ ] The phase checklist and progress meter in [`KOTLIN_MIGRATION.mdx`](../KOTLIN_MIGRATION.mdx) are updated.

