package com.moonlib.cosmos.data.settings

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * AI 请求认证头构造器。
 *
 * 职责单一：根据服务商生成 HTTP Authorization header，Vertex 会用服务账号 JSON 换取 OAuth token。
 */
object AiAuthorizationHeader {
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    private var cachedClientEmail: String? = null
    private var cachedToken: String? = null
    private var cachedExpiresAtMillis: Long = 0L

    fun create(serviceType: AiServiceType, apiKey: String): String {
        return if (serviceType == AiServiceType.VERTEX) {
            "Bearer ${getVertexAccessToken(apiKey)}"
        } else {
            "Bearer $apiKey"
        }
    }

    @Synchronized
    private fun getVertexAccessToken(serviceAccountJson: String): String {
        val json = JSONObject(serviceAccountJson)
        val clientEmail = json.getString("client_email")
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && cachedClientEmail == clientEmail && now < cachedExpiresAtMillis - 60_000L) {
            return cached
        }

        val assertion = buildJwtAssertion(json, now / 1000L)
        val body = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion" to assertion
        ).joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode != 200) {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Vertex OAuth 认证失败 HTTP ${conn.responseCode}: ${errorText.take(160)}")
        }

        val response = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val token = response.getString("access_token")
        val expiresIn = response.optLong("expires_in", 3600L)
        cachedClientEmail = clientEmail
        cachedToken = token
        cachedExpiresAtMillis = now + expiresIn * 1000L
        return token
    }

    private fun buildJwtAssertion(json: JSONObject, issuedAtSeconds: Long): String {
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        val claim = JSONObject().apply {
            put("iss", json.getString("client_email"))
            put("scope", SCOPE)
            put("aud", TOKEN_URL)
            put("iat", issuedAtSeconds)
            put("exp", issuedAtSeconds + 3600L)
        }
        val unsigned = "${base64Url(header.toString().toByteArray(Charsets.UTF_8))}.${base64Url(claim.toString().toByteArray(Charsets.UTF_8))}"
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(parsePrivateKey(json.getString("private_key")))
        signature.update(unsigned.toByteArray(Charsets.UTF_8))
        return "$unsigned.${base64Url(signature.sign())}"
    }

    private fun parsePrivateKey(privateKeyPem: String): java.security.PrivateKey {
        val normalized = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(normalized, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }
}
