package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Scraper {
    private const val TAG = "WBJEE_Scraper"
    private const val TARGET_URL = "https://wbjeeb.nic.in/wbjee/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Main fetch method. Uses Gemini if API key is provided and valid,
     * otherwise falls back to robust local JSoup parsing.
     */
    suspend fun fetchNotices(): List<Notice> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        
        val html = try {
            fetchHtml(TARGET_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch HTML from $TARGET_URL", e)
            return@withContext emptyList()
        }

        if (html.isBlank()) {
            return@withContext emptyList()
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                Log.d(TAG, "Attempting intelligent extraction with Gemini API...")
                val geminiNotices = fetchWithGemini(html, apiKey)
                if (geminiNotices.isNotEmpty()) {
                    Log.d(TAG, "Gemini extraction succeeded with ${geminiNotices.size} notices.")
                    return@withContext geminiNotices
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini extraction failed, falling back to local JSoup parser", e)
            }
        }

        Log.d(TAG, "Running robust local JSoup parser...")
        return@withContext parseLocalJsoup(html)
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unexpected HTTP response code: ${response.code}")
            }
            return response.body?.string() ?: ""
        }
    }

    /**
     * Local parsing fallback using JSoup.
     * Scrapes links, filters menus/nav, resolves relative paths, and categorizes.
     */
    private fun parseLocalJsoup(html: String): List<Notice> {
        val notices = mutableListOf<Notice>()
        val doc = Jsoup.parse(html)
        val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        // Target standard areas where NIC S3WAAS portals render news, updates, or lists of files
        val anchors = doc.select("a[href]")
        
        val seenUrls = mutableSetOf<String>()

        for (element in anchors) {
            val text = element.text().trim()
            var url = element.attr("href").trim()
            
            if (text.length < 10) continue
            if (url.startsWith("#") || url.startsWith("javascript:") || url.isBlank()) continue

            // Exclude common navigation or non-notice menu terms
            val lowerText = text.lowercase()
            if (lowerText.contains("skip to") || lowerText.contains("screen reader") ||
                lowerText.contains("contact us") || lowerText.contains("privacy policy") ||
                lowerText.contains("disclaimer") || lowerText.contains("terms of use") ||
                lowerText.contains("about board") || lowerText.contains("sitemap") ||
                lowerText.contains("help") || lowerText.contains("copyright") ||
                lowerText.contains("facebook") || lowerText.contains("twitter") ||
                lowerText.contains("instagram") || lowerText.contains("youtube")
            ) {
                continue
            }

            // Clean & resolve relative URLs
            if (url.startsWith("/")) {
                url = "https://wbjeeb.nic.in" + url
            } else if (!url.startsWith("http")) {
                url = "https://wbjeeb.nic.in/wbjee/" + url
            }

            if (!seenUrls.add(url)) continue

            // Determine category
            val category = when {
                lowerText.contains("counselling") || lowerText.contains("counseling") ||
                lowerText.contains("allotment") || lowerText.contains("seat") || 
                lowerText.contains("choice") || lowerText.contains("registration") -> "Counselling"
                
                lowerText.contains("schedule") || lowerText.contains("date") || 
                lowerText.contains("timetable") || lowerText.contains("calendar") -> "Schedule"
                
                lowerText.contains("rules") || lowerText.contains("guideline") || 
                lowerText.contains("bulletin") || lowerText.contains("syllabus") -> "Notice"
                
                else -> "General"
            }

            val id = generateMd5Hash(text + url)
            notices.add(
                Notice(
                    id = id,
                    title = text,
                    url = url,
                    date = todayStr,
                    category = category,
                    scannedAt = System.currentTimeMillis(),
                    isNew = true,
                    description = "Official document link extracted from WBJEE website."
                )
            )
        }

        // Return notices sorted (prioritizing potential PDFs/documents at the top)
        return notices.sortedWith(compareBy<Notice> { 
            if (it.url.lowercase().endsWith(".pdf")) 0 else 1 
        }.thenBy { it.category })
    }

    /**
     * Advanced intelligent extraction using Gemini API.
     */
    private suspend fun fetchWithGemini(html: String, apiKey: String): List<Notice> {
        val doc = Jsoup.parse(html)
        // Strip scripts, styles, and other heavy non-content nodes to fit within limits elegantly
        doc.select("script, style, head, header, footer, nav, iframe").remove()
        
        // Extract a condensed body text or structured text
        val cleanedText = doc.body().text().take(15000) // Keep text safe and manageable

        val prompt = """
            You are a specialized webpage parser for Indian examination portals. Analyze the following webpage text from the West Bengal Joint Entrance Examinations Board (WBJEE). Extract all active notices, counselling updates, schedules, bulletins, and announcements.
            
            Webpage Text:
            $cleanedText
            
            For each item, extract:
            1. title (precise, clean official title)
            2. url (resolve absolute URL if mentioned or construct using 'https://wbjeeb.nic.in/wbjee/'; if relative path like /document/abc.pdf, make it 'https://wbjeeb.nic.in/document/abc.pdf')
            3. date (if a date is mentioned nearby, use DD-MM-YYYY format, else use the current date)
            4. category (one of: 'Notice', 'Counselling', 'Schedule', 'General')
            5. description (a short summary of the notice)

            Return ONLY a valid JSON array of objects. Do not wrap it in markdown block. The JSON format should match:
            [
              {
                "title": "...",
                "url": "...",
                "date": "...",
                "category": "...",
                "description": "..."
              }
            ]
        """.trimIndent()

        // Make direct HTTP request to Gemini REST API
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            // We want json output structure
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        val requestBody = jsonPayload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Gemini HTTP failure: ${response.code}")
                }
                val body = response.body?.string() ?: ""
                val jsonResponse = JSONObject(body)
                val candidates = jsonResponse.getJSONArray("candidates")
                val textResponse = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val cleanJson = textResponse.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonArray = JSONArray(cleanJson)
                val resultList = mutableListOf<Notice>()
                val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "").trim()
                    val url = obj.optString("url", "").trim()
                    val date = obj.optString("date", todayStr).trim()
                    val category = obj.optString("category", "General").trim()
                    val description = obj.optString("description", "").trim()

                    if (title.isNotEmpty()) {
                        val finalUrl = when {
                            url.startsWith("/") -> "https://wbjeeb.nic.in" + url
                            url.startsWith("http") -> url
                            else -> "https://wbjeeb.nic.in/wbjee/" + url
                        }
                        val id = generateMd5Hash(title + finalUrl)
                        resultList.add(
                            Notice(
                                id = id,
                                title = title,
                                url = finalUrl,
                                date = date,
                                category = category,
                                scannedAt = System.currentTimeMillis(),
                                isNew = true,
                                description = description.ifEmpty { "Extracted intelligently by Gemini AI." }
                            )
                        )
                    }
                }
                resultList
            }
        }
    }

    private fun generateMd5Hash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
