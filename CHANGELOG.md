<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced File Types Plugin Changelog

## [Unreleased]

### Added
* Support for custom mappings of VS Code and Linguist language IDs to IDE file types in the settings file.

### Changed
* Moved the Advanced File Types settings page under **Settings | Editor | File Types**.
* Test Pattern Matching now displays the global file type when no override rule applies.
* Test Pattern Matching now highlights the matching pattern in the rules list.
* Refined and corrected some built-in VS Code and Linguist language ID mappings.

### Fixed
* Stop at the first matching rule when its target file type is unavailable in the current IDE.
* Prevented `LockAccessDisallowed` exceptions during editor painting when resolving project-specific file types.

## [0.1.0] - Released 2026-10-13

### Added
* Initial release, supporting IDE versions 2025.3 through 2026.3 (EAP).
* File type overrides using glob or regex patterns.
* VS Code settings `files.associations` support.
* .gitattributes `linguist-language` support.
