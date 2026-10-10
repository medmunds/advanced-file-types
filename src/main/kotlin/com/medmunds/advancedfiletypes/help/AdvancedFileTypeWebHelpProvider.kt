package com.medmunds.advancedfiletypes.help

import com.intellij.openapi.help.WebHelpProvider

class AdvancedFileTypeWebHelpProvider : WebHelpProvider() {

    override fun getHelpTopicPrefix(): String = HELP_TOPIC_PREFIX

    override fun getHelpPageUrl(helpTopicId: String): String? {
        return if (helpTopicId.startsWith(HELP_TOPIC_PREFIX)) {
            README_URL
        } else {
            null
        }
    }

    companion object {
        const val HELP_TOPIC_PREFIX = "com.medmunds.advancedfiletypes"
        const val HELP_TOPIC = "com.medmunds.advancedfiletypes.settings"
        const val README_URL = "https://github.com/medmunds/advanced-file-types#readme"
    }
}
