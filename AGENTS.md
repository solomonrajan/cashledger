# Agent Guidelines & Rules

## UI Development & XML Migration Policy: Jetpack Compose Strategy

- **No New XML Files**: Never create new XML layout or view files (`res/layout/*.xml`). All new UI screens, components, dialogs, headers, and view items must be implemented exclusively in **Jetpack Compose**.
- **Upgrade on Edit**: When any existing XML layout or view requires editing or updates, upgrade that UI component to a Jetpack Compose implementation rather than editing the XML file.
- **Interoperability & Integration**:
  - In existing View-based Activities or Fragments, embed Compose using `ComposeView` (`setContent { ... }`) or `ComponentActivity.setContent { ... }`.
  - For lists/recyclers, migrate to Compose `LazyColumn` / `LazyRow` or embed `ComposeView` in list items as part of the component upgrade.
  - Follow Material 3 Compose standards (`androidx.compose.material3.*`).
  - Adhere to the codebase's 100% pure Kotlin standard with zero `!!` operators.
