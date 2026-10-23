<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced File Types Plugin Changelog

## Unreleased

### Added

- Enabled JetBrains Marketplace reporting for plugin-attributed exceptions and IDE freezes.

### Fixed

- Removing multiple selected rules now removes all selected rows.

## 0.2.0 - 2026-10-22

### Added

- Support for custom mappings of VS Code and Linguist language IDs to IDE file types in the settings file.
- Notifications for invalid entries and unsupported file types in the settings file.

### Changed

- Moved the Advanced File Types settings page under **Settings | Editor | File Types**.
- Test Pattern Matching now displays the global file type when no override rule applies.
- Test Pattern Matching now highlights the matching pattern in the rules list.
- Refined and corrected some built-in VS Code and Linguist language ID mappings.
- Documented limitation on files located outside the project root folder.

### Fixed

- Stop at the first matching rule when its target file type is unavailable in the current IDE.
- Prevented `LockAccessDisallowed` exceptions during editor painting when resolving project-specific file types.
- Prevented potential IDE freezes and UI delays during project startup when resolving `.gitattributes` file associations.
- Prevented background thread stalls and editor lag caused by workspace index lock contention during file type resolution.
- Prevented "Write-unsafe context" errors when refreshing file type associations during background updates.
- Fixed settings panel failing to open when `advancedFileTypes.xml` contains syntax errors or unrecognized tags.

## 0.1.0 - Released 2026-10-13

### Added

- Initial release, supporting IDE versions 2025.3 through 2026.3 (EAP).
- File type overrides using glob or regex patterns.
- VS Code settings `files.associations` support.
- .gitattributes `linguist-language` support.
