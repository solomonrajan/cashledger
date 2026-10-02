# Modernization Workflow Rules

1. **Architecture Goals**: All future edits, migrations, and new code must be written with the strict goal of completely modernizing the architecture.
2. **Key Technologies**: Use **Jetpack Compose** for UI and **Kotlin Coroutines / Flow** for asynchronous logic and state management.
3. **Avoid Legacy Patterns**: Do not just do a 1:1 translation of legacy Android patterns (like `LoaderManager`, `CursorAdapter`, `IntentService`, XML layouts). Actively refactor towards modern MVVM / MVI patterns with `ViewModel` and `StateFlow`.
4. **Auto-commit**: Continue to auto-commit all successful conversions with a proper commit message and detailed commit description unless told otherwise.
5. **Language**: Strictly use the latest Kotlin language for all new logic.
