package com.kakao.actionbase.server.filter

import com.kakao.actionbase.server.filter.model.ResponseMetaContext

import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.http.HttpHeaders

class ResponseMetaFactory(
    private val gitProperties: GitProperties?,
    private val buildProperties: BuildProperties,
) {
    companion object {
        private const val UNKNOWN: String = "unknown"
    }

    fun createResponseMeta(headers: HttpHeaders): Map<String, String> = systemMeta + extractHeaderMeta(headers)

    private val systemMeta =
        mapOf(
            ResponseMetaContext.VERSION.metaKey to getVersion(),
            ResponseMetaContext.HOSTNAME.metaKey to getHostname(),
        )

    private fun extractHeaderMeta(headers: HttpHeaders): Map<String, String> =
        ResponseMetaContext.headerEntries
            .mapNotNull { entry ->
                headers[entry.contextKey]?.firstOrNull()?.let {
                    Pair(entry.metaKey, it)
                }
            }.toMap()

    private fun getHostname(): String =
        try {
            java.net.InetAddress
                .getLocalHost()
                ?.hostName ?: UNKNOWN
        } catch (e: Exception) {
            UNKNOWN
        }

    private fun getVersion(): String {
        val buildVersion = buildProperties.version ?: return UNKNOWN

        if (!buildVersion.uppercase().endsWith("-SNAPSHOT")) {
            // Release version handling
            return "v$buildVersion"
        }

        // SNAPSHOT version handling
        val baseVersion =
            listOf(
                buildVersion,
                gitProperties?.shortCommitId ?: UNKNOWN,
                gitProperties?.branch ?: UNKNOWN,
            ).joinToString(":")

        // Check dirty state
        val isDirty = gitProperties?.get("dirty")?.toBooleanStrictOrNull() ?: false
        return if (isDirty) "$baseVersion(dirty)" else baseVersion
    }
}
