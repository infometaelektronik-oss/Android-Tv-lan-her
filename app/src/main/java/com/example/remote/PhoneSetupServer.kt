package com.example.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import com.example.data.LauncherPrefs
import com.example.data.WeatherRepository
import com.example.tv.TvInputHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class PhoneSetupServer(
    private val context: Context,
    private val prefs: LauncherPrefs,
    private val weatherRepo: WeatherRepository,
    private val scope: CoroutineScope,
    private val onConfigApplied: () -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun start(): String {
        stop()
        val socket = try {
            ServerSocket(8765)
        } catch (_: Throwable) {
            ServerSocket(0)
        }
        serverSocket = socket
        val port = socket.localPort
        val ip = getLocalIpAddress(context)

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleClient(client) }
                } catch (_: Throwable) {
                    break
                }
            }
        }
        return "http://$ip:$port"
    }

    fun stop() {
        runCatching { serverJob?.cancel() }
        runCatching { serverSocket?.close() }
        serverJob = null
        serverSocket = null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 360): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (method == "POST" && contentLength > 0 && contentLength < 256 * 1024) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n <= 0) break
                    read += n
                }
                String(buf, 0, read)
            } else {
                ""
            }

            when {
                method == "GET" && path == "/api/state" -> {
                    writeJson(sock, 200, buildStateJson().toString())
                }
                method == "POST" && path == "/api/save" -> {
                    val ok = applyConfigJson(body)
                    if (ok) {
                        scope.launch(Dispatchers.Main) { onConfigApplied() }
                        writeJson(sock, 200, """{"ok":true}""")
                    } else {
                        writeJson(sock, 400, """{"ok":false}""")
                    }
                }
                else -> {
                    writeHtml(sock, 200, buildMobileHtml())
                }
            }
        }
    }

    private fun buildStateJson(): JSONObject {
        val allApps = TvInputHelper.queryInstalledApps(context, includeHidden = true, hiddenSet = emptySet())
        val hidden = prefs.hiddenPackages
        val pinned = prefs.pinnedPackages

        val appsArr = JSONArray()
        allApps.forEach { app ->
            appsArr.put(
                JSONObject().apply {
                    put("pkg", app.packageName)
                    put("label", app.label)
                    put("hidden", hidden.contains(app.packageName))
                    put("pinned", pinned.contains(app.packageName))
                }
            )
        }

        return JSONObject().apply {
            put("cityAuto", prefs.cityModeAuto)
            put("cityName", prefs.cityName)
            put("largeText", prefs.largeTextMode)
            put("ytMode", prefs.youtubeSourceMode)
            put("ytChannels", prefs.youtubeChannelIds)
            put("ytApiKey", prefs.youtubeApiKey)
            put("unsplashKey", prefs.unsplashAccessKey)
            put("pexelsKey", prefs.pexelsApiKey)
            put("screensaverMin", prefs.screensaverMinutes)
            put("cardOrder", JSONArray(prefs.cardOrderIds))
            put("apps", appsArr)
        }
    }

    private suspend fun applyConfigJson(raw: String): Boolean {
        return try {
            val json = JSONObject(raw)
            val cityAuto = json.optBoolean("cityAuto", prefs.cityModeAuto)
            val cityInput = json.optString("cityName", prefs.cityName).trim()
            prefs.cityModeAuto = cityAuto
            if (!cityAuto && cityInput.isNotEmpty() && cityInput != prefs.cityName) {
                val found = weatherRepo.searchCities(cityInput).firstOrNull()
                if (found != null) {
                    prefs.cityName = found.name
                    prefs.cityLat = found.latitude
                    prefs.cityLon = found.longitude
                } else {
                    prefs.cityName = cityInput
                }
            }
            prefs.clearWeatherCacheTimestamp()

            prefs.largeTextMode = json.optBoolean("largeText", prefs.largeTextMode)
            prefs.youtubeSourceMode = json.optString("ytMode", prefs.youtubeSourceMode)
            prefs.youtubeChannelIds = json.optString("ytChannels", prefs.youtubeChannelIds)
            prefs.youtubeApiKey = json.optString("ytApiKey", prefs.youtubeApiKey)
            prefs.unsplashAccessKey = json.optString("unsplashKey", prefs.unsplashAccessKey)
            prefs.pexelsApiKey = json.optString("pexelsKey", prefs.pexelsApiKey)
            prefs.screensaverMinutes = json.optInt("screensaverMin", prefs.screensaverMinutes)
            prefs.clearYouTubeCacheTimestamp()

            json.optJSONArray("cardOrder")?.let { arr ->
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.optString(i))
                if (list.isNotEmpty()) prefs.cardOrderIds = list
            }
            json.optJSONArray("hiddenPkgs")?.let { arr ->
                val set = HashSet<String>()
                for (i in 0 until arr.length()) set.add(arr.optString(i))
                prefs.hiddenPackages = set
            }
            json.optJSONArray("pinnedPkgs")?.let { arr ->
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.optString(i))
                prefs.pinnedPackages = list
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeHtml(socket: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header =
            "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun writeJson(socket: Socket, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header =
            "HTTP/1.1 $code OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wm?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    private fun buildMobileHtml(): String = """
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>SadeTV Uzaktan Kurulum</title>
<style>
  body{font-family:-apple-system,system-ui,sans-serif;background:#0b0e14;color:#f8fafc;margin:0;padding:18px}
  .wrap{max-width:560px;margin:0 auto}
  h1{font-size:22px;margin:0 0 6px}
  p.sub{color:#94a3b8;font-size:13px;margin:0 0 18px}
  .card{background:#171c26;border:1px solid #2b3444;border-radius:14px;padding:16px;margin-bottom:14px}
  label{display:block;font-size:13px;color:#cbd5e1;margin-bottom:6px;font-weight:600}
  input[type=text],select{width:100%;box-sizing:border-box;padding:11px;border-radius:10px;border:1px solid #334155;background:#0f141d;color:#fff;font-size:15px;margin-bottom:12px}
  .row{display:flex;align-items:center;justify-content:space-between;padding:8px 0;border-bottom:1px solid #232b38}
  .row:last-child{border-bottom:none}
  .btn{width:100%;padding:14px;border:none;border-radius:12px;background:#3b82f6;color:#fff;font-size:16px;font-weight:700;cursor:pointer}
  .mini{padding:6px 10px;border-radius:8px;border:1px solid #334155;background:#1e293b;color:#fff;margin-left:6px}
  .status{margin-top:12px;text-align:center;font-weight:600;color:#34d399}
</style>
</head>
<body>
<div class="wrap">
  <h1>SadeTV Uzaktan Kurulum</h1>
  <p class="sub">TV ile aynı Wi-Fi üzerinden şehir, kart sırası ve uygulamaları düzenleyin.</p>
  <div class="card">
    <label><input type="checkbox" id="cityAuto"/> Otomatik Konum (IP ile)</label>
    <label for="cityName">Şehir Adı</label>
    <input type="text" id="cityName" placeholder="Örn: İstanbul, Ankara, İzmir"/>
    <label><input type="checkbox" id="largeText"/> Büyük Yazı Modu (Yaşlılar için)</label>
    <label for="screensaverMin">Boşta Saat Ekranı (Dakika)</label>
    <select id="screensaverMin">
      <option value="0">Kapalı</option>
      <option value="3">3 Dakika</option>
      <option value="5">5 Dakika</option>
      <option value="10">10 Dakika</option>
    </select>
  </div>
  <div class="card">
    <label for="ytMode">YouTube Video Kaynağı</label>
    <select id="ytMode">
      <option value="CHANNEL">Seçili Kanallar (RSS veya API)</option>
      <option value="POPULAR">Genel Popüler (API Anahtarı gerekir)</option>
    </select>
    <label for="ytChannels">YouTube Kanal ID'leri (virgülle)</label>
    <input type="text" id="ytChannels" placeholder="UC..."/>
    <label for="ytApiKey">Opsiyonel YouTube Data API Anahtarı</label>
    <input type="text" id="ytApiKey" placeholder="Boş bırakılabilir"/>
    <label for="unsplashKey">Opsiyonel Unsplash Access Key</label>
    <input type="text" id="unsplashKey" placeholder="Boş bırakılabilir"/>
    <label for="pexelsKey">Opsiyonel Pexels API Key</label>
    <input type="text" id="pexelsKey" placeholder="Boş bırakılabilir"/>
  </div>
  <div class="card">
    <label>Ana Ekran Kart Sırası</label>
    <div id="orderList"></div>
  </div>
  <div class="card">
    <label>Uygulamalar (Ana Ekrana Sabitle / Gizle)</label>
    <div id="appsList">Yükleniyor...</div>
  </div>
  <button class="btn" onclick="saveAll()">TV'ye Kaydet</button>
  <div class="status" id="status"></div>
</div>
<script>
const cardLabels={youtube:"YouTube",television:"Televizyon",tgdizifilm:"TG Dizi Film",resume_last:"Son İzlenen",clear_cache:"Önbellek Temizle",all_apps:"Tüm Uygulamalar"};
let state={};
async function load(){
  const r=await fetch('/api/state'); state=await r.json();
  document.getElementById('cityAuto').checked=state.cityAuto;
  document.getElementById('cityName').value=state.cityName||'';
  document.getElementById('largeText').checked=state.largeText;
  document.getElementById('screensaverMin').value=String(state.screensaverMin||5);
  document.getElementById('ytMode').value=state.ytMode||'CHANNEL';
  document.getElementById('ytChannels').value=state.ytChannels||'';
  document.getElementById('ytApiKey').value=state.ytApiKey||'';
  document.getElementById('unsplashKey').value=state.unsplashKey||'';
  document.getElementById('pexelsKey').value=state.pexelsKey||'';
  renderOrder(); renderApps();
}
function renderOrder(){
  const el=document.getElementById('orderList'); el.innerHTML='';
  state.cardOrder.forEach((id,idx)=>{
    const d=document.createElement('div'); d.className='row';
    d.innerHTML='<span>'+(cardLabels[id]||id)+'</span><div><button class="mini" onclick="move('+idx+',-1)">↑</button><button class="mini" onclick="move('+idx+',1)">↓</button></div>';
    el.appendChild(d);
  });
}
function move(i,dir){
  const j=i+dir; if(j<0||j>=state.cardOrder.length)return;
  const t=state.cardOrder[i]; state.cardOrder[i]=state.cardOrder[j]; state.cardOrder[j]=t;
  renderOrder();
}
function renderApps(){
  const el=document.getElementById('appsList'); el.innerHTML='';
  state.apps.forEach((a,idx)=>{
    const d=document.createElement('div'); d.className='row';
    d.innerHTML='<span>'+a.label+'</span><div><label style="display:inline;margin-right:10px"><input type="checkbox" '+(a.pinned?'checked':'')+' onchange="state.apps['+idx+'].pinned=this.checked"> Sabitle</label><label style="display:inline"><input type="checkbox" '+(a.hidden?'checked':'')+' onchange="state.apps['+idx+'].hidden=this.checked"> Gizle</label></div>';
    el.appendChild(d);
  });
}
async function saveAll(){
  const payload={
    cityAuto:document.getElementById('cityAuto').checked,
    cityName:document.getElementById('cityName').value,
    largeText:document.getElementById('largeText').checked,
    screensaverMin:parseInt(document.getElementById('screensaverMin').value,10),
    ytMode:document.getElementById('ytMode').value,
    ytChannels:document.getElementById('ytChannels').value,
    ytApiKey:document.getElementById('ytApiKey').value,
    unsplashKey:document.getElementById('unsplashKey').value,
    pexelsKey:document.getElementById('pexelsKey').value,
    cardOrder:state.cardOrder,
    hiddenPkgs:state.apps.filter(a=>a.hidden).map(a=>a.pkg),
    pinnedPkgs:state.apps.filter(a=>a.pinned).map(a=>a.pkg)
  };
  const r=await fetch('/api/save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  document.getElementById('status').innerText=r.ok?'Ayarlar TV\'ye uygulandı!':'Hata oluştu';
}
load();
</script>
</body>
</html>
""".trimIndent()
}
