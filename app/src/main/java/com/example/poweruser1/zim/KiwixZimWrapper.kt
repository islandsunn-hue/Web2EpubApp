package com.web2epub1.poweruser1.zim

import android.os.ParcelFileDescriptor
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Entry
import java.io.File
import java.lang.Exception
import java.net.URLDecoder

class KiwixZimWrapper : AutoCloseable {

    private val archive: Archive
    private var pfd: ParcelFileDescriptor? = null

    constructor(file: File) {
        archive = Archive(file.absolutePath)
    }

    constructor(pfd: ParcelFileDescriptor) {
        this.pfd = pfd
        val size = pfd.statSize
        archive = try {
            org.kiwix.libzim.Archive(org.kiwix.libzim.FdInput(pfd.fileDescriptor, 0, size))
        } catch (e: Throwable) {
            try {
                org.kiwix.libzim.Archive(pfd.fileDescriptor)
            } catch (e2: Throwable) {
                throw e2
            }
        }
    }

    val articleCount: Long
        get() = try { archive.articleCount.toLong() } catch (_: Exception) { 0L }
        
    val id: String
        get() = try { archive.uuid } catch (_: Exception) { "" }
        
    val title: String
        get() = try { archive.getMetadata("Title") } catch(_: Exception) { "Unknown ZIM" }

    fun getMainPageEntry(): Entry? {
        try {
            if (archive.hasMainEntry()) {
                var entry = archive.mainEntry
                if (entry.isRedirect) {
                    try {
                        entry = entry.redirectEntry
                    } catch (_: Exception) {}
                }
                return entry
            }
        } catch (_: Exception) {}
        return null
    }

    fun getRandomArticle(): Entry? {
        try {
            val count = archive.articleCount.toLong()
            if (count > 0) {
                var entry = archive.randomEntry
                if (entry?.isRedirect == true) {
                    try {
                        entry = entry.redirectEntry
                    } catch (_: Exception) {}
                }
                return entry
            }
        } catch (_: Exception) {}
        return null
    }

    fun getEntryForPath(rawPath: String): Entry? {
        if (rawPath.isBlank()) return null

        val cleanPath = rawPath.removePrefix("/")

        // 1. Try exact path match
        try {
            if (archive.hasEntryByPath(cleanPath)) {
                return archive.getEntryByPath(cleanPath)
            }
        } catch (_: Exception) {}

        // 2. Try URL-decoded path if it contains percent-encoding
        if (cleanPath.contains("%")) {
            try {
                val decoded = URLDecoder.decode(cleanPath, "UTF-8")
                if (archive.hasEntryByPath(decoded)) {
                    return archive.getEntryByPath(decoded)
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to namespace prefixes if namespace is omitted
        val hasNamespace = cleanPath.length >= 2 && cleanPath[1] == '/'
        if (!hasNamespace) {
            val namespaces = arrayOf("A/", "C/", "I/", "J/", "-/")
            for (ns in namespaces) {
                val nsPath = ns + cleanPath
                try {
                    if (archive.hasEntryByPath(nsPath)) {
                        return archive.getEntryByPath(nsPath)
                    }
                } catch (_: Exception) {}

                if (cleanPath.contains("%")) {
                    try {
                        val decodedNsPath = ns + URLDecoder.decode(cleanPath, "UTF-8")
                        if (archive.hasEntryByPath(decodedNsPath)) {
                            return archive.getEntryByPath(decodedNsPath)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 4. Try exact title match
        try {
            if (archive.hasEntryByTitle(cleanPath)) {
                return archive.getEntryByTitle(cleanPath)
            }
        } catch (_: Exception) {}

        if (cleanPath.contains("%")) {
            try {
                val decodedTitle = URLDecoder.decode(cleanPath, "UTF-8")
                if (archive.hasEntryByTitle(decodedTitle)) {
                    return archive.getEntryByTitle(decodedTitle)
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun searchArticles(query: String, limit: Int = 50): List<Entry> {
        val results = mutableListOf<Entry>()
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            try {
                val iter = archive.iterByTitle()
                try {
                    while (iter.hasNext() && results.size < limit) {
                        results.add(iter.next())
                    }
                } finally {
                    try { iter.dispose() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                try {
                    val iter = archive.iterByPath()
                    try {
                        while (iter.hasNext() && results.size < limit) {
                            results.add(iter.next())
                        }
                    } finally {
                        try { iter.dispose() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            return results
        }

        // Search by title prefix
        try {
            val iter = archive.findByTitle(trimmedQuery)
            try {
                while (iter.hasNext() && results.size < limit) {
                    results.add(iter.next())
                }
            } finally {
                try { iter.dispose() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Search by path prefix if more results needed
        if (results.size < limit) {
            try {
                val iter = archive.findByPath(trimmedQuery)
                try {
                    while (iter.hasNext() && results.size < limit) {
                        val entry = iter.next()
                        if (results.none { it.path == entry.path }) {
                            results.add(entry)
                        }
                    }
                } finally {
                    try { iter.dispose() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // If no prefix matches, check direct exact match via getEntryForPath
        if (results.isEmpty()) {
            val directEntry = getEntryForPath(trimmedQuery)
            if (directEntry != null) {
                results.add(directEntry)
            }
        }

        return results
    }

    override fun close() {
        try {
            archive.dispose()
        } catch (_: Exception) {}
        try {
            pfd?.close()
        } catch (_: Exception) {}
    }
}
