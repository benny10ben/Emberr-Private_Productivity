package com.emberr.presentation.home.overview.bookmarks

const val OtherBookmarksCategory = "Others"

private val SocialDomains = mapOf(
    "instagram" to "Instagram",
    "instagr" to "Instagram",
    "facebook" to "Facebook",
    "fb" to "Facebook",
    "x" to "X",
    "twitter" to "X",
    "threads" to "Threads",
    "tiktok" to "TikTok",
    "snapchat" to "Snapchat",
    "reddit" to "Reddit",
    "redd" to "Reddit",
    "pinterest" to "Pinterest",
    "pin" to "Pinterest",
    "linkedin" to "LinkedIn",
    "lnkd" to "LinkedIn",
    "tumblr" to "Tumblr",
    "bsky" to "Bluesky",
    "mastodon" to "Mastodon",
    "discord" to "Discord",
    "discordapp" to "Discord",
    "telegram" to "Telegram",
    "whatsapp" to "WhatsApp",
    "wa" to "WhatsApp",
    "quora" to "Quora",
    "nextdoor" to "Nextdoor",
    "weibo" to "Weibo",
    "vk" to "VK"
)

private val VideoDomains = mapOf(
    "youtube" to "YouTube",
    "youtu" to "YouTube",
    "youtube-nocookie" to "YouTube",
    "vimeo" to "Vimeo",
    "twitch" to "Twitch",
    "dailymotion" to "Dailymotion",
    "rumble" to "Rumble",
    "netflix" to "Netflix",
    "primevideo" to "Prime Video",
    "hulu" to "Hulu",
    "disneyplus" to "Disney+",
    "hotstar" to "Hotstar",
    "loom" to "Loom",
    "ted" to "TED"
)

private val MusicDomains = mapOf(
    "spotify" to "Spotify",
    "spoti" to "Spotify",
    "soundcloud" to "SoundCloud",
    "snd" to "SoundCloud",
    "bandcamp" to "Bandcamp",
    "deezer" to "Deezer",
    "tidal" to "Tidal",
    "last" to "Last.fm",
    "genius" to "Genius",
    "audiomack" to "Audiomack"
)

private val DeveloperDomains = mapOf(
    "github" to "GitHub",
    "git" to "GitHub",
    "gitlab" to "GitLab",
    "bitbucket" to "Bitbucket",
    "stackoverflow" to "Stack Overflow",
    "stackexchange" to "Stack Exchange",
    "ycombinator" to "Hacker News",
    "npmjs" to "npm",
    "pypi" to "PyPI",
    "maven" to "Maven",
    "docker" to "Docker",
    "kaggle" to "Kaggle",
    "huggingface" to "Hugging Face",
    "codepen" to "CodePen",
    "codesandbox" to "CodeSandbox",
    "replit" to "Replit",
    "vercel" to "Vercel",
    "netlify" to "Netlify",
    "heroku" to "Heroku",
    "cloudflare" to "Cloudflare",
    "digitalocean" to "DigitalOcean",
    "jetbrains" to "JetBrains",
    "kotlinlang" to "Kotlin",
    "android" to "Android",
    "mozilla" to "MDN",
    "leetcode" to "LeetCode",
    "hackerrank" to "HackerRank",
    "codeforces" to "Codeforces",
    "dev" to "DEV Community",
    "freecodecamp" to "freeCodeCamp",
    "w3schools" to "W3Schools",
    "arxiv" to "arXiv"
)

private val ReadingDomains = mapOf(
    "medium" to "Medium",
    "substack" to "Substack",
    "wikipedia" to "Wikipedia",
    "wikimedia" to "Wikipedia",
    "goodreads" to "Goodreads",
    "blogspot" to "Blogger",
    "blogger" to "Blogger",
    "wordpress" to "WordPress",
    "ghost" to "Ghost",
    "notion" to "Notion",
    "obsidian" to "Obsidian",
    "scribd" to "Scribd",
    "archive" to "Internet Archive",
    "gutenberg" to "Project Gutenberg",
    "researchgate" to "ResearchGate",
    "jstor" to "JSTOR",
    "sciencedirect" to "ScienceDirect",
    "springer" to "Springer",
    "nature" to "Nature",
    "nih" to "PubMed"
)

private val NewsDomains = mapOf(
    "nytimes" to "New York Times",
    "nyti" to "New York Times",
    "bbc" to "BBC",
    "cnn" to "CNN",
    "theguardian" to "The Guardian",
    "reuters" to "Reuters",
    "bloomberg" to "Bloomberg",
    "wsj" to "Wall Street Journal",
    "washingtonpost" to "Washington Post",
    "forbes" to "Forbes",
    "economist" to "The Economist",
    "theverge" to "The Verge",
    "techcrunch" to "TechCrunch",
    "arstechnica" to "Ars Technica",
    "wired" to "Wired",
    "engadget" to "Engadget",
    "cnet" to "CNET",
    "aljazeera" to "Al Jazeera",
    "npr" to "NPR",
    "apnews" to "AP News",
    "ndtv" to "NDTV",
    "thehindu" to "The Hindu",
    "indiatimes" to "Times of India",
    "timesofindia" to "Times of India",
    "hindustantimes" to "Hindustan Times"
)

private val ShoppingDomains = mapOf(
    "amazon" to "Amazon",
    "amzn" to "Amazon",
    "ebay" to "eBay",
    "etsy" to "Etsy",
    "aliexpress" to "AliExpress",
    "alibaba" to "Alibaba",
    "flipkart" to "Flipkart",
    "myntra" to "Myntra",
    "walmart" to "Walmart",
    "target" to "Target",
    "bestbuy" to "Best Buy",
    "ikea" to "IKEA",
    "shein" to "Shein",
    "temu" to "Temu",
    "shopify" to "Shopify",
    "nike" to "Nike",
    "zara" to "Zara"
)

private val ProductivityDomains = mapOf(
    "google" to "Google",
    "goo" to "Google",
    "microsoft" to "Microsoft",
    "office" to "Microsoft",
    "sharepoint" to "Microsoft",
    "apple" to "Apple",
    "icloud" to "Apple",
    "dropbox" to "Dropbox",
    "figma" to "Figma",
    "canva" to "Canva",
    "trello" to "Trello",
    "asana" to "Asana",
    "slack" to "Slack",
    "zoom" to "Zoom",
    "miro" to "Miro",
    "airtable" to "Airtable",
    "clickup" to "ClickUp",
    "atlassian" to "Atlassian",
    "calendly" to "Calendly",
    "typeform" to "Typeform",
    "linear" to "Linear"
)

private val LearningDomains = mapOf(
    "coursera" to "Coursera",
    "udemy" to "Udemy",
    "edx" to "edX",
    "khanacademy" to "Khan Academy",
    "udacity" to "Udacity",
    "skillshare" to "Skillshare",
    "duolingo" to "Duolingo",
    "brilliant" to "Brilliant",
    "pluralsight" to "Pluralsight"
)

private val AiDomains = mapOf(
    "openai" to "ChatGPT",
    "chatgpt" to "ChatGPT",
    "anthropic" to "Claude",
    "claude" to "Claude",
    "perplexity" to "Perplexity",
    "midjourney" to "Midjourney",
    "runwayml" to "Runway",
    "elevenlabs" to "ElevenLabs"
)

private val TravelDomains = mapOf(
    "airbnb" to "Airbnb",
    "booking" to "Booking.com",
    "tripadvisor" to "Tripadvisor",
    "expedia" to "Expedia",
    "uber" to "Uber",
    "yelp" to "Yelp",
    "openstreetmap" to "OpenStreetMap",
    "zomato" to "Zomato",
    "swiggy" to "Swiggy"
)

private val ImageDomains = mapOf(
    "imgur" to "Imgur",
    "flickr" to "Flickr",
    "unsplash" to "Unsplash",
    "pexels" to "Pexels",
    "behance" to "Behance",
    "dribbble" to "Dribbble",
    "deviantart" to "DeviantArt",
    "artstation" to "ArtStation",
    "giphy" to "Giphy",
    "500px" to "500px"
)

private val FinanceDomains = mapOf(
    "paypal" to "PayPal",
    "stripe" to "Stripe",
    "coinbase" to "Coinbase",
    "binance" to "Binance",
    "tradingview" to "TradingView",
    "investopedia" to "Investopedia",
    "robinhood" to "Robinhood",
    "zerodha" to "Zerodha"
)

private val GamingDomains = mapOf(
    "steampowered" to "Steam",
    "steamcommunity" to "Steam",
    "epicgames" to "Epic Games",
    "playstation" to "PlayStation",
    "xbox" to "Xbox",
    "nintendo" to "Nintendo",
    "itch" to "itch.io",
    "roblox" to "Roblox",
    "ign" to "IGN"
)

private val ShortLinkDomains = mapOf(
    "bit" to "Short Links",
    "tinyurl" to "Short Links",
    "rebrand" to "Short Links",
    "shorturl" to "Short Links",
    "cutt" to "Short Links",
    "buff" to "Short Links",
    "ow" to "Short Links",
    "dub" to "Short Links"
)

private val CategoryByDomainName: Map<String, String> =
    SocialDomains + VideoDomains + MusicDomains + DeveloperDomains + ReadingDomains +
        NewsDomains + ShoppingDomains + ProductivityDomains + LearningDomains + AiDomains +
        TravelDomains + ImageDomains + FinanceDomains + GamingDomains + ShortLinkDomains

private val CategoryByExactHost = mapOf(
    "t.co" to "X",
    "t.me" to "Telegram",
    "gemini.google.com" to "Gemini",
    "chat.openai.com" to "ChatGPT",
    "sora.com" to "ChatGPT",
    "mail.google.com" to "Gmail",
    "drive.google.com" to "Google Drive",
    "maps.google.com" to "Google Maps",
    "maps.app.goo.gl" to "Google Maps",
    "news.google.com" to "Google News",
    "music.apple.com" to "Apple Music",
    "podcasts.apple.com" to "Apple Podcasts",
    "developer.mozilla.org" to "MDN"
)

private val CategoryDisplayOrder = listOf(
    "Instagram",
    "YouTube",
    "X",
    "TikTok",
    "Reddit",
    "Pinterest",
    "Threads",
    "Facebook",
    "Snapchat",
    "LinkedIn",
    "WhatsApp",
    "Telegram",
    "Spotify",
    "Twitch",
    "Netflix",
    "GitHub",
    "Stack Overflow",
    "Hacker News",
    "Medium",
    "Substack",
    "Notion",
    "ChatGPT",
    "Claude",
    "Gemini",
    "Google",
    "Wikipedia",
    "Amazon"
)

private val SharedSecondLevelDomains = setOf("co", "com", "net", "org", "ac", "gov", "edu")

fun bookmarkCategoryOf(url: String): String {
    val host = extractHost(url) ?: return OtherBookmarksCategory
    CategoryByExactHost[host]?.let { return it }
    val domainName = extractDomainName(host) ?: return OtherBookmarksCategory
    return CategoryByDomainName[domainName] ?: OtherBookmarksCategory
}

fun availableBookmarkCategories(
    urls: List<String>,
    customOrder: List<String> = emptyList()
): List<String> {
    if (urls.isEmpty()) return emptyList()

    val foundCategories = urls.mapTo(mutableSetOf()) { bookmarkCategoryOf(it) }
    return foundCategories.sortedWith(
        compareBy({ rankOf(it, customOrder) }, { it })
    )
}

private fun rankOf(category: String, customOrder: List<String>): Int {
    val customIndex = customOrder.indexOf(category)
    if (customIndex >= 0) return customIndex

    val fallbackBase = customOrder.size
    if (category == OtherBookmarksCategory) return fallbackBase + CategoryDisplayOrder.size + 1
    return fallbackBase + displayOrderIndexOf(category)
}

private fun displayOrderIndexOf(category: String): Int {
    val index = CategoryDisplayOrder.indexOf(category)
    return if (index >= 0) index else CategoryDisplayOrder.size
}

private fun extractHost(url: String): String? {
    val trimmedUrl = url.trim()
    if (trimmedUrl.isEmpty()) return null

    val host = trimmedUrl
        .substringAfter("://", trimmedUrl)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .lowercase()
        .removePrefix("www.")

    return host.ifBlank { null }
}

private fun extractDomainName(host: String): String? {
    val labels = host.split('.').filter { it.isNotBlank() }
    if (labels.size < 2) return null

    val domainLabel = labels[labels.size - 2]
    if (domainLabel in SharedSecondLevelDomains && labels.size >= 3) return labels[labels.size - 3]
    return domainLabel
}
