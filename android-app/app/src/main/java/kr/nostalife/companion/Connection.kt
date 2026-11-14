package kr.nostalife.companion

import java.net.URI

class Connection private constructor(val baseUrl: String, val token: String, val requiresToken: Boolean) {
    val hosted: Boolean get() = baseUrl.startsWith(PUBLIC_ORIGIN + "/api/channels/")
    val latestUrl: String get() = baseUrl + if (hosted) "/latest" else "/recommendations/latest"
    override fun toString(): String = baseUrl // Never expose the token in diagnostics.
    companion object {
        const val EMULATOR_URL = "http://10.0.2.2:4180"
        const val PUBLIC_ORIGIN = "https://nostalife-drive.rusticglint.chatgpt.site"
        fun normalizeCode(value: String): String = value.uppercase().replace("-", "").replace(" ", "").also {
            require(Regex("[A-HJ-NP-Z2-9]{10}").matches(it)) { "웹 화면의 연결 코드 10자리를 입력하세요." }
        }
        fun publicAddress(code: String): String = PUBLIC_ORIGIN + "/api/channels/" + normalizeCode(code)
        fun create(address: String, token: String, debug: Boolean, allowMissingToken: Boolean = false): Connection {
            val uri = try { URI(address.trim().trimEnd('/')) } catch (_: Exception) { throw IllegalArgumentException("서버 주소를 확인해 주세요.") }
            val scheme = uri.scheme?.lowercase()
            require(scheme in setOf("http", "https")) { "http:// 또는 https://로 시작하는 주소를 입력하세요." }
            require(debug || scheme == "https") { "HTTP 연결은 디버그 APK에서만 사용할 수 있습니다." }
            if (scheme == "https" && uri.host == "nostalife-drive.rusticglint.chatgpt.site" && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                Regex("/api/channels/[A-HJ-NP-Z2-9]{10}").matches(uri.rawPath ?: "")) {
                return Connection(PUBLIC_ORIGIN + uri.rawPath, "", false)
            }
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()) {
                "서버의 기본 주소만 입력하세요. 경로·계정·쿼리는 넣지 않습니다."
            }
            val host = uri.host?.lowercase() ?: throw IllegalArgumentException("서버의 IPv4 주소를 입력하세요.")
            val octets = host.split('.').map { it.toIntOrNull() }
            val ip = if (octets.size == 4 && octets.all { it != null && it in 0..255 } &&
                octets.joinToString(".") == host) octets.map { it!! } else null
            val local = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            val privateIp = ip != null && (ip[0] == 10 || (ip[0] == 172 && ip[1] in 16..31) || (ip[0] == 192 && ip[1] == 168))
            require(local || privateIp) { "로컬 데모는 PC의 사설 IPv4 주소와 에뮬레이터 주소만 지원합니다." }
            require(uri.port == -1 || uri.port in 1..65535) { "포트는 1~65535 범위여야 합니다." }
            val cleanToken = token.trim()
            require(cleanToken.isEmpty() || Regex("[A-Za-z0-9_-]{32,256}").matches(cleanToken)) { "데모 토큰은 공백 없는 영문·숫자·-·_ 32~256자입니다." }
            require(local || cleanToken.isNotEmpty() || allowMissingToken) { "같은 Wi-Fi의 PC에 연결하려면 데모 토큰을 입력하세요." }
            val port = if (uri.port == -1) "" else ":" + uri.port
            return Connection("$scheme://$host$port", cleanToken, !local)
        }
    }
}
