package com.example.adblock

import android.net.Uri
import java.util.Locale

class AdBlockRules private constructor(
    private val exceptionRules: List<Rule>,
    private val blockRules: List<Rule>
) {

    fun isBlocked(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val host = hostOf(trimmed) ?: return false
        if (exceptionRules.any { it.matches(trimmed, host) }) return false
        return blockRules.any { it.matches(trimmed, host) }
    }

    companion object {

        fun parse(lines: Iterable<String>): AdBlockRules {
            val exceptions = ArrayList<Rule>()
            val blocks = ArrayList<Rule>()
            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("!") || line.startsWith("#")) continue
                if (line.startsWith("[Adblock")) continue
                if (line.startsWith("@@")) {
                    parseRule(line.removePrefix("@@"), exceptions)
                } else {
                    parseRule(line, blocks)
                }
            }
            return AdBlockRules(exceptions, blocks)
        }

        private fun parseRule(original: String, into: MutableList<Rule>) {
            val rule = stripOptions(original)
            val regex = toRegex(rule)
            if (regex != null) {
                into += Rule(hostSuffix = null, pathPrefix = null, contains = null, regex = regex)
                return
            }
            if (rule.startsWith("||")) {
                val rest = rule.removePrefix("||")
                val slash = rest.indexOf('/')
                val suffix = if (slash >= 0) rest.substring(0, slash) else rest
                val path = if (slash >= 0) rest.substring(slash) else null
                into += Rule(
                    hostSuffix = suffix.trimEnd('^', '*').lowercase(Locale.ROOT),
                    pathPrefix = path,
                    contains = null,
                    regex = null
                )
                return
            }
            if (rule.startsWith("|")) {
                val prefix = rule.removePrefix("|").removeSuffix("|")
                into += Rule(hostSuffix = null, pathPrefix = prefix, contains = null, regex = null)
                return
            }
            if (rule.startsWith("/") && rule.endsWith("/")) {
                into += Rule(hostSuffix = null, pathPrefix = null, contains = null, regex = runCatching { Regex(rule.substring(1, rule.length - 1)) }.getOrNull())
                return
            }
            into += Rule(
                hostSuffix = null,
                pathPrefix = null,
                contains = rule.trimEnd('^', '*').lowercase(Locale.ROOT),
                regex = null
            )
        }

        private fun stripOptions(rule: String): String {
            val index = rule.indexOf('$')
            return if (index > 0) rule.substring(0, index) else rule
        }

        private fun toRegex(rule: String): Regex? {
            if (!rule.startsWith("/") || !rule.endsWith("/")) return null
            val body = rule.substring(1, rule.length - 1)
            return runCatching { Regex(body, setOf(RegexOption.IGNORE_CASE)) }.getOrNull()
        }

        private fun hostOf(url: String): String? {
            if (url.startsWith("data:") || url.startsWith("about:")) return null
            val host = runCatching { Uri.parse(if (url.startsWith("//")) "http:$url" else url).host }.getOrNull()
            return host?.trim('.')?.lowercase(Locale.ROOT)
        }
    }

    private class Rule(
        val hostSuffix: String?,
        val pathPrefix: String?,
        val contains: String?,
        val regex: Regex?
    ) {
        fun matches(url: String, host: String): Boolean {
            if (hostSuffix != null) {
                if (!host.endsWith(hostSuffix, ignoreCase = true)) return false
                if (pathPrefix != null && !url.startsWith(pathPrefix)) return false
                return true
            }
            if (pathPrefix != null) return url.startsWith(pathPrefix)
            if (contains != null) return url.contains(contains, ignoreCase = true)
            if (regex != null) return regex.containsMatchIn(url)
            return false
        }
    }
}