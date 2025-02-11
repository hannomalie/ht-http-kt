import jdk.internal.net.http.RequestPublishers
import org.example.Header
import org.example.HttpServer
import org.example.Response
import org.example.RouteDefinition
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals


class ServerTests {

    val server = HttpServer(
        9909,
        RouteDefinition("GET", "/") { request ->
            Response(
                200,
                listOf(Header("Content-Type", "text/html")),
                "<html><body>" + request.headers.filter { it.key == "custom-header" } + "</body></html>"
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
    ).apply {
        CompletableFuture.runAsync {
            run()
        }
    }

    @Test
    fun getRoot() {
        val client = HttpClient.newHttpClient()
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9909/"))
            .header("custom-header", "foo")
            .build()
        val response: HttpResponse<String> = client.send(request, BodyHandlers.ofString())
        assertEquals(response.statusCode(), 200)
        assertEquals("<html><body>[Header(key=custom-header, value=foo)]</body></html>", response.body())
    }

    @Test
    fun getParams() {
        val client = HttpClient.newHttpClient()
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9909/params&foo=bar"))
            .build()
        val response: HttpResponse<String> = client.send(request, BodyHandlers.ofString())
        assertEquals(response.statusCode(), 200)
        assertEquals("<html><body>[Param(key=foo, value=bar)]</body></html>", response.body())
    }

    @Test
    fun putBody() {
        val client = HttpClient.newHttpClient()
        val request: HttpRequest = HttpRequest.newBuilder()
            .method("PUT", HttpRequest.BodyPublishers.ofString("some body", Charsets.UTF_8))
            .uri(URI.create("http://localhost:9909/body"))
            .build()
        val response: HttpResponse<String> = client.send(request, BodyHandlers.ofString())
        assertEquals(response.statusCode(), 200)
        assertEquals("some body", response.body())
    }
}