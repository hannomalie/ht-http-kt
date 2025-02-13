package org.example

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.ByteBuffer

data class Header(val key: String, val value: String)
data class Param(val key: String, val value: String)
data class Request(val headers: List<Header>, val params: List<Param>, val body: String)
data class Response(val status: Int, val headers: List<Header>, val body: String)

class RouteDefinition(val method: String, val path: String, val handler: RequestHandler)
fun interface RequestHandler {
    operator fun invoke(request: Request): Response
}

class HttpServer(val port: Int, vararg val routeDefinitions: RouteDefinition) {
    var stopRequested = false
}

fun HttpServer.runOnSocket() {
    val serverSocket = ServerSocket(port)
    while(!stopRequested) {
        val lines = mutableListOf<String>()
        val clientSocket = serverSocket.accept()
        val outputStream = clientSocket.getOutputStream()
        val out = PrintWriter(outputStream, true)
        val inputStream = clientSocket.getInputStream()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        var emptyLineIndex = -1
        var line: String

        while(emptyLineIndex == -1) {
            line = bufferedReader.readLine()
            lines.add(line)
            emptyLineIndex = lines.indexOf("")
        }

        val methodLine = lines.first()
        val methodLineParts = methodLine.split(" ")
        val method = methodLineParts.first()
        val pathAndParams = methodLineParts[1]
        val pathAndParamsString = pathAndParams.split("&")
        val path = pathAndParamsString.first()
        val paramsString = pathAndParamsString.subList(1, pathAndParamsString.size);
        val params = if(paramsString.isNotEmpty()) {
            paramsString[0].split("?").map {
                val keyValue = it.split("=")
                Param(keyValue[0], keyValue[1])
            }
        } else emptyList()

        val handler = routeDefinitions.firstOrNull {
            it.method == method && it.path == path
        }?.handler
        val responseString = if(handler != null) {
            val headerLines = lines.subList(1, emptyLineIndex)
            val headers = headerLines.map {
                val keyValue = it.split(": ")
                Header(keyValue[0], keyValue[1])
            }
            val contentLength = headers.firstOrNull { it.key.lowercase() == "content-length" }?.value?.toInt()
            val body = if(contentLength == null) {
                ""
            } else {
                val bodyBuffer = ByteBuffer.allocate(contentLength)
                val bodyUntilNow = lines.subList(emptyLineIndex, lines.size).joinToString("")
                bodyBuffer.put(bodyUntilNow.toByteArray(Charsets.UTF_8))

                val restBody = inputStream.readNBytes(contentLength - bodyUntilNow.length)
                bodyBuffer.put(restBody)

                String(bodyBuffer.array(), Charsets.UTF_8)

            }
            val response = handler.invoke(
                Request(
                    headers = headers,
                    params,
                    body = body,
                ),
            )
            buildString {
                append("HTTP/1.1 ")
                append(response.status.toString())
                append("\n")
                append(response.headers.joinToString("\n") { "${it.key}:${it.value}" })
                append("\n\n")
                append(response.body)
            }
        } else {
            "HTTP/1.1 404"
        }
        out.write(responseString)
        out.close()
        inputStream.close()
    }
}

fun main(args: Array<String>) {
    val port: Int = if (args.isNotEmpty()) args[0].toInt() else 8080

    HttpServer(
        port,
        RouteDefinition("GET", "/") { request ->
            Response(
                200,
                listOf(Header("Content-Type", "text/html")),
                "<html><body>" + request.headers + "</body></html>"
            )
        },
        RouteDefinition("GET", "/params") { request ->
            Response(
                200,
                listOf(Header("Content-Type", "text/html")),
                "<html><body>" + request.params + "</body></html>"
            )
        },
        RouteDefinition("PUT", "/body") { request ->
            Response(
                200,
                listOf(Header("Content-Type", "text/html")),
                request.body
            )
        },
    ).runOnSocket()
}
