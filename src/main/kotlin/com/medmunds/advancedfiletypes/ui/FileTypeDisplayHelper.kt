package com.medmunds.advancedfiletypes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.medmunds.advancedfiletypes.AdvancedFileTypesBundle
import javax.swing.Icon

/**
 * Describes the origin of a file type name when the IDE cannot resolve it to a concrete FileType.
 */
enum class UnknownFileTypeOrigin {
    /** The name came from a user rule, VS Code map, or Linguist map — it targets an IntelliJ type not available in this IDE. */
    NO_IDE_SUPPORT,

    /** The name is a raw VS Code language identifier that could not be mapped. */
    VS_CODE_IDENTIFIER,

    /** The name is a raw Linguist language or alias that could not be mapped. */
    LINGUIST_IDENTIFIER
}

/**
 * Helper for displaying file type names and icons consistently across the plugin UI.
 *
 * Rules:
 * - Only include the description if it differs from the display name.
 * - Show unknown file types with an unknown icon and an appropriate qualifier.
 */
object FileTypeDisplayHelper {

    /**
     * Returns the display text for a resolved [FileType].
     * Format: "Name" if description is blank or equals the name; otherwise "Name (Description)".
     */
    fun displayText(fileType: FileType): String {
        val name = fileType.displayName
        val desc = fileType.description
        if (desc.isBlank() || desc.equals(name, ignoreCase = true)) {
            return name
        }
        return AdvancedFileTypesBundle.message("fileType.display.withDescription", name, desc)
    }

    /**
     * Returns the display text for an unknown file type given its name and origin.
     */
    fun displayTextForUnknown(name: String, origin: UnknownFileTypeOrigin): String {
        return when (origin) {
            UnknownFileTypeOrigin.NO_IDE_SUPPORT ->
                AdvancedFileTypesBundle.message("fileType.unknown.noIdeSupport", name)
            UnknownFileTypeOrigin.VS_CODE_IDENTIFIER ->
                AdvancedFileTypesBundle.message("fileType.unknown.vsCode", name)
            UnknownFileTypeOrigin.LINGUIST_IDENTIFIER ->
                AdvancedFileTypesBundle.message("fileType.unknown.linguist", name)
        }
    }

    /**
     * Returns the icon for a resolved [FileType].
     */
    fun iconFor(fileType: FileType): Icon? = fileType.icon

    /**
     * Returns the unknown file type icon (question-mark file icon).
     */
    fun iconForUnknown(): Icon? = UnknownFileType.INSTANCE.icon

    /**
     * Returns the icon for a JetBrains file type that is not available in this IDE.
     */
    fun iconForUnsupported(): Icon = AllIcons.General.Warning

    /**
     * Resolves a file type name to a [FileType] and returns the display text.
     * If the type is not found, returns the unknown display text with the given origin.
     */
    fun displayTextForName(typeName: String, origin: UnknownFileTypeOrigin? = null): String {
        val fileType = FileTypeManager.getInstance().findFileTypeByName(typeName)
        return if (fileType != null) {
            displayText(fileType)
        } else if (origin != null) {
            displayTextForUnknown(typeName, origin)
        } else {
            typeName
        }
    }

    /**
     * Resolves a file type name to a [FileType] and returns the icon.
     * If the type is not found, returns the unsupported file type icon.
     */
    fun iconForName(typeName: String): Icon {
        val fileType = FileTypeManager.getInstance().findFileTypeByName(typeName)
        return fileType?.icon ?: iconForUnsupported()
    }
}
