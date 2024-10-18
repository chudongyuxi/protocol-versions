import org.gradle.api.Project
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream


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
    downloadAPK(apkLink)
    downloadEden()
    runEden(version)
}

private fun Project.downloadAPK(apkLink: String) {
    val file = File(rootDir, "eden/Eden.apk")
    if (file.exists()) {
        println("APK 存在，取消下载")
        return
    } else {
        println("正在下载 APK")
    }
    file.parentFile.mkdirs()
    val process = ProcessBuilder()
        .command("wget -O eden/Eden.apk $apkLink".split(' '))
        .start()
    val executor = Executors.newSingleThreadExecutor()
    executor.execute {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        try {
            while ((reader.readLine().also { line = it }) != null) {
                println("wget -- $line")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val exitCode: Int = process.waitFor()
    println("wget 已退出，退出码 $exitCode")
    executor.shutdown()
    if (!file.exists()) {
        throw IllegalStateException("APK 下载失败")
    }
}
private fun Project.downloadEden() {
    println("正在下载 Eden")
    val dir = File(rootDir, "eden")
    val conn = URL("https://github.com/MrXiaoM/Eden/releases/download/1.0.5/Eden-1.0.5.zip").openConnection()
    conn.connectTimeout = 30 * 1000
    conn.readTimeout = 30 * 1000
    conn.applyHeaders()
    conn.getInputStream().use { input ->
        println("下载完成，正在解压")
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val fileName = entry.name
                FileOutputStream(File(dir, fileName)).use { out ->
                    out.write(zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
    }
}
private fun Project.runEden(version: String) {
    println("正在执行更新操作")
    val pad = File(rootDir, "master/android_pad/$version.json")
    val phone = File(rootDir, "master/android_phone/$version.json")
    val process = ProcessBuilder()
        .directory(File("eden"))
        .command("dotnet Eden.CLI.dll --phone-override ../master/android_phone/$version.json --pad-override ../master/android_pad/$version.json".split(' '))
        .start()
    val executor = Executors.newSingleThreadExecutor()
    executor.execute {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        try {
            while ((reader.readLine().also { line = it }) != null) {
                println("Eden -- $line")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val exitCode: Int = process.waitFor()
    println("Eden 已退出，退出码 $exitCode")
    executor.shutdown()
    if (!pad.exists() || !phone.exists()) {
        throw IllegalStateException("更新失败，未生成协议信息文件")
    }
    File(rootDir, "commit_flag").writeText(version)
}
