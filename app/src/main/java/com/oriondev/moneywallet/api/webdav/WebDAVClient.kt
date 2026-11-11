/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.api.webdav

import com.oriondev.moneywallet.model.WebDAVFile
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The whole WebDAV surface this app needs: list one folder, create one folder, upload a file,
 * download a file. That is PROPFIND, MKCOL, PUT and GET.
 *
 * It runs on OkHttp rather than the platform's `HttpURLConnection` because the platform
 * client validates the method against a fixed list and throws
 * `ProtocolException: Expected one of [OPTIONS, GET, HEAD, POST, PUT, DELETE, TRACE, PATCH]`
 * for both PROPFIND and MKCOL. OkHttp has no such list, and its own source special cases WebDAV
 * verbs, so it is the only realistic transport here.
 *
 * The methods that do not touch the network are static and package visible so they can be unit
 * tested on the JVM.
 */
class WebDAVClient(baseUrl: String?, username: String?, password: String?) {

    private val mBaseUrl: String = normalizeBaseUrl(baseUrl)
    private val mAuthorization: String = Credentials.basic(username ?: "", password ?: "")
    private val mHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    internal fun urlFor(path: String?): String = mBaseUrl + encodePath(path ?: "")

    /**
     * The path component of the base URL, used to strip a server's absolute href back down to a
     * path relative to the configured root.
     */
    internal fun basePath(): String {
        return try {
            URL(mBaseUrl).path ?: ""
        } catch (e: MalformedURLException) {
            ""
        }
    }

    private fun request(path: String?): Request.Builder {
        return Request.Builder()
            .url(urlFor(path))
            .header("Authorization", mAuthorization)
    }

    @Throws(WebDAVException::class)
    fun list(path: String?): List<@JvmSuppressWildcards WebDAVFile> {
        val request = request(directoryPath(path))
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .header("Depth", "1")
            .build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "list $path")
                }
                val body = response.body
                    ?: throw WebDAVException("Server returned an empty folder listing", true)
                return parsePropfind(body.byteStream(), basePath(), trimSlashes(path))
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not list $path", e, true)
        } catch (e: SAXException) {
            throw WebDAVException("Could not read the server's folder listing", e, true)
        } catch (e: ParserConfigurationException) {
            throw WebDAVException("Could not read the server's folder listing", e, true)
        }
    }

    @Throws(WebDAVException::class)
    fun makeCollection(path: String?) {
        // MKCOL takes no body. OkHttp requires an explicit empty one for a method it does not know
        // to be body-less.
        val request = request(directoryPath(path))
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "create folder $path")
                }
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not create folder $path", e, true)
        }
    }

    /**
     * Streams the upload rather than buffering it: a backup archive is arbitrarily large and the
     * caller's stream already reports progress.
     */
    @Throws(WebDAVException::class)
    fun upload(path: String?, inputStream: InputStream, size: Long) {
        val body = object : RequestBody() {
            override fun contentType(): MediaType = OCTET_STREAM

            override fun contentLength(): Long = if (size > 0) size else -1L

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                copy(inputStream, sink.outputStream())
            }
        }
        val request = request(path).put(body).build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "upload $path")
                }
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not upload $path", e, true)
        }
    }

    /**
     * Opens the download stream. The caller owns it and must close it, which also releases the
     * connection back to the pool.
     */
    @Throws(WebDAVException::class)
    fun openDownload(path: String?): InputStream {
        val request = request(path).get().build()
        var response: Response? = null
        try {
            val executed = mHttp.newCall(request).execute()
            response = executed
            if (!executed.isSuccessful) {
                val code = executed.code
                executed.close()
                throw WebDAVException.forStatus(code, "download $path")
            }
            val body = executed.body
            if (body == null) {
                executed.close()
                throw WebDAVException("Server returned an empty response for $path", true)
            }
            return body.byteStream()
        } catch (e: IOException) {
            response?.close()
            throw WebDAVException("Could not download $path", e, true)
        }
    }

    /**
     * A cheap PROPFIND against the root, used by the setup dialog so a typo or a wrong password is
     * reported while the user is still looking at the form.
     */
    @Throws(WebDAVException::class)
    fun checkConnection() {
        list("")
    }

    companion object {
        internal const val DAV_NAMESPACE = "DAV:"

        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 20L
        private const val WRITE_TIMEOUT_S = 60L

        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        private const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:resourcetype/><d:getcontentlength/>" +
                "</d:prop></d:propfind>"

        /**
         * Trims the URL and guarantees exactly one trailing slash, so joining a path is always a plain
         * concatenation. A base URL saved with or without the slash has to behave identically; users
         * type it both ways.
         */
        @JvmStatic
        internal fun normalizeBaseUrl(url: String?): String {
            var trimmed = url?.trim() ?: ""
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length - 1)
            }
            return "$trimmed/"
        }

        /**
         * Percent encodes each path segment, leaving the separators alone. A backup file named with a
         * space or an accented character is ordinary, and an unencoded one produces a 400 from most
         * servers, so this is not a corner case.
         */
        @JvmStatic
        internal fun encodePath(path: String?): String {
            if (path.isNullOrEmpty()) {
                return ""
            }
            val builder = StringBuilder()
            val segments = path.split("/")
            for (i in segments.indices) {
                if (i > 0) {
                    builder.append('/')
                }
                builder.append(encodeSegment(segments[i]))
            }
            return builder.toString()
        }

        private fun encodeSegment(segment: String): String {
            if (segment.isEmpty()) {
                return segment
            }
            return try {
                // URLEncoder targets form bodies, not paths: it maps a space to '+' and escapes a few
                // characters that are legal in a path. Undo those three rather than hand rolling a
                // second encoder.
                URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")
                    .replace("%7E", "~")
                    .replace("*", "%2A")
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError("UTF-8 is always available")
            }
        }

        @JvmStatic
        internal fun decodePath(encoded: String): String {
            return try {
                URLDecoder.decode(encoded, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError("UTF-8 is always available")
            } catch (e: IllegalArgumentException) {
                // A malformed escape is the server's problem, not a reason to lose the whole listing.
                encoded
            }
        }

        /**
         * Reduces an href from a PROPFIND response to a path relative to the base URL. Servers are
         * inconsistent here: some return an absolute URL, some return an absolute path including the
         * base URL's own prefix, so both shapes are handled.
         */
        @JvmStatic
        internal fun hrefToRelativePath(href: String, basePath: String): String {
            var path = href
            val schemeEnd = path.indexOf("://")
            if (schemeEnd >= 0) {
                val hostEnd = path.indexOf('/', schemeEnd + 3)
                path = if (hostEnd >= 0) path.substring(hostEnd) else "/"
            }
            path = decodePath(path)
            if (basePath.isNotEmpty() && path.startsWith(basePath)) {
                path = path.substring(basePath.length)
            }
            return trimSlashes(path)
        }

        @JvmStatic
        internal fun nameOf(path: String): String {
            val slash = path.lastIndexOf('/')
            return if (slash >= 0) path.substring(slash + 1) else path
        }

        /**
         * Parses a PROPFIND multistatus body. Namespace aware on purpose: servers disagree about the
         * prefix (D:, d:, lp1:, ns0:), so matching on the DAV: namespace rather than the tag text is
         * what makes this work across Nextcloud, ownCloud and a plain Apache mod_dav.
         *
         * The entry describing the requested folder itself is dropped: a Depth 1 PROPFIND always
         * returns it alongside its children.
         */
        @JvmStatic
        @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
        internal fun parsePropfind(
            inputStream: InputStream,
            basePath: String,
            requestedPath: String
        ): List<WebDAVFile> {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            // The response is data, not a document that gets to reference the local filesystem.
            setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.isExpandEntityReferences = false

            val builder = factory.newDocumentBuilder()
            val document = builder.parse(inputStream)

            val files = ArrayList<WebDAVFile>()
            val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
            for (i in 0 until responses.length) {
                val response = responses.item(i) as? Element ?: continue
                val href = textOf(response, "href") ?: continue
                val path = hrefToRelativePath(href, basePath)
                if (path == requestedPath) {
                    continue
                }
                val isDirectory = firstElement(response, "collection") != null
                var size = 0L
                val contentLength = textOf(response, "getcontentlength")
                if (contentLength != null) {
                    try {
                        size = contentLength.trim().toLong()
                    } catch (ignored: NumberFormatException) {
                        // A folder, or a server that omits the length. Zero is the right answer.
                    }
                }
                files.add(WebDAVFile(nameOf(path), path, size, isDirectory))
            }
            return files
        }

        private fun setFeatureQuietly(factory: DocumentBuilderFactory, feature: String, value: Boolean) {
            try {
                factory.setFeature(feature, value)
            } catch (ignored: ParserConfigurationException) {
                // Not every parser knows this feature. Namespace awareness is the part we depend on.
            }
        }

        private fun firstElement(parent: Element, localName: String): Element? {
            val nodes = parent.getElementsByTagNameNS(DAV_NAMESPACE, localName)
            return if (nodes.length > 0) nodes.item(0) as? Element else null
        }

        private fun textOf(parent: Element, localName: String): String? {
            val element = firstElement(parent, localName) ?: return null
            val text = StringBuilder()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    text.append(child.nodeValue)
                }
            }
            return text.toString()
        }

        @JvmStatic
        internal fun trimSlashes(path: String?): String {
            var result = path ?: ""
            while (result.startsWith("/")) {
                result = result.substring(1)
            }
            while (result.endsWith("/")) {
                result = result.substring(0, result.length - 1)
            }
            return result
        }

        private fun directoryPath(path: String?): String {
            val trimmed = trimSlashes(path)
            return if (trimmed.isEmpty()) "" else "$trimmed/"
        }

        @Throws(IOException::class)
        private fun copy(inputStream: InputStream, outputStream: OutputStream) {
            val chunk = ByteArray(8192)
            while (true) {
                val read = inputStream.read(chunk)
                if (read == -1) {
                    break
                }
                outputStream.write(chunk, 0, read)
            }
        }
    }
}
