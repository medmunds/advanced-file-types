<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced-file-types Changelog

## [Unreleased]
### Added
- Project-specific file type override rules matching directory and filename patterns (Glob and Regex).
- Settings configurable under **Editor > Advanced File Types** with rule management (add, edit, remove, reorder) and interactive pattern testing.
- Integration with IntelliJ file type detection via `FileTypeOverrider`.
- Settings dialog help button support and dialog context help linking to GitHub documentation.
- Extracted all UI strings to resource bundle for full localization support, including human-readable syntax options.
- Added typeahead speed search (`ComboboxSpeedSearch`) on the file type selector in the rule configuration dialog.
- Added dynamic VS Code `.vscode/settings.json` (`files.associations`) integration supporting JSONC comments, trailing commas, static/dynamic language mapping, setting enable toggle, and tester diagnostics.

### Fixed
- Allowed file type overrides to apply to `.vscode/settings.json` itself via plugin rules or self-referential associations.
