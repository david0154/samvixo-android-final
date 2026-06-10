package com.nexuzy.samvixo.data.remote

// RSS is fetched via OkHttp directly (no Retrofit needed for XML parsing)
// Sources:
//   India  → https://feeds.feedburner.com/ndtvnews-top-stories
//   US     → https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml
//   Europe → https://feeds.bbci.co.uk/news/world/europe/rss.xml

object RssSources {
    val feeds = listOf(
        RssFeed("India",  "🇮🇳", "https://feeds.feedburner.com/ndtvnews-top-stories"),
        RssFeed("US",     "🇺🇸", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        RssFeed("Europe", "🇪🇺", "https://feeds.bbci.co.uk/news/world/europe/rss.xml")
    )
}

data class RssFeed(val region: String, val flag: String, val url: String)
