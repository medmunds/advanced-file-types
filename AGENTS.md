# AGENTS.md

This document provides essential context, architectural principles, build workflows, and development guidelines for autonomous agents and developers working on the **Advanced File Types** JetBrains IDE plugin.

---

## 1. Project Overview

**Advanced File Types** is a plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc., version 2025.3+) that enables project-specific file type associations and pattern-based overrides.

### Core Problem Solved
JetBrains IDEs natively support only global, project-agnostic file type associations based on simple filename extensions/patterns without path awareness or priority ordering. This plugin allows:
- Project-scoped file type rules saved in `.idea/advancedFileTypes.xml`.
- Path-aware glob and regex matching (e.g., `templates/**/*.html` as Jinja2 while other `.html` files remain plain HTML).
- Priority-ordered rule lists.
- Interoperability with VS Code (`.vscode/settings.json` `files.associations`) and GitHub Linguist (`.gitattributes` `linguist-language` attributes).

---

## 2. Architecture & Design Principles

### Evaluation Hierarchy
When resolving a file's type, rules are evaluated in the following strict order, stopping at the first match:
1. **Custom Project Rules:** Configured in **Settings | Editor | Advanced File Types**, evaluated top-to-bottom.
2. **VS Code Settings:** `.vscode/settings.json` (`files.associations`), if enabled.
3. **Git Attributes:** `.gitattributes` (`linguist-language`), if enabled.
4. **IDE Global Associations:** Default IntelliJ fallback (`Settings | Editor | File Types`).

### Critical VFS vs PSI Architecture Rule
* **`FileTypeOverrider` runs at the VFS level before PSI trees exist.**
* **Never invoke PSI operations or high-level project index queries inside file type resolution.** Doing so causes circular file-type resolution dependencies, deadlocks, and threading violations.
* File parsing for external configuration (such as `.vscode/settings.json`) must use lightweight parsing (e.g., `json5-java` with `VfsUtilCore.loadText` / `VirtualFile.contentsToByteArray()`) rather than PSI or IntelliJ JSON PSI files.
* Recursion guard: `AdvancedFileTypeOverrider` uses a `ThreadLocal<Boolean>` flag (`isResolving`) to prevent infinite recursion during resolution.

### Dynamic Plugin Loading (No IDE Restart)
* The plugin must fully support **dynamic plugin loading** (installation, updating, enabling, and unloading without requiring an IDE restart).
* Avoid storing static references to `Project`, `VirtualFile`, or disposable objects.
* Use declarative extension points and listeners registered in `plugin.xml` so the platform can safely unload and reload plugin classes.

### Pattern Matching Semantics (`PathPatternMatcher`)
- **Glob Syntax**: Compatible with [VS Code Glob Patterns](https://code.visualstudio.com/docs/editor/glob-patterns) (`*`, `?`, `**`, `{a,b,...}`, `[a-z]`, `[!a-z]`, `[^a-z]`, and bracket escapes like `[[]` and `[]]`).
- **Globs without `/`**: Match against filename only, anywhere in the project hierarchy (e.g., `*.txt` matches `file.txt` and `dir/sub/file.txt`). This aligns with JetBrains conventions and VS Code's `files.associations` filename pattern matching.
- **Globs with `/`**: Match against the full project-relative path from the project root (e.g., `docs/*.txt`, or `/*.html` / `./*.html` to match root-only files).
- **Regex**: Always evaluated against the entire project-relative path with normalized `/` separators, case-insensitive, anchored to the full string (`^...$`).
- **Case Sensitivity**: Retained case-insensitive on all platforms for both user-entered patterns and `.vscode/settings.json` associations for cross-platform consistency.
- **Path Normalization**: Windows backslashes `\` are always normalized to `/`. Leading `/` and `./` anchor the glob to the project root.

### Key Components & Package Structure
`com.medmunds.advancedfiletypes`
- **`AdvancedFileTypeOverrider`**: Plugin entry point implementing `com.intellij.openapi.fileTypes.impl.FileTypeOverrider`.
- **`model/`**:
  - `FileTypeOverrideRule`: Data model for custom rules (pattern, fileType name, syntax mode: GLOB vs REGEX).
  - `PathPatternMatcher`: Compiled pattern matcher supporting glob-to-regex translation and direct regex matching.
- **`settings/`**:
  - `AdvancedFileTypeSettings`: Project-level service (`@Service(Service.Level.PROJECT)`) managing persistent state (`.idea/advancedFileTypes.xml`).
- **`vscode/`**:
  - `VsCodeSettingsReader`: Reads `files.associations` from `.vscode/settings.json`.
  - `VsCodeLanguageResolver`: Resolves VS Code language identifiers to IntelliJ `FileType` instances via `vscode-language-map.properties` and case-insensitive fallback.
  - `VsCodeSettingsFileListener`: `BulkFileListener` detecting changes to `.vscode/settings.json` to trigger file type refresh.
- **`gitattributes/`**:
  - `GitAttributesService`: Project-level service managing a long-lived `git check-attr --stdin -z linguist-language` process with caching, timeout handling, and failure recovery.
  - `LinguistLanguageResolver`: Maps Linguist language identifiers to IntelliJ `FileType` instances via `linguist-language-map.properties`.
  - `GitAttributesFileListener`: `BulkFileListener` detecting changes to any `.gitattributes` or `.git/info/attributes` files to trigger cache clear and file type refresh.
- **`ui/`**:
  - `AdvancedFileTypeConfigurable`: Settings page registered under `editor` (`Settings | Editor | Advanced File Types`).
  - `AddEditRuleDialog`: Modal dialog for creating and editing individual pattern rules.
- **`help/`**:
  - `AdvancedFileTypeWebHelpProvider`: Directs IDE help buttons to online documentation.

---

## 3. Technology Stack & Tooling

- **Language:** Kotlin (JVM target 21+)
- **Build System:** Gradle (Kotlin DSL: `build.gradle.kts`, `settings.gradle.kts`)
- **Platform Plugin:** IntelliJ Platform Gradle Plugin (`org.jetbrains.intellij.platform`) 2.x
- **Target Platform:** IntelliJ IDEA Community / Ultimate 2025.3+ (`IU-2025.3.6.1`)
- **Testing Frameworks:** JUnit 5 (`junit-jupiter`), JUnit 4 vintage engine, IntelliJ Platform Test Framework (`TestFrameworkType.Platform`)

---

## 4. Build, Test, and Verification Commands

All standard tasks use the Gradle wrapper:

### Run Unit and Platform Tests
```bash
./gradlew test
```
or full verification:
```bash
./gradlew check
```

### Build Plugin Distribution
Generates the deployable plugin archive at `build/distributions/advanced-file-types-<version>.zip`:
```bash
./gradlew buildPlugin
```

### Run in Sandbox IDE
Launches a test IDE instance with the plugin enabled:
```bash
./gradlew runIde
```

---

## 5. Development Guidelines for Agents

1. **Safety in File Type Resolution:**
   Keep `getOverriddenFileType` lightweight and fast. File type resolution occurs frequently on EDT and background indexing threads. Avoid blocking IO or locks.
2. **Platform & SDK First (Minimal Dependencies):**
   - Prefer using built-in IntelliJ Platform SDK services, utilities, and APIs rather than pulling in additional third-party dependencies or libraries.
   - Utilize standard platform facilities (e.g., `VfsUtilCore`, platform XML serialization, standard file listeners) to minimize maintenance overhead, reduce binary size, and ensure seamless platform integration.
3. **JetBrains UI Components & Guidelines:**
   - Use standard JetBrains UI components (e.g., Kotlin UI DSL v2, `JBTable`, `JBTextField`, `JBLabel`, `ToolbarDecorator`) and adhere strictly to the JetBrains UI Guidelines to ensure a cohesive, native user experience across themes and OS platforms.
   - User-facing text must be defined in `src/main/resources/messages/AdvancedFileTypesBundle.properties` and accessed via `AdvancedFileTypesBundle.message(...)`.
4. **API Currency & No Deprecated APIs:**
   - Do not introduce or use deprecated IntelliJ Platform APIs or Kotlin features. Always use modern, actively supported APIs for IntelliJ 2025.3+.
5. **Language Mapping Updates:**
   - When adding support for new language identifiers from VS Code, update `src/main/resources/com/medmunds/advancedfiletypes/vscode-language-map.properties`.
   - When adding support for GitHub Linguist languages, update `src/main/resources/com/medmunds/advancedfiletypes/linguist-language-map.properties`.
   - Ensure mapping tests in `VsCodeLanguageResolverTest` and `LinguistLanguageResolverTest` cover the new additions.
6. **Platform Event Notification:**
   Whenever rule settings or external association files change, call `AdvancedFileTypeSettings.notifyFileTypesChanged()` (which triggers `FileTypeManagerEx.getInstance().fireFileTypesChanged()`) so open editors update their syntax highlighting.
7. **Dynamic Loading Compatibility:**
   Ensure all newly introduced services, listeners, and extensions remain compatible with dynamic plugin loading (no memory leaks on project closing or plugin unloading).
8. **Testing & Regression Expectations:**
   - **Bug Fixes & Regression Coverage:** Always add regression tests when fixing bugs to ensure issues do not recur.
   - **Focused Test Cases:** Keep test cases atomic and focused on a single behavior or scenario; avoid compound test cases.
   - **Test Layering:** Do not duplicate unit-level tests at the integration or platform level unless there is a specific reason or interaction that warrants it.
   - **Pattern Matching:** Always verify pattern matching additions with parameterized unit tests in `PathPatternMatcherTest`.
   - **Verification:** Run `./gradlew check` before completing any code changes.
9. **Documentation Currency (`AGENTS.md` & `README.md`):**
   - Keep `AGENTS.md` up to date. Update this document for any significant architectural decisions, component changes, or modifications that would otherwise leave the documentation inaccurate or misleading.
   - Update `README.md` in concert with any changes that affect user-facing features, configuration options, workflows, or supported behavior.
