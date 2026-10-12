package com.inodra.movesui.mvr

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class MvrPackage(val name: String, val description: String?, val homepage: String?, val packageAddress: String?, val version: Int?,
                      val repository: String?, val path: String?, val tag: String?) {
    val org get() = name.substringBefore('/').removePrefix("@")
    val pkg get() = name.substringAfter('/')
}

/** Thin client for the Move Registry HTTP API with an in-memory cache. Never call on the EDT. */
object MvrClient {
    private const val MAINNET = "https://mainnet.mvr.mystenlabs.com/v1"
    private const val TESTNET = "https://testnet.mvr.mystenlabs.com/v1"
    private val detailCache = ConcurrentHashMap<String, MvrPackage?>()
    private val searchCache = ConcurrentHashMap<String, List<MvrPackage>>()

    val NAME_REGEX = Regex("""@[a-z0-9-]+/[a-z0-9_-]+(/\d+)?""")

    fun search(query: String, limit: Int = 30, testnet: Boolean = false): List<MvrPackage> = searchCache.getOrPut("$testnet:$query:$limit") {
        runCatching {
            val url = "${base(testnet)}/names?search=${URLEncoder.encode(query, "UTF-8")}&limit=$limit"
            val json = JsonParser.parseString(HttpRequests.request(url).connectTimeout(4000).readTimeout(6000).readString()).asJsonObject
            json.getAsJsonArray("data").map { parsePackage(it.asJsonObject) }
        }.getOrDefault(emptyList())
    }

    fun details(name: String, testnet: Boolean = false): MvrPackage? {
        val key = "$testnet:$name"
        if (detailCache.containsKey(key)) return detailCache[key]
        val result = runCatching {
            val url = "${base(testnet)}/names/${name.substringBefore('/', name)}/${name.substringAfter('/')}"
            val json = JsonParser.parseString(HttpRequests.request(url).connectTimeout(4000).readTimeout(6000).readString()).asJsonObject
            if (json.has("message") && !json.has("name")) null else parsePackage(json)
        }.getOrNull()
        detailCache[key] = result
        return result
    }

    private fun base(testnet: Boolean) = if (testnet) TESTNET else MAINNET

    private fun parsePackage(o: JsonObject): MvrPackage {
        val meta = o.getAsJsonObject("metadata")
        val git = o["git_info"]?.takeIf { it.isJsonObject }?.asJsonObject
        fun JsonObject?.str(k: String) = this?.get(k)?.takeIf { !it.isJsonNull }?.asString
        return MvrPackage(
            name = o.str("name") ?: "",
            description = meta.str("description"),
            homepage = meta.str("homepage_url"),
            packageAddress = o.str("package_address"),
            version = o["version"]?.takeIf { it.isJsonPrimitive }?.asInt,
            repository = git.str("repository_url") ?: git.str("repository"),
            path = git.str("path"),
            tag = git.str("tag"),
        )
    }
}
