package com.xtsream.app

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

interface NetworkBridge {
    fun get(url: String, headersJson: String): String
    fun post(url: String, headersJson: String, body: String): String
}

class NetworkBridgeImpl : NetworkBridge {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun get(url: String, headersJson: String): String {
        return executeCall(url, headersJson, null)
    }

    override fun post(url: String, headersJson: String, body: String): String {
        return executeCall(url, headersJson, body)
    }

    private fun executeCall(url: String, headersJson: String, body: String?): String {
        val requestBuilder = Request.Builder().url(url)
        try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val headers: Map<String, String> = gson.fromJson(headersJson, type)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        } catch (e: Exception) {}

        if (body != null) {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            requestBuilder.post(body.toRequestBody(mediaType))
        }

        val request = requestBuilder.build()

        while (true) {
            try {
                val response = client.newCall(request).execute()
                val resMap = mutableMapOf<String, Any>()
                resMap["code"] = response.code
                val resHeaders = mutableMapOf<String, String>()
                response.headers.forEach { pair -> resHeaders[pair.first.lowercase()] = pair.second }
                resMap["headers"] = resHeaders
                resMap["body"] = response.body?.string() ?: ""
                return gson.toJson(resMap)
            } catch (e: IOException) {
                continue
            } catch (e: Exception) {
                return "{ \"code\": 500, \"error\": \"${e.message}\" }"
            }
        }
    }
}

interface CryptoBridge {
    fun md5Hex(data: String): String
    fun sha256Hex(data: String): String
    fun hmacMd5Base64(data: String, keyBase64: String): String
    fun hmacSha256Hex(dataHex: String, keyHex: String): String
    fun hkdfSha256(ikmHex: String, saltHex: String, infoHex: String, length: Int): String
    fun aesCbcDecrypt(encryptedBase64: String, keyHex: String, ivHex: String): String
    fun aesCbcEncrypt(data: String, keyHex: String, ivHex: String): String
    fun aesGcmDecrypt(encryptedHex: String, keyHex: String, ivHex: String): String
    fun generateRandomHex(length: Int): String
}

class CryptoBridgeImpl : CryptoBridge {
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun md5Hex(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        return byteArrayToHexString(md.digest(data.toByteArray(Charsets.UTF_8)))
    }

    override fun sha256Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return byteArrayToHexString(md.digest(data.toByteArray(Charsets.UTF_8)))
    }

    override fun hmacMd5Base64(data: String, keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(keyBytes, "HmacMD5"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    override fun hmacSha256Hex(dataHex: String, keyHex: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hexStringToByteArray(keyHex), "HmacSHA256"))
        return byteArrayToHexString(mac.doFinal(hexStringToByteArray(dataHex)))
    }

    override fun hkdfSha256(ikmHex: String, saltHex: String, infoHex: String, length: Int): String {
        val mac = Mac.getInstance("HmacSHA256")
        val salt = if (saltHex.isEmpty()) ByteArray(32) else hexStringToByteArray(saltHex)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(hexStringToByteArray(ikmHex))
        
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val info = hexStringToByteArray(infoHex)
        var result = ByteArray(0)
        var t = ByteArray(0)
        val iterations = ceil(length.toDouble() / 32.0).toInt()
        
        for (i in 1..iterations) {
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            result += t
        }
        return byteArrayToHexString(result.copyOfRange(0, length))
    }

    override fun aesCbcDecrypt(encryptedBase64: String, keyHex: String, ivHex: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hexStringToByteArray(keyHex), "AES"), IvParameterSpec(hexStringToByteArray(ivHex)))
            val decrypted = cipher.doFinal(Base64.decode(encryptedBase64, Base64.DEFAULT))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    override fun aesCbcEncrypt(data: String, keyHex: String, ivHex: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(hexStringToByteArray(keyHex), "AES"), IvParameterSpec(hexStringToByteArray(ivHex)))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    override fun aesGcmDecrypt(encryptedHex: String, keyHex: String, ivHex: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(hexStringToByteArray(keyHex), "AES"), GCMParameterSpec(128, hexStringToByteArray(ivHex)))
            val decrypted = cipher.doFinal(hexStringToByteArray(encryptedHex))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    override fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return byteArrayToHexString(bytes)
    }
}

interface Base64Bridge {
    fun encode(data: String): String
    fun decode(base64Str: String): String
    fun customEncode(dataHex: String, sourceChars: String, targetChars: String): String
}

class Base64BridgeImpl : Base64Bridge {
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    override fun encode(data: String): String {
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    override fun decode(base64Str: String): String {
        return String(Base64.decode(base64Str, Base64.DEFAULT), Charsets.UTF_8)
    }

    override fun customEncode(dataHex: String, sourceChars: String, targetChars: String): String {
        val inputBytes = hexStringToByteArray(dataHex)
        val standardBase64 = Base64.encodeToString(inputBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val sb = java.lang.StringBuilder()
        for (char in standardBase64) {
            val idx = sourceChars.indexOf(char)
            if (idx != -1 && idx < targetChars.length) {
                sb.append(targetChars[idx])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }
}
