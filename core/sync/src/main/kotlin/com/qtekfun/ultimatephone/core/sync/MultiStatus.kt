package com.qtekfun.ultimatephone.core.sync

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** One `<d:response>` of a PROPFIND answer. */
data class DavResource(val href: String, val isCollection: Boolean, val etag: String?, val status: Int)

/**
 * Reads a WebDAV `207 Multi-Status` body. The parser never sees a DOCTYPE or an entity declaration: such a document
 * is rejected before parsing, so external entities and entity expansion attacks are impossible by construction. The
 * parser features are also turned off where the platform supports them, and any external entity would resolve to
 * nothing.
 */
object MultiStatus {
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun parse(xml: String): List<DavResource> {
        if (xml.length > MAX_BYTES) throw SyncException(SyncError.UNKNOWN, "Multi-Status answer too large")
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            throw SyncException(SyncError.UNKNOWN, "Multi-Status answer has a DOCTYPE")
        }
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            hardened(factory)
            val reader = factory.newSAXParser().xmlReader
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw SyncException(SyncError.UNKNOWN, "Multi-Status answer is not valid XML", e)
        }
        return handler.resources
    }

    /** Each feature is optional: Android's parser rejects some names, and that is fine because the DOCTYPE is already refused. */
    private fun hardened(factory: SAXParserFactory) {
        val features = mapOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
        )
        for ((name, value) in features) {
            try {
                factory.setFeature(name, value)
            } catch (_: SAXException) {
                // Not supported by this parser.
            } catch (_: javax.xml.parsers.ParserConfigurationException) {
                // Not supported by this parser.
            }
        }
    }

    private class Handler : DefaultHandler() {
        val resources = ArrayList<DavResource>()
        private val text = StringBuilder()
        private var href = ""
        private var etag: String? = null
        private var collection = false
        private var status = 0
        private var inResponse = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            text.setLength(0)
            when (localName) {
                "response" -> {
                    inResponse = true
                    href = ""
                    etag = null
                    collection = false
                    status = 0
                }
                "collection" -> if (inResponse) collection = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val value = text.toString().trim()
            when (localName) {
                "href" -> if (inResponse && href.isEmpty()) href = value
                "getetag" -> if (inResponse && value.isNotEmpty()) etag = value
                // `HTTP/1.1 200 OK`: the first status of the response is the one for the properties asked.
                "status" -> if (inResponse && status == 0) status = value.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                "response" -> {
                    resources += DavResource(href, collection, etag, status)
                    inResponse = false
                }
            }
            text.setLength(0)
        }

        override fun error(e: org.xml.sax.SAXParseException) = throw e
    }
}
