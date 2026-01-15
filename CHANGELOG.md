# Changelog

All notable changes to this project will be documented in this file.

*The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)*

## 0.1.2 - 2026-01-15

### Added

- `:exclude-patterns` option for `hash-assets!` to filter out files by regex patterns
- `:include-patterns` option for `hash-assets!` to whitelist specific files by regex patterns
- Incremental hashing support - `hash-assets!` now merges new assets into existing manifest instead of overwriting

### Fixed

- Fixed binary file corruption in `hash-asset-file!` and `fetch-asset!` functions. 
The previous implementation used `slurp`/`spit` which corrupted binary files (like PNG images) by applying UTF-8 encoding. 
Binary files are now handled correctly using `io/input-stream` and `io/output-stream`. (@mkrcah)

## 0.1.0 - 2025-03-12

### Added

- Initial commit. 
