package dev.kharioki.nyumbani

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A tiny web server that lets a phone on the same network send a wallpaper to the TV.
 *
 * It only exists while the "Send from phone" screen is open. It listens on the TV's LAN address
 * (not every interface), every request must carry the random token from the QR code, and it only
 * accepts a single JPEG, PNG or WebP of at most 25 MB at a time.
 */
class PhoneUploadServer(
    private val workDir: File,
    /** Called on a server thread with the uploaded file; returns normally on success or throws with a message for the phone. */
    private val onUpload: (File) -> Unit,
) {
    private val token = randomToken()
    private val busy = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(3) { r -> Thread(r, "nyumbani-upload").apply { isDaemon = true } }
    @Volatile private var socket: ServerSocket? = null

    /** Starts listening and returns the address to put in the QR code, or null when the TV has no LAN address. */
    fun start(): String? {
        val address = lanAddress() ?: return null
        val server = ServerSocket()
        server.reuseAddress = true
        try {
            server.bind(InetSocketAddress(address, PREFERRED_PORT), 8)
        } catch (e: IOException) {
            server.bind(InetSocketAddress(address, 0), 8)
        }
        socket = server
        pool.execute { acceptLoop(server) }
        return "http://${address.hostAddress}:${server.localPort}/$token"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: SocketException) {
                break // closed by stop()
            } catch (e: IOException) {
                Log.w(TAG, "Upload server accept failed", e)
                continue
            }
            try {
                pool.execute { client.use { handle(it) } }
            } catch (e: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val request = readHead(input) ?: return respond(out, 400, "text/plain", "Bad request")

        val path = request.path.substringBefore('?').trimEnd('/')
        val segments = path.trimStart('/').split('/')
        // Everything lives under /<token>; anything else gets the same plain 404.
        if (segments.isEmpty() || !sameToken(segments[0])) return respond(out, 404, "text/plain", "Not found")

        when {
            segments.size == 1 && request.method == "GET" -> respond(out, 200, "text/html; charset=utf-8", PAGE)
            segments.size == 2 && segments[1] == "upload" && request.method == "POST" -> receive(request, input, out)
            else -> respond(out, 404, "text/plain", "Not found")
        }
    }

    private fun receive(request: Head, input: InputStream, out: OutputStream) {
        val type = request.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (type !in ALLOWED_TYPES) return respondJson(out, 415, false, "Please send a JPEG, PNG or WebP image.")
        val length = request.headers["content-length"]?.toLongOrNull()
            ?: return respondJson(out, 411, false, "The upload had no size.")
        if (length <= 0 || length > MAX_BYTES) return respondJson(out, 413, false, "That image is too big (25 MB at most).")
        if (!busy.compareAndSet(false, true)) return respondJson(out, 429, false, "Another photo is still being sent.")

        val file = File(workDir, "upload-${System.nanoTime()}.tmp")
        try {
            workDir.mkdirs()
            file.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) throw IOException("Upload ended early")
                    sink.write(buffer, 0, n)
                    remaining -= n
                }
            }
            if (!looksLikeImage(file)) return respondJson(out, 415, false, "That file isn't a JPEG, PNG or WebP image.")
            onUpload(file)
            respondJson(out, 200, true, "Done! It's on your TV.")
        } catch (e: Exception) {
            Log.w(TAG, "Upload failed", e)
            respondJson(out, 500, false, e.message ?: "The TV couldn't use that image.")
        } finally {
            file.delete()
            busy.set(false)
        }
    }

    /** Checks the file's first bytes, so a renamed file of another kind is turned away. */
    private fun looksLikeImage(file: File): Boolean {
        val head = ByteArray(12)
        val n = file.inputStream().use { it.read(head) }
        if (n < 12) return false
        val jpeg = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        val png = head[0] == 0x89.toByte() && String(head, 1, 3, Charsets.US_ASCII) == "PNG"
        val webp = String(head, 0, 4, Charsets.US_ASCII) == "RIFF" && String(head, 8, 4, Charsets.US_ASCII) == "WEBP"
        return jpeg || png || webp
    }

    private fun sameToken(candidate: String) =
        MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))

    private class Head(val method: String, val path: String, val headers: Map<String, String>)

    /** Reads the request line and headers (up to 16 KB) and leaves the stream at the body. */
    private fun readHead(input: InputStream): Head? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val b = input.read()
            if (b < 0 || buffer.size() > 16 * 1024) return null
            buffer.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && b == '\n'.code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
        }
        val lines = buffer.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 3) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase(Locale.ROOT) to line.substring(i + 1).trim()
        }.toMap()
        return Head(parts[0], parts[1], headers)
    }

    private fun respondJson(out: OutputStream, code: Int, ok: Boolean, message: String) {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        respond(out, code, "application/json", """{"ok":$ok,"message":"$escaped"}""")
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 411 -> "Length Required"
            413 -> "Payload Too Large"; 415 -> "Unsupported Media Type"; 429 -> "Too Many Requests"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            // The token is in the address; don't hand it to anything the page links to.
            "Referrer-Policy: no-referrer\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(bytes)
            out.flush()
        }
    }

    companion object {
        private const val TAG = "Nyumbani"
        private const val PREFERRED_PORT = 8765
        private const val MAX_BYTES = 25L * 1024 * 1024
        private val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        private fun randomToken(): String {
            val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            return (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }

        /**
         * The TV's private IPv4 address. Wired (eth) first, then Wi-Fi, then anything else that is
         * up; works on Ethernet, unlike WifiManager.
         */
        fun lanAddress(): InetAddress? {
            val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }.getOrDefault(false) }
                .sortedBy {
                    when {
                        it.name.startsWith("eth") -> 0
                        it.name.startsWith("wlan") -> 1
                        else -> 2
                    }
                }
            val candidates = interfaces.flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            return candidates.firstOrNull { it.isSiteLocalAddress } ?: candidates.firstOrNull()
        }

        /** The page the phone sees: pick a photo, preview it, send it. */
        private val PAGE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nyumbani wallpaper</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font: 16px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
         background: #0e0f1f; color: #fff; }
  main { max-width: 480px; margin: 0 auto; padding: 28px 20px 40px; }
  h1 { font-weight: 300; font-size: 26px; margin: 0 0 6px; }
  p { color: rgba(255,255,255,.75); margin: 0 0 20px; }
  label.pick { display: block; text-align: center; padding: 16px; border-radius: 16px;
               border: 1.5px dashed rgba(255,255,255,.35); cursor: pointer; }
  input[type=file] { display: none; }
  img { display: none; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 14px; margin: 18px 0 0; }
  button { width: 100%; margin-top: 18px; padding: 15px; border: 0; border-radius: 999px; font: inherit;
           font-weight: 600; background: #f2a866; color: #0e0f1f; }
  button:disabled { opacity: .4; }
  progress { width: 100%; margin-top: 16px; display: none; accent-color: #f2a866; }
  #status { margin-top: 14px; min-height: 1.5em; color: rgba(255,255,255,.85); }
  #status.error { color: #ff9b8a; }
</style>
</head>
<body>
<main>
  <h1>Send a wallpaper</h1>
  <p>Pick a photo. It's shown on your TV straight away, cropped to fill the screen.</p>
  <label class="pick" for="file">Choose a photo</label>
  <input id="file" type="file" accept="image/jpeg,image/png,image/webp">
  <img id="preview" alt="Preview">
  <button id="send" disabled>Send to TV</button>
  <progress id="progress" max="100" value="0"></progress>
  <div id="status"></div>
</main>
<script>
  const MAX = 25 * 1024 * 1024;
  const allowed = ["image/jpeg", "image/png", "image/webp"];
  const file = document.getElementById("file"), preview = document.getElementById("preview");
  const send = document.getElementById("send"), status = document.getElementById("status");
  const progress = document.getElementById("progress");
  let chosen = null;

  function say(text, error) { status.textContent = text; status.className = error ? "error" : ""; }

  file.addEventListener("change", () => {
    chosen = file.files[0] || null;
    send.disabled = true;
    if (!chosen) return;
    if (!allowed.includes(chosen.type)) { say("Please choose a JPEG, PNG or WebP image.", true); chosen = null; return; }
    if (chosen.size > MAX) { say("That photo is over 25 MB. Please choose a smaller one.", true); chosen = null; return; }
    preview.src = URL.createObjectURL(chosen);
    preview.style.display = "block";
    send.disabled = false;
    say("");
  });

  send.addEventListener("click", () => {
    if (!chosen) return;
    send.disabled = true;
    progress.style.display = "block";
    progress.value = 0;
    say("Sending…");
    const xhr = new XMLHttpRequest();
    xhr.open("POST", location.pathname.replace(/\/$/, "") + "/upload");
    xhr.setRequestHeader("Content-Type", chosen.type);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) progress.value = e.loaded / e.total * 100; };
    xhr.onload = () => {
      let reply = {};
      try { reply = JSON.parse(xhr.responseText); } catch (e) {}
      progress.style.display = "none";
      send.disabled = false;
      say(reply.message || (xhr.status === 200 ? "Done!" : "Something went wrong."), !reply.ok);
    };
    xhr.onerror = () => {
      progress.style.display = "none";
      send.disabled = false;
      say("Couldn't reach the TV. Is the Send from phone screen still open?", true);
    };
    xhr.send(chosen);
  });
</script>
</body>
</html>
""".trimIndent()
    }
}
