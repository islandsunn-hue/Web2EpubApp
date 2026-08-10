package com.web2epub1.poweruser1

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubTitleFallbackTest {

    private fun extractEpubTitle(rawHtml: String): String {
        val doc = Jsoup.parse(rawHtml)
        var title = doc.title().trim()
        if (title.isBlank()) {
            title = doc.selectFirst("h1, h2, h3, h4, h5, h6")?.text()?.trim() ?: ""
        }
        if (title.isBlank()) {
            title = "Untitled Document"
        }
        return title
    }

    @Test
    fun testUsesTitleWhenPresent() {
        val html = "<html><head><title>My Page Title</title></head><body><h1>Heading</h1></body></html>"
        assertEquals("My Page Title", extractEpubTitle(html))
    }

    @Test
    fun testUsesFirstHeadingWhenTitleIsMissing() {
        val html = "<html><head></head><body><h1>First Article Heading</h1><h2>Second Heading</h2></body></html>"
        assertEquals("First Article Heading", extractEpubTitle(html))
    }

    @Test
    fun testUsesFirstHeadingWhenTitleIsEmpty() {
        val html = "<html><head><title>   </title></head><body><h2>Second Level Heading</h2></body></html>"
        assertEquals("Second Level Heading", extractEpubTitle(html))
    }

    @Test
    fun testTextOnlyCleanerGetTitle() {
        val htmlWithH1 = "<html><body><h1>Real Header</h1><p>Content</p></body></html>"
        assertEquals("Real Header", com.web2epub1.poweruser1.ui.home.TextOnlyCleaner.getTitle(htmlWithH1))

        val htmlNoHeader = "<html><body><p>This is a long content that should be truncated to twelve characters.</p></body></html>"
        assertEquals("This is a lo", com.web2epub1.poweruser1.ui.home.TextOnlyCleaner.getTitle(htmlNoHeader))

        val htmlShortBody = "<html><body><p>Short</p></body></html>"
        assertEquals("Short", com.web2epub1.poweruser1.ui.home.TextOnlyCleaner.getTitle(htmlShortBody))

        val htmlEmpty = ""
        assertEquals("Untitled Article", com.web2epub1.poweruser1.ui.home.TextOnlyCleaner.getTitle(htmlEmpty))
    }
}
