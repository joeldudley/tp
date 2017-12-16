package server

import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WriteResponseTests {
    private val server = Server(PORT)

    // TODO: Other tests?

    @Test
    fun `request line is parsed correctly`() {
        val responseBody = "Test body"
        val expectedResponse = "HTTP/1.1 200 OK\n" +
                "Content-Type: text/plain\n" +
                "Content-Length: 10\n" +
                "Connection: close\n" +
                "\n" +
                "$responseBody\n"

        val mockSocket = mock(Socket::class.java)
        val mockOutputStream = ByteArrayOutputStream()
        `when`(mockSocket.getOutputStream()).thenReturn(mockOutputStream)

        server.writeResponse(mockSocket, responseBody)

        assertEquals(expectedResponse, mockOutputStream.toString())
    }
}