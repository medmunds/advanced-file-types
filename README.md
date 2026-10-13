# Advanced File Types plugin for JetBrains IDEs

A plugin for JetBrains IDEs that enables project-specific file type associations.
* Treat `templates/**/*.html` as Jinja2 while leaving other `.html` files as plain HTML.
* Handle `*.sql` as MySQL in one project and PostgreSQL in another.
* Use file type rules from VS Code settings or GitHub Linguist associations stored with the project.

## Features

* **Custom pattern matching:** Define custom file type patterns using globs or regular expressions. Patterns can match filenames or full paths and are applied in the order you specify.

  <!-- TODO: Screenshot of the settings UI showing a few configured override rules in the table -->

* **Per-project settings:** Rules are applied to the current project only. The IDE's global *File Types* apply if no project rule matches. Settings are stored in `.idea/advancedFileTypes.xml` and can be committed to your repo to share.

* **VS Code settings compatibility:** Reads `files.associations` from `.vscode/settings.json` and applies them as file type overrides. (Optional.)

  <!-- TODO: Cropped screenshot of the settings UI showing the VS Code integration checkbox and the pattern tester matching a `.vscode/settings.json` rule -->

* **GitHub Linguist compatibility:** Reads `linguist-language` information from `.gitattributes` and applies it as file type overrides. (Optional.)

  <!-- TODO: Cropped screenshot of the settings UI showing the gitattributes checkbox and the pattern tester matching a gitattributes rule -->

* **Free and open source**

## Installation

The Advanced File Types plugin should work with any JetBrains IDE version 2025.3 or later.

1. Open **Settings | Plugins**
2. Search for **Advanced File Types** in the marketplace
3. Click **Install**

Alternatively, download the latest release from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/XXXXX-advanced-file-types).

## Usage

Configure file type overrides in **Settings | Editor | Advanced File Types**.

* Use the `+` button to add a new rule, `-` to remove a rule, and arrow buttons to reorder rules. Select an existing rule and click the pencil button to edit it (or double click it in the list).
* Use the checkboxes to enable or disable VS Code and Git attributes matching. (Both are enabled by default.)
* The **Test Pattern Matching** section can be helpful to verify your rules work as expected.

The plugin applies matching rules in this order, stopping at the first match:

* Custom patterns from top to bottom
* `.vscode/settings.json` (if enabled)
* `.gitattributes` (if enabled)

If none of those match a particular file, the IDE's global rules from **Settings | Editor | File Types** apply.

### Glob patterns

Glob patterns follow [VS Code's glob syntax][vscode-glob-patterns] and support `*` and `?` filename wildcards, `**` to match zero or more directories, `{txt,html}` alternations, `[0-9.]` character ranges and `[!a-z]` negated ranges.

* Use `/` as the directory separator, even on Windows. (Backslashes in Windows paths are converted to `/` before matching.)
* Glob patterns **without `/`** match a *filename* anywhere in the project. (`*.txt` matches `readme.txt` and `docs/help/faq.txt` but not `docs/intro.txt.bak`.)
* Glob patterns **with `/`** anywhere match a *full path* starting at project root. (`docs/*.txt` matches `docs/intro.txt` but not `readme.txt` or `public/docs/faq.txt`. `/*.txt` or `./*.txt` matches `readme.txt` in the root directory but not `docs/intro.txt`.) Use `**` to match multiple path segments.
* Matching is **case-insensitive** on all operating systems.
* You cannot use backslash to escape characters in glob patterns. Use the `[...]` syntax instead: `[[]` to match a literal `[`, `[}]` to match `}`, etc.

[vscode-glob-patterns]: https://code.visualstudio.com/docs/editor/glob-patterns

### Regular expression patterns

Regex patterns are always evaluated against the *entire project-relative path,* with no leading `/` and with directory separators normalized to `/` on all platforms. A full string match is required (implicit `^...$` anchors).

Note that regex `.*` matches *any* characters, including `/` directory separators. Use `[^/]*` to match a single pathname component.

Regex syntax follows Java's [`java.util.regex.Pattern`][java-regex] with case-insensitive matching. See [JetBrains IntelliJ IDEA Regular Expressions Guide][jetbrains-regex] for details.

[java-regex]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html
[jetbrains-regex]: https://www.jetbrains.com/help/idea/regular-expressions.html

### VS Code settings and .gitattributes linguist-language support

VS Code file type associations are read from the `files.associations` section in `.vscode/settings.json`. (Patterns are treated as case-insensitive on all platforms, which doesn't strictly match VS Code. The plugin only checks the root `.vscode/settings.json` file. Multi-root workspaces are not currently supported.)

GitHub language IDs are read from `linguist-language` properties in `.gitattributes` files. (The plugin delegates git attribute parsing to `git`, so this only works in projects that are actually in a git repository.)

In both cases, the plugin tries to map the [VS Code language identifier][vscode-language-id] or [GitHub Linguist ID/alias][linguist-language-id] to a JetBrains file type, based on mappings built into the plugin or case-insensitive comparison to the IDE's supported file type names. (Please open an issue for incorrect or missing mappings.) If no IDE language can be determined, the plugin will ignore the rule and not override the file type.

If the mapped JetBrains file type is not supported by your IDE, the plugin will ignore the rule and not override the file type. The pattern tester will identify the file type as "unsupported in this IDE."

[vscode-language-id]: https://code.visualstudio.com/docs/languages/identifiers#_known-language-identifiers

[linguist-language-id]: https://github.com/github/linguist/blob/master/lib/linguist/languages.yml

## Why this plugin?

JetBrains [**File Types**][file-types-setting] associations are global to all projects, allow only filename patterns (not path matching), and don't support prioritization of rules. This is insufficient for many real-world use cases. See these JetBrains issues:

* [IDEABKL-6335](https://youtrack.jetbrains.com/issue/IDEABKL-6335/Assign-file-types-not-only-globally-but-project-wide-as-well): Assign file types not only globally but project-wide as well
* [IJPL-2316](https://youtrack.jetbrains.com/issue/IJPL-2316/Dynamic-File-Type-Patterns): Dynamic File Type Patterns
* [IJPL-52980](https://youtrack.jetbrains.com/issue/IJPL-52980/File-Type-Association-should-use-REGEX-not-just-and-for-pattern-matching): File Type Association should use REGEX not just * and ? for pattern matching
* [IJPL-3389](https://youtrack.jetbrains.com/issue/IJPL-3389/File-association-prioritization-for-multiple-matching-patterns): File association prioritization for multiple matching patterns
* [IJPL-258202](https://youtrack.jetbrains.com/issue/IJPL-258202/File-type-associations-Honor-linguist-language-overrides-from-.gitattributes): File type associations: Honor linguist-language overrides from .gitattributes

(JetBrains: please feel welcome to adopt, absorb, or otherwise obsolete this plugin!)

[file-types-setting]: https://www.jetbrains.com/help/idea/creating-and-registering-file-types.html#configure-associations-between-filename-patterns-and-file-types

## Development

To install the latest development build:
* Download `advanced-file-types-<version>.zip` from GitHub's [releases]
* Install it under **Settings | Plugins | ⚙️ | Install plugin from disk** (see JetBrains [managing plugins][install-plugin-from-disk] docs)

To build locally:
* Clone the repository
* Run `./gradlew check` to run the tests
* Run `./gradlew buildPlugin` to build the plugin
* Install the plugin from the generated `build/distributions/advanced-file-types-<version>.zip` file

[releases]: https://github.com/medmunds/advanced-file-types/releases
[install-plugin-from-disk]: https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk

## License

This project is licensed under the [MIT License](LICENSE).

### Third-Party Licenses

This project uses the following third-party software which is bundled in its distribution:

* [json5-java](https://github.com/marhali/json5-java):  [Apache License 2.0](https://github.com/marhali/json5-java/blob/main/LICENSE)
