/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import okhttp3.*
import java.io.IOException
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger

class DavCalendar @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Constants.log
): DavCollection(httpClient, location, log) {

    companion object {
        @JvmField
        val MIME_ICALENDAR = MediaType.parse("text/calendar;charset=utf-8")
    }

    private val timeFormatUTC = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)


    /**
     * Sends a calendar-query REPORT to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun calendarQuery(component: String, start: Date?, end: Date?) {
        /* <!ELEMENT calendar-query ((DAV:allprop |
                                      DAV:propname |
                                      DAV:prop)?, filter, timezone?)>
           <!ELEMENT filter (comp-filter)>
           <!ELEMENT comp-filter (is-not-defined | (time-range?,
                                  prop-filter*, comp-filter*))>
           <!ATTLIST comp-filter name CDATA #REQUIRED>
           name value: a calendar object or calendar component
                       type (e.g., VEVENT)

        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-query")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            serializer.startTag(XmlUtils.NS_CALDAV, "filter")
                serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter")
                serializer.attribute(null, "name", "VCALENDAR")
                    serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter")
                    serializer.attribute(null, "name", component)
                    if (start != null || end != null) {
                        serializer.startTag(XmlUtils.NS_CALDAV, "time-range")
                        if (start != null)
                            serializer.attribute(null, "start", timeFormatUTC.format(start))
                        if (end != null)
                            serializer.attribute(null, "end", timeFormatUTC.format(end))
                        serializer.endTag(XmlUtils.NS_CALDAV, "time-range")
                    }
                    serializer.endTag(XmlUtils.NS_CALDAV, "comp-filter")
                serializer.endTag(XmlUtils.NS_CALDAV, "comp-filter")
            serializer.endTag(XmlUtils.NS_CALDAV, "filter")
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-query")
        serializer.endDocument()

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "1")
                .build()).execute()

        checkStatus(response, false)
        assertMultiStatus(response)

        members.clear()
        related.clear()
        response.body()?.charStream()?.use { processMultiStatus(it) }
    }

    /**
     * Sends a calendar-multiget REPORT to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun multiget(urls: Array<HttpUrl>) {
        /* <!ELEMENT calendar-multiget ((DAV:allprop |
                                        DAV:propname |
                                        DAV:prop)?, DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-multiget")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getcontenttype")      // to determine the character set
                serializer.endTag(XmlUtils.NS_WEBDAV, "getcontenttype")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.startTag(XmlUtils.NS_CALDAV, "calendar-data")
                serializer.endTag(XmlUtils.NS_CALDAV, "calendar-data")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            for (url in urls) {
                serializer.startTag(XmlUtils.NS_WEBDAV, "href")
                    serializer.text(url.encodedPath())
                serializer.endTag(XmlUtils.NS_WEBDAV, "href")
            }
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-multiget")
        serializer.endDocument()

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .build()).execute()

        checkStatus(response, false)
        assertMultiStatus(response)

        members.clear()
        related.clear()
        response.body()?.charStream()?.use { processMultiStatus(it) }
    }

}