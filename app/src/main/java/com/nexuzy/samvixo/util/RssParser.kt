package com.nexuzy.samvixo.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class RssHeadline(val region: String, val flag: String, val title: String)

object RssParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Fetch up to [maxPerFeed] titles from a single RSS URL. Never throws. */
    fun fetch(url: String, maxPerFeed: Int = 3): List<String> {
        return try {
            val req  = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().body?.string() ?: return emptyList()
            parseRss(body, maxPerFeed)
        } catch (_: Exception) { emptyList() }
    }

    private fun parseRss(xml: String, max: Int): List<String> {
        val titles  = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser  = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var inItem   = false
        var inTitle  = false
        var event    = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < max) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item"  -> inItem  = true
                        "title" -> if (inItem) inTitle = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) titles += parser.text.trim()
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> inTitle = false
                        "item"  -> inItem  = false
                    }
                }
            }
            event = parser.next()
        }
        return titles
    }
}
