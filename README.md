# Advanced File Types

## Overview

Advanced File Types is a plugin for JetBrains IDEs that enables project-specific file type overrides by directory and filename patterns (such as treating all `.txt` files in `docs/` as Markdown, or matching custom naming conventions).

### Features
- **Project-Specific Overrides**: Define custom file type rules stored per project in `.idea/advancedFileTypes.xml`.
- **VS Code Settings Compatibility**: Dynamically reads file associations from `.vscode/settings.json` (`files.associations`) with JSONC comment and trailing comma support.
- **Git Attributes Compatibility**: Dynamically reads `linguist-language` overrides from `.gitattributes` for GitHub Linguist language detection.
- **Directory and Filename Pattern Matching**: Supports both Glob (e.g. `docs/**/*.txt`, `*.custom`, `src/templates/*`) and Regular Expression syntax.
- **Ordered Evaluation & Precedence**: Rules are evaluated top-to-bottom: project override rules take first precedence, followed by `.vscode/settings.json` associations, then `.gitattributes` `linguist-language` overrides, falling back to standard IDE file type associations.
- **Settings UI**: Accessible via **Settings / Preferences > Editor > File Type Matching** with add, edit, remove, reorder, VS Code and Git attributes toggles, and an interactive pattern testing tool.

## Pattern Syntax

### Glob Patterns

Glob patterns provide a simple and expressive syntax for matching filenames and directory paths relative to the project root. All glob pattern matching is **case-insensitive**.

#### Supported Glob Syntax

| Pattern | Description | Example | Matches |
| :--- | :--- | :--- | :--- |
| `*` | Matches any characters within a single directory level (does not match `/`). | `docs/*.txt` | `docs/readme.txt` (but not `docs/sub/readme.txt`) |
| `**` | Matches zero or more directories / subdirectories recursively. | `docs/**/*.txt` | `docs/readme.txt`, `docs/sub/guide.txt`, `docs/a/b/c/note.txt` |
| `?` | Matches exactly one single character (excluding `/`). | `file-?.log` | `file-1.log`, `file-a.log` (not `file-12.log`) |
| `{a,b,...}` | Alternation: matches any one of the comma-separated choices. | `*.{txt,md}` | `file.txt`, `file.md` (not `file.html`) |
| `[abc]`, `[0-9]` | Character class: matches any single character enclosed in brackets. | `src/test-[0-9].kt` | `src/test-1.kt`, `src/test-5.kt` |
| `[!abc]` | Negated character class: matches any single character not enclosed in brackets. | `data-[!0-9].csv` | `data-a.csv` (not `data-1.csv`) |

#### Path Matching Rules for Globs

- **Filename-only patterns (no `/`)**: Patterns that do not contain a slash (such as `*.txt` or `build.gradle`) match files anywhere in the project tree regardless of directory depth.
- **Path patterns (containing `/`)**: Patterns containing a directory separator (such as `docs/*.txt`, `src/**/*.kt`, or `**/tests/*.spec.ts`) match paths relative to the project root.
- **Leading separators**: Leading `./` and `/` are stripped and normalized (e.g. `./docs/*.txt` is treated the same as `docs/*.txt`).
- **Path separators**: Both Windows backslashes (`\`) and Unix forward slashes (`/`) are normalized to forward slashes.

### Regular Expressions

Regular expression syntax offers fine-grained matching capabilities for complex naming conventions.

#### How Regex Patterns Are Applied

- **Full relative pathname match**: Regular expressions are evaluated against the **entire project-relative path** (using normalized forward slashes `/`, without leading `/` or `./`). Regex matching requires a complete string match (equivalent to having implicit start `^` and end `$` anchors across the full relative path).
- **Matching sub-paths or substrings**: Because regexes must match the entire path, use `.*` if you want to match anywhere in the path or match within arbitrary subdirectories (e.g. `docs/.*\.txt` or `.*service-\d+\.cfg`).
- **Filename fallback**: If a file is not associated with a project directory (or relative path cannot be determined), the expression is evaluated against the standalone filename.
- **Case sensitivity**: Regex matching is compiled with case-insensitivity (`Pattern.CASE_INSENSITIVE`).

#### Regex Syntax Reference

Regex patterns follow the standard Java regular expression syntax (`java.util.regex.Pattern`), which is the standard regex engine utilized across the IntelliJ Platform:

- [Oracle Java Regular Expressions (`java.util.regex.Pattern`) Reference](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html)
- [JetBrains IntelliJ IDEA Regular Expressions Guide](https://www.jetbrains.com/help/idea/regular-expressions.html)

## Git Attributes Support

The plugin can dynamically read `linguist-language` overrides from a project's `.gitattributes` file, allowing teams using [GitHub Linguist](https://github.com/github-linguist/linguist) language overrides to have those same file type associations automatically honored inside JetBrains IDEs.

### How It Works

When enabled (by default), the plugin reads `.gitattributes` from the project root and extracts all `linguist-language` attributes. These are evaluated after plugin rules and `.vscode/settings.json` associations, using Git's **"last match wins"** semantics.

### Example

```text
# .gitattributes
*.data linguist-language=JSON
docs/*.txt linguist-language=Markdown
docs/raw.txt -linguist-language
```

- `config.data` → JSON syntax highlighting
- `docs/guide.txt` → Markdown syntax highlighting
- `docs/raw.txt` → Falls back to IDE default (attribute explicitly unset)

### Supported Attribute Formats

| Format | Description |
| :--- | :--- |
| `linguist-language=Language` | Assign a language (e.g., `linguist-language=Python`) |
| `linguist-language="Language"` | Quoted language name for spaces (e.g., `linguist-language="C++"`) |
| `-linguist-language` | Unset the language override, falling back to IDE defaults |
| `!linguist-language` | Unset the language override (same as `-linguist-language`) |

### Pattern Matching

`.gitattributes` patterns use Glob syntax with these rules:
- **Filename-only patterns** (no `/`): Match files anywhere in the project (e.g., `*.data`).
- **Path patterns** (containing `/`): Match relative to the project root (e.g., `docs/*.txt`).
- Patterns are **case-insensitive**.

### Language Resolution

The plugin maps GitHub Linguist language names to IntelliJ FileTypes using a static alias map for common languages (e.g., `Shell` → `Shell Script`, `reStructuredText` → `ReST`, `JSON with Comments` → `JSON5`), with dynamic fallback to installed file types by name, display name, or language ID.

If a language is not recognized or not installed in the current IDE, the override is skipped and the file falls back to the IDE's default file type.

### Precedence

`.gitattributes` overrides are evaluated in this order:
1. **Plugin override rules** (configured in Settings UI) — highest priority
2. **`.vscode/settings.json`** `files.associations` (if enabled)
3. **`.gitattributes`** `linguist-language` (if enabled) — last match wins
4. **IDE default file types** — lowest priority

You can disable `.gitattributes` integration via the checkbox in **Settings / Preferences > Editor > File Type Matching**.
