# Advanced File Types

## Overview

Advanced File Types is a plugin for JetBrains IDEs that enables project-specific file type overrides by directory and filename patterns (such as treating all `.txt` files in `docs/` as Markdown, or matching custom naming conventions).

### Features
- **Project-Specific Overrides**: Define custom file type rules stored per project in `.idea/advancedFileTypes.xml`.
- **Directory and Filename Pattern Matching**: Supports both Glob (e.g. `docs/**/*.txt`, `*.custom`, `src/templates/*`) and Regular Expression syntax.
- **Ordered Evaluation**: Rules are evaluated top-to-bottom; the first matching rule takes precedence. If no rule matches, standard IDE file type associations apply.
- **Settings UI**: Accessible via **Settings / Preferences > Editor > File Type Matching** with add, edit, remove, reorder, and an interactive pattern testing tool.
