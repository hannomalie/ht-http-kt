package org.example

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

data class Header(val key: String, val value: String)
data class Param(val key: String, val value: String)
data class Request(val headers: List<Header>, val params: List<Param>, val body: String)
data class Response(val status: Int, val headers: List<Header>, val body: String)

class RouteDefinition(val method: String, val path: String, val handler: RequestHandler)
fun interface RequestHandler {
    operator fun invoke(request: Request): Response
}

class HTTPRequestHandler(private val routeDefinitions: List<RouteDefinition>) : ChannelInboundHandlerAdapter() {
    private var buf: ByteBuf? = null

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        buf!!.release()
        buf = null
    }
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        buf = ctx.alloc().buffer(128)
    }
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf = buf!!

        val m = msg as ByteBuf
        buf.writeBytes(m)
        val lines = buf.toString(Charsets.UTF_8).lines()
        val emptyLineIndex = lines.indexOf("")

        m.release()

        if (emptyLineIndex != -1) {
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
                headerLines.forEach { println(it) }
                val headers = headerLines.map {
                    val keyValue = it.split(": ")
                    Header(keyValue[0], keyValue[1])
                }
                val contentLength = headers.firstOrNull { it.key.lowercase() == "content-length" }?.value?.toInt()
                val body = lines.subList(emptyLineIndex, lines.size).joinToString("")

                val bodyUtf8Bytes: ByteArray = body.toByteArray(Charsets.UTF_8)
                val completeBodyWasReceived = bodyUtf8Bytes.size == contentLength

                if(completeBodyWasReceived) {
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
                    null
                }
            } else {
                "HTTP/1.1 404"
            }
            if(responseString != null) {
                val utf8Bytes: ByteArray = responseString.toByteArray(Charsets.UTF_8)
                val resultBuf = ctx.alloc().buffer(utf8Bytes.size)
                resultBuf.writeBytes(utf8Bytes)

                ctx.writeAndFlush(resultBuf)
                ctx.close()
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { // (4)
        cause.printStackTrace()
        ctx.close()
    }
}

class HttpServer(val port: Int, vararg val routeDefinitions: RouteDefinition) {
    fun run() {
        val bossGroup = NioEventLoopGroup()
        val workerGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                .channel(
                    NioServerSocketChannel::class.java)
                        .childHandler(object: ChannelInitializer <SocketChannel>() {
                            override fun initChannel(ch: SocketChannel) {
                                ch.pipeline().addLast(HTTPRequestHandler(routeDefinitions.toList()))
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)

            // Bind and start to accept incoming connections.
            val f = b.bind(port).sync()

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync()
        } finally {
            workerGroup.shutdownGracefully()
            bossGroup.shutdownGracefully()
        }
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
    ).run()
}
