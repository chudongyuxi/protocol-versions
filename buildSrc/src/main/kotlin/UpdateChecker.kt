import org.gradle.api.Project
import java.io.File
import java.net.URL
import java.net.URLConnection


fun URLConnection.applyHeaders() {
    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36 Edg/129.0.0.0")
}
fun get(url: String): String {
    val conn = URL(url).openConnection()
    conn.connectTimeout = 30 * 1000
    conn.readTimeout = 30 * 1000
    conn.applyHeaders()
    return conn.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
}

fun Project.checkUpdate() {
    val index = get("https://im.qq.com/index/#downloadAnchor")
    val regex = Regex("https://[A-Za-z0-9/_.-]+/js/(pc|mobile)[A-Za-z0-9/_.-]+\\.js")
    val jsLink = regex.find(index)?.value ?: throw IllegalStateException("找不到更新链接 $index")
    val js = get(jsLink)
    val regex1 = Regex("\"x64Link\": *\"(https://[A-Za-z0-9/_.-]+\\.apk).*?\"")
    val apkLink = regex1.find(js)?.groupValues?.get(1) ?: throw IllegalStateException("找不到安装包链接 $js")
    val version = Regex("\\d+\\.\\d+\\.\\d+").find(apkLink.substringAfterLast('/'))?.value ?: throw IllegalStateException("无法从安装包链接截取版本号 $apkLink")
    println(apkLink)
    println(version)
    if (File(rootDir, "master/android_pad/$version.json").exists()) {
        println("仓库中已有该版本，无需更新")
        return
    }
    File(rootDir, "download_apk.sh").writeText("wget -O eden/Eden.apk $apkLink")
    File(rootDir, "apk_version").writeText(version)
}
