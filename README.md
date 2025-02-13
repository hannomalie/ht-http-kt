## HTTP server from scratch

Every developer has to write an HTTP server from scratch once, right?
My take is barely using anything but TCP sockets (plus I have an implementation based on Netty, well... ).

This is how you can define a routing for it:

```kotlin
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
).apply {
    runOnSocket()
}
```

Not too bad, hu? The whole implementation is in _HttpServer.kt_. Not too much magic - open a socket,
read until you find an empty line (which denotes end of the headers), check for a content-length header
and read the remaining bytes from the socket. Iterate all registered request handlers,
find a matching one and invoke it with an instance of a request, which now contains params, headers and body.
Convert the resulting response to bytes, send them back on the socket, and finish.