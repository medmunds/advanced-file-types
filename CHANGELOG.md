<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced File Types Plugin Changelog

## [Unreleased]

### Changed
* Test Pattern Matching now displays the global file type when no override rule applies.

### Fixed
* Prevented `LockAccessDisallowed` exceptions during editor painting when resolving project-specific file types.

## [0.1.0] - Released 2026-10-13

### Added
* Initial release, supporting IDE versions 2025.3 through 2026.3 (EAP).
* File type overrides using glob or regex patterns.
* VS Code settings `files.associations` support.
* .gitattributes `linguist-language` support.
