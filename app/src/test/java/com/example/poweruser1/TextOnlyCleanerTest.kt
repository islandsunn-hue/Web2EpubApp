package com.example.poweruser1

import com.example.poweruser1.ui.home.TextOnlyCleaner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOnlyCleanerTest {

    @Test
    fun testRemovesNavAndAdsButPreservesTitleAndByline() {
        val html = """
            <html>
                <head><title>Breaking News Story</title></head>
                <body>
                    <nav class="navbar">Menu Item 1 | Menu Item 2</nav>
                    <div class="article-header">
                        <h1 class="entry-title">Breaking News Story</h1>
                        <div class="byline">By Jane Doe</div>
                    </div>
                    <div class="ad-banner">Sponsored Ad</div>
                    <article>
                        <p>Main article paragraph.</p>
                    </article>
                    <footer class="site-footer">Footer links</footer>
                </body>
            </html>
        """.trimIndent()

        val cleaned = TextOnlyCleaner.clean(html)

        assertTrue(cleaned.contains("Breaking News Story"))
        assertTrue(cleaned.contains("By Jane Doe"))
        assertTrue(cleaned.contains("Main article paragraph."))
        assertFalse(cleaned.contains("Menu Item 1"))
        assertFalse(cleaned.contains("Sponsored Ad"))
        assertFalse(cleaned.contains("Footer links"))
    }

    @Test
    fun testPrependsDocTitleWhenNoHeadingExists() {
        val html = """
            <html>
                <head><title>Potosí Department - Wikipedia</title></head>
                <body>
                    <p>Potosí is a department in southwestern Bolivia.</p>
                </body>
            </html>
        """.trimIndent()

        val cleaned = TextOnlyCleaner.clean(html)

        assertTrue(cleaned.contains("<h1>Potosí Department - Wikipedia</h1>"))
        assertTrue(cleaned.contains("Potosí is a department in southwestern Bolivia."))
    }
}
