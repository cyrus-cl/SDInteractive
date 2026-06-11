package com.sdinteractive.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

internal data class ArkConfig(
    val apiKey: String,
    val model: String,
    val endpointUrl: String
) {
    companion object {
        fun load(
            environment: Map<String, String> = System.getenv(),
            fileValues: Map<String, String> = readDotEnvValues()
        ): ArkConfig? {
            val apiKey = firstConfigured(
                environment,
                fileValues,
                "ARK_API_KEY",
                "DOUBAO_API_KEY",
                "VOLCENGINE_API_KEY",
                "APIKEY"
            )
            val model = firstConfigured(
                environment,
                fileValues,
                "ARK_MODEL",
                "DOUBAO_MODEL",
                "ARK_ENDPOINT_ID",
                "EP",
                "MODEL"
            )
            if (apiKey == null || model == null) return null
            val configuredUrl = firstConfigured(
                environment,
                fileValues,
                "ARK_BASE_URL",
                "DOUBAO_BASE_URL",
                "VOLCENGINE_BASE_URL"
            ) ?: DEFAULT_BASE_URL
            return ArkConfig(
                apiKey = apiKey,
                model = model,
                endpointUrl = configuredUrl.toChatCompletionsUrl()
            )
        }
    }
}

internal interface AiTextClient {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        temperature: Double = 0.2,
        maxTokens: Int = 2_000
    ): String
}

internal class ArkClient(
    private val config: ArkConfig,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) : AiTextClient {
    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?,
        imageMimeType: String?,
        temperature: Double,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", config.model)
            put(
                "messages",
                buildJsonArray {
                    add(message("system", JsonPrimitive(systemPrompt)))
                    add(
                        message(
                            "user",
                            arkUserContent(
                                prompt = userPrompt,
                                imageBase64 = imageBase64,
                                imageMimeType = imageMimeType
                            )
                        )
                    )
                }
            )
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }
        val request = HttpRequest.newBuilder(URI.create(config.endpointUrl))
            .timeout(Duration.ofSeconds(ARK_REQUEST_TIMEOUT_SECONDS))
            .header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("ark_http_${response.statusCode()}_${response.body().take(240)}")
        }
        extractArkContent(response.body())
    }
}

private fun message(role: String, content: JsonElement): JsonObject = buildJsonObject {
    put("role", role)
    put("content", content)
}

private fun arkUserContent(
    prompt: String,
    imageBase64: String?,
    imageMimeType: String?
): JsonElement {
    val frame = imageBase64?.trim()?.takeIf { it.isNotBlank() }
        ?: return JsonPrimitive(prompt)
    val mimeType = imageMimeType
        ?.trim()
        ?.takeIf { it.matches(Regex("image/[A-Za-z0-9.+-]+")) }
        ?: "image/jpeg"
    return buildJsonArray {
        add(
            buildJsonObject {
                put("type", "text")
                put("text", prompt)
            }
        )
        add(
            buildJsonObject {
                put("type", "image_url")
                put(
                    "image_url",
                    buildJsonObject {
                        put("url", "data:$mimeType;base64,$frame")
                    }
                )
            }
        )
    }
}

internal fun arkUserMessageContentJson(
    prompt: String,
    request: AiPersonInsightRequest
): String = arkUserContent(
    prompt = prompt,
    imageBase64 = request.frameImageBase64,
    imageMimeType = request.frameMimeType
).toString()

internal fun extractArkContent(responseBody: String): String {
    val content = Json.parseToJsonElement(responseBody)
        .jsonObject["choices"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("message")
        ?.jsonObject
        ?.get("content")
        ?: throw IllegalStateException("ark_missing_content")
    val text = when (content) {
        is JsonPrimitive -> content.contentOrNull
        is JsonArray -> content
            .mapNotNull { item ->
                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }
            .joinToString("\n")
        else -> null
    }?.trim()
    return text?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("ark_empty_content")
}

private fun firstConfigured(
    environment: Map<String, String>,
    fileValues: Map<String, String>,
    vararg names: String
): String? = names.firstNotNullOfOrNull { name ->
    environment[name]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fileValues[name]?.trim()?.takeIf { it.isNotBlank() }
}

private fun String.toChatCompletionsUrl(): String {
    val normalized = trim().trimEnd('/')
    return if (normalized.endsWith("/chat/completions")) {
        normalized
    } else {
        "$normalized/chat/completions"
    }
}

private fun readDotEnvValues(): Map<String, String> {
    val paths = listOf(Paths.get(".env"), Paths.get("..", ".env"))
    val file = paths.firstOrNull { Files.exists(it) && Files.isRegularFile(it) } ?: return emptyMap()
    val values = linkedMapOf<String, String>()
    Files.readAllLines(file).forEach { raw ->
        val line = raw.trim()
        if (line.isBlank() || line.startsWith("#")) return@forEach
        val separatorIndex = listOf(line.indexOf('='), line.indexOf(':'))
            .filter { it >= 0 }
            .minOrNull()
        if (separatorIndex != null) {
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim().trim('"', '\'')
            if (key.isNotBlank() && value.isNotBlank()) values[key] = value
        } else if ("MODEL" !in values) {
            values["MODEL"] = line
        }
    }
    return values
}

private const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
internal const val ARK_REQUEST_TIMEOUT_SECONDS = 45L
