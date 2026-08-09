package com.example.poweruser1.ui.home

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object TextOnlyCleaner {

    private val REMOVE_TAGS = listOf(
        "script", "style", "link", "img", "video", "audio", "iframe",
        "embed", "object", "svg", "canvas", "input", "button", "select",
        "textarea", "form", "dialog",
    )

    private val REMOVE_STRUCTURAL_TAGS = listOf(
        "nav", "footer", "aside", "menu",
    )

    private val REMOVE_ROLES = listOf(
        "[role='navigation']", "[role='menu']", "[role='menubar']",
        "[role='banner']", "[role='contentinfo']", "[role='complementary']",
        "[role='search']", "[role='dialog']",
    )

    fun getTitle(html: String): String {
        if (html.isBlank()) return "Untitled Article"
        val doc = Jsoup.parse(html)
        
        // 1. Try to find the first real header
        val header = doc.selectFirst("h1, h2, h3, h4, h5, h6")
        if (header != null) {
            val text = header.text().trim()
            if (text.isNotBlank()) return text
        }
        
        // 2. Fallback: first 12 characters of body text
        val bodyText = doc.body().text().trim()
        return if (bodyText.length > 12) {
            bodyText.substring(0, 12).trim()
        } else if (bodyText.isNotBlank()) {
            bodyText
        } else {
            "Untitled Article"
        }
    }

    fun clean(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        val doc = Jsoup.parse(rawHtml)
        val docTitle = doc.title().trim()

        for (tag in REMOVE_TAGS) {
            doc.select(tag).remove()
        }

        for (tag in REMOVE_STRUCTURAL_TAGS) {
            doc.select(tag).remove()
        }

        for (role in REMOVE_ROLES) {
            doc.select(role).remove()
        }

        doc.select("ins.adsbygoogle, div[id^='google_ads'], div[id^='div-gpt-ad']").remove()

        val allElements = doc.allElements
        val elementsToRemove = mutableListOf<Element>()

        for (element in allElements) {
            val className = element.className()
            val id = element.id()

            if (matchesExclude(className) || matchesExclude(id) || element.tagName().equals("header", ignoreCase = true)) {
                if (!isArticleHeaderOrTitleOrByline(element)) {
                    val tag = element.tagName().lowercase()
                    if ((tag != "body") && (tag != "html") && (tag != "main") && (tag != "article")) {
                        elementsToRemove.add(element)
                    }
                }
            }
        }

        for (el in elementsToRemove) {
            el.remove()
        }

        val body = doc.body()

        if (docTitle.isNotBlank() && (body.selectFirst("h1") == null)) {
            body.prependChild(doc.createElement("h1").text(docTitle))
        }

        val outputSettings = doc.outputSettings()
        outputSettings.syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
        outputSettings.escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
        outputSettings.charset("UTF-8")

        return body.html()
    }

    private fun isArticleHeaderOrTitleOrByline(element: Element): Boolean {
        val tag = element.tagName().lowercase()

        if ((tag == "h1") || (tag == "h2") || (tag == "h3")) {
            return true
        }

        if (element.selectFirst("h1, h2, h3") != null) {
            return true
        }

        val className = element.className().lowercase()
        val id = element.id().lowercase()

        val isTitleOrBylineMatch = className.contains("title") ||
                className.contains("headline") ||
                className.contains("byline") ||
                className.contains("by-line") ||
                className.contains("author") ||
                className.contains("pubdate") ||
                className.contains("post-meta") ||
                className.contains("article-meta") ||
                className.contains("entry-meta") ||
                id.contains("title") ||
                id.contains("headline") ||
                id.contains("byline") ||
                id.contains("author") ||
                id.contains("firstheading")

        if (isTitleOrBylineMatch) return true

        if (element.hasAttr("itemprop")) {
            val itemprop = element.attr("itemprop").lowercase()
            if ((itemprop == "author") || (itemprop == "headline") || (itemprop == "datepublished") || (itemprop == "name")) {
                return true
            }
        }

        if (tag == "header") {
            val parentTag = element.parent()?.tagName()?.lowercase() ?: ""
            if ((parentTag == "article") || (parentTag == "main") || element.parents().any { it.tagName().equals("article", ignoreCase = true) }) {
                return true
            }
        }

        return false
    }

    private fun matchesExclude(str: String): Boolean {
        if (str.isBlank()) return false
        val s = str.lowercase()
        return s.contains("nav") ||
                s.contains("menu") ||
                s.contains("footer") ||
                s.contains("sidebar") ||
                s.contains("side-bar") ||
                s.contains("_side") ||
                s.contains("aside") ||
                s.contains("advert") ||
                s.contains("sponsor") ||
                s.contains("banner") ||
                s.contains("promo") ||
                s.contains("popup") ||
                s.contains("modal") ||
                s.contains("cookie") ||
                s.contains("gdpr") ||
                s.contains("social") ||
                s.contains("share") ||
                s.contains("widget") ||
                s.contains("breadcrumb") ||
                s.contains("toc") ||
                s.contains("ad-") ||
                s.contains("ads")
    }
}
