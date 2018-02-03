package server

import server.RequestHeader.*
import java.net.Socket

// TODO: Split some of this functionality out into a RequestParser class

/**
 * A wrapper around a connection to a client.
 *
 * Provides methods for parsing a request and writing the response that it generated by the corresponding [Handler].
 *
 * @param connection The connection to the client.
 */
class ClientConnection(connection: Socket) {
    // Used to read from the connection.
    private val connectionReader = connection.getInputStream().bufferedReader()
    // Used to write to the connection.
    private val connectionWriter = connection.getOutputStream().bufferedWriter()
    val acceptedHeaders = mapOf("host" to HOST, "connection" to CONNECTION, "content-length" to CONTENT_LENGTH)

    /**
     * Parses the HTTP request sent by the client.
     *
     * @return The HTTP request.
     */
    internal fun parseRequest(): Request {
        val (method, path, protocol) = extractRequestLine()
        val headers = extractHeaders()
        return when (method) {
            "GET" -> GetRequest(path, protocol, headers)
            "POST" -> {
                val contentLengthString = headers[CONTENT_LENGTH]
                        ?: throw NoContentLengthHeaderOnPostRequestException()
                val contentLength = contentLengthString.toInt()
                val body = extractBody(contentLength)
                PostRequest(body, path, protocol, headers)
            }
            "PUT" -> {
                val contentLengthString = headers[CONTENT_LENGTH]
                        ?: throw NoContentLengthHeaderOnPostRequestException()
                val contentLength = contentLengthString.toInt()
                val body = extractBody(contentLength)
                PutRequest(body, path, protocol, headers)
            }
            // TODO: Need to escalate this error back to the client.
            else -> throw UnrecognisedHTTPMethodException()
        }
    }

    /**
     * Parses the request line of the HTTP request.
     *
     * @return The request line's method, path and protocol.
     */
    private fun extractRequestLine(): Triple<String, String, String> {
        val requestLine = connectionReader.readLine()
        val requestLineRegex = Regex("""[^ ]+""")
        val requestLineMatchResults = requestLineRegex.findAll(requestLine)
        val requestLineItems = requestLineMatchResults.map { it.value }.toList()
        if (requestLineItems.size != 3) {
            throw MalformedRequestLineException()
        }
        val (method, path, protocol) = requestLineItems
        return Triple(method, path, protocol)
    }

    /**
     * Parses the headers of the HTTP request.
     *
     * @return A mapping of the request's headers to values.
     */
    private fun extractHeaders(): Map<RequestHeader, String> {
        val headers = mutableMapOf<RequestHeader, String>()

        while (true) {
            val line = connectionReader.readLine() ?:
                    throw NoBlankLineAfterHeadersException()

            if (line == "") break

            if (!line.contains(':')) throw MissingColonInHeadersException()

            // We don't need to check for too many colons, as the vault is allowed to contain colons.
            val (headerString, value) = line.toLowerCase().split(':', limit = 2).map { it.trim() }

            // We ignore unrecognised headers.
            val header = acceptedHeaders[headerString.toLowerCase()] ?: continue
            headers.put(header, value)
        }

        return headers
    }

    /**
     * Parses the body of the HTTP request.
     *
     * @param contentLength The body's length, based on the Content-Length header.
     * @return A mapping of the request body's keys to values.
     */
    private fun extractBody(contentLength: Int): Map<String, String> {
        val bodyChars = CharArray(contentLength)
        connectionReader.read(bodyChars, 0, contentLength)
        val bodyString = bodyChars.joinToString("")

        val body = mutableMapOf<String, String>()

        // TODO: Do this more gracefully - use a regex below?
        if (contentLength == 0) {
            return body
        }

        val namesAndValues = bodyString.split('&')
            for (nameAndValue in namesAndValues) {
                val numberOfSeparators = nameAndValue.count { it == '=' }
                if (numberOfSeparators == 0) throw MissingBodyValueException()
                if (numberOfSeparators >= 2) throw MissingBodyNameException()

                val (name, value) = nameAndValue.split('=', limit = 2).map { it.trim() }

                if (name in body) throw RepeatedBodyNameException()

                body.put(name, value)
            }

        return body
    }

    /**
     * Writes an HTTP response to the client.
     *
     * @return The HTTP response's headers and body.
     */
    internal fun writeResponse(response: Response) {
        connectionWriter.write(response.statusLine.toString())
        connectionWriter.newLine()
        for (header in response.headers) {
            connectionWriter.write(header.toString())
            connectionWriter.newLine()
        }
        connectionWriter.newLine()
        connectionWriter.write(response.body)
        connectionWriter.newLine()
        connectionWriter.flush()
    }
}

class NoContentLengthHeaderOnPostRequestException: IllegalArgumentException()
class UnrecognisedHTTPMethodException: IllegalArgumentException()
class UnrecognisedHeaderException: IllegalArgumentException()
class MalformedRequestLineException: IllegalArgumentException()
class MissingBodyNameException: IllegalArgumentException()
class MissingBodyValueException: IllegalArgumentException()
class MissingColonInHeadersException: IllegalArgumentException()
class NoBlankLineAfterHeadersException: IllegalArgumentException()
class RepeatedBodyNameException: IllegalArgumentException()