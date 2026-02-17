package com.example.tvremotetest.casting

import android.graphics.Bitmap
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class MjpegServer(private val port: Int = 8080) {

    companion object {
        private const val TAG = "MjpegServer"
    }

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val latestFrameBytes = AtomicReference<ByteArray?>(null)

    @Volatile
    var isRunning = false
        private set

    private val htmlPage = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>TV Remote - Screen Mirror</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: #0D0D1A;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    color: #fff;
                    overflow: hidden;
                }
                .header {
                    position: fixed;
                    top: 0;
                    width: 100%;
                    padding: 12px;
                    text-align: center;
                    background: linear-gradient(180deg, rgba(13,13,26,0.95) 0%, transparent 100%);
                    z-index: 10;
                }
                h1 {
                    font-size: 1rem;
                    color: #E94560;
                    letter-spacing: 2px;
                    text-transform: uppercase;
                }
                .status {
                    font-size: 0.75rem;
                    color: #4CAF50;
                    margin-top: 4px;
                }
                .status.disconnected { color: #FF5252; }
                #screen {
                    max-width: 100vw;
                    max-height: 100vh;
                    object-fit: contain;
                    border-radius: 4px;
                }
                .footer {
                    position: fixed;
                    bottom: 0;
                    width: 100%;
                    padding: 8px;
                    text-align: center;
                    background: linear-gradient(0deg, rgba(13,13,26,0.95) 0%, transparent 100%);
                    font-size: 0.7rem;
                    color: #555;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>📺 Screen Mirror</h1>
                <div id="statusText" class="status">Connecting...</div>
            </div>
            <img id="screen" alt="Screen Mirror">
            <div class="footer">TV Remote &mdash; Screen Cast</div>
            <script>
                const img = document.getElementById('screen');
                const status = document.getElementById('statusText');
                let errorCount = 0;
                
                function loadFrame() {
                    const frame = new Image();
                    frame.onload = function() {
                        img.src = frame.src;
                        errorCount = 0;
                        status.textContent = '● Live';
                        status.className = 'status';
                        setTimeout(loadFrame, 80);
                    };
                    frame.onerror = function() {
                        errorCount++;
                        if (errorCount > 10) {
                            status.textContent = '● Disconnected';
                            status.className = 'status disconnected';
                        }
                        setTimeout(loadFrame, 500);
                    };
                    frame.src = '/snapshot?t=' + Date.now();
                }
                
                loadFrame();
            </script>
        </body>
        </html>
    """.trimIndent()

    fun start() {
        if (isRunning) return
        isRunning = true

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server started on port $port")

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting client", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server failed to start", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return

            // Read all headers
            var line = input.readLine()
            while (!line.isNullOrEmpty()) {
                line = input.readLine()
            }

            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val output = BufferedOutputStream(socket.getOutputStream())

            when {
                path.startsWith("/snapshot") -> serveSnapshot(output)
                else -> serveHtml(output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveHtml(output: OutputStream) {
        val body = htmlPage.toByteArray()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray())
        output.write(body)
        output.flush()
    }

    private fun serveSnapshot(output: OutputStream) {
        val jpeg = latestFrameBytes.get()
        if (jpeg != null) {
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: image/jpeg\r\n")
                append("Content-Length: ${jpeg.size}\r\n")
                append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(header.toByteArray())
            output.write(jpeg)
        } else {
            // No frame available yet - return a 1x1 transparent pixel
            val header = buildString {
                append("HTTP/1.1 204 No Content\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(header.toByteArray())
        }
        output.flush()
    }

    fun pushFrame(bitmap: Bitmap) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        latestFrameBytes.set(baos.toByteArray())
    }

    fun stop() {
        isRunning = false
        latestFrameBytes.set(null)

        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        executor.shutdownNow()
        Log.d(TAG, "Server stopped")
    }
}
