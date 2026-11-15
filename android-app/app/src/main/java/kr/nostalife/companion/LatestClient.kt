package kr.nostalife.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.io.ByteArrayOutputStream
import org.json.JSONObject

class FetchException(val userMessage: String) : Exception(userMessage)
fun interface LatestReader { suspend fun fetch(connection: Connection): Latest }

class HttpLatestReader : LatestReader {
    private var lastReceipt: String? = null
    override suspend fun fetch(connection: Connection): Latest = withContext(Dispatchers.IO) {
        var request: HttpURLConnection? = null
        try {
            if (connection.requiresToken && connection.token.isEmpty()) throw FetchException("연결 설정에서 데모 토큰을 다시 입력하세요. 토큰은 앱 종료 후 저장하지 않습니다.")
            request = URL(connection.latestUrl).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            request.requestMethod = "GET"
            request.instanceFollowRedirects = false
            request.connectTimeout = 5000
            request.readTimeout = 8000
            request.useCaches = false
            request.setRequestProperty("Accept", "application/json")
            request.setRequestProperty("User-Agent", "Nostalife-Pleos/5.1")
            request.setRequestProperty("X-Nostalife-App", "pleos")
            if (connection.hosted) request.setRequestProperty("X-Driving-Lab-Client", "pleos")
            if (connection.token.isNotEmpty()) request.setRequestProperty("Authorization", "Bearer " + connection.token)
            val status = request.responseCode
            if (status == 401 || status == 403) throw FetchException("연결이 허용되지 않았습니다. 데모 토큰과 PC 서버 설정을 확인하세요.")
            if (status in 300..399) throw FetchException("서버의 리디렉션을 차단했습니다. PC의 기본 주소를 직접 입력하세요.")
            if (status != 200 && status != 404) throw FetchException("PC 서버가 오류를 반환했습니다 (HTTP $status). 서버를 확인하고 다시 시도하세요.")
            val stream = if (status == 200) request.inputStream else request.errorStream
                ?: throw ContractException()
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_048_576) throw ContractException("결과 응답이 허용 크기(1 MiB)를 넘었습니다.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val parsed = RecommendationContract.parse(status, bytes.toString(Charsets.UTF_8))
            if (connection.hosted && parsed is Latest.Found) {
                val receipt = connection.baseUrl + "/" + parsed.result.id
                if (lastReceipt != receipt && acknowledge(connection, parsed.result.id)) lastReceipt = receipt
            }
            parsed
        } catch (error: FetchException) { throw error }
        catch (error: ContractException) { throw error }
        catch (_: SocketTimeoutException) { throw FetchException("연결 시간이 초과되었습니다. PC와 서버가 켜져 있는지 확인하세요.") }
        catch (_: java.io.IOException) { throw FetchException(if (connection.hosted) "인터넷 연결을 확인해 주세요. 연결이 돌아오면 다시 불러옵니다." else "PC 분석 서버에 연결할 수 없습니다. 서버를 실행하거나 웹 연결 코드를 입력하세요.") }
        finally { request?.disconnect() }
    }
    private fun acknowledge(connection: Connection, id: String): Boolean {
        var request: HttpURLConnection? = null
        return try {
            request = URL(connection.baseUrl + "/received").openConnection(Proxy.NO_PROXY) as HttpURLConnection
            request.requestMethod = "POST"
            request.instanceFollowRedirects = false
            request.connectTimeout = 2500
            request.readTimeout = 2500
            request.doOutput = true
            request.setRequestProperty("Content-Type", "application/json")
            request.setRequestProperty("User-Agent", "Nostalife-Pleos/5.1")
            val bytes = JSONObject().put("recommendation_id", id).toString().toByteArray(Charsets.UTF_8)
            request.setFixedLengthStreamingMode(bytes.size)
            request.outputStream.use { it.write(bytes) }
            request.responseCode == 200
        } catch (_: java.io.IOException) { false }
        finally { request?.disconnect() }
    }
}
