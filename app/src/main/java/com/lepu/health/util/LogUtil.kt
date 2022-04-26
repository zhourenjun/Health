package com.lepu.health.util

import android.annotation.SuppressLint
import android.os.Environment
import android.util.Log
import com.lepu.health.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by zrj 2017/6/10.
 */
object LogUtil {
    private var logEnabled = BuildConfig.DEBUG
    private var tag = "zrj"
    private var isSaveLog = true
    private var pathLogOther = "${Environment.getExternalStorageDirectory().path}/zrj/other"
    private var pathLogNet = "${Environment.getExternalStorageDirectory().path}/zrj/net"
    private var pathLogSerial = "${Environment.getExternalStorageDirectory().path}/zrj/serial"
    private var pathLogRoom = "${Environment.getExternalStorageDirectory().path}/zrj/room"

    //串口
    fun v(msg: String, customTag: String = "Serial") {
        log(Log.VERBOSE, customTag, msg, pathLogSerial)
    }

    //网络有关的日志
    fun d(msg: String, customTag: String = "Net") {
        log(Log.DEBUG, customTag, msg, pathLogNet)
    }

    //数据库
    fun i(msg: String, customTag: String = "Room") {
        log(Log.INFO, customTag, msg, pathLogRoom)
    }

    fun ble(msg: String, customTag: String = tag) {
        if (!logEnabled) return
        val elements = Thread.currentThread().stackTrace
        val index = findIndex(elements)
        val element = elements[index]
        val tag = handleTag(element, customTag)
        Log.println(Log.INFO, tag, msg)
        if (isSaveLog) {
            point(pathLogSerial, tag, msg)
        }
    }

    @Suppress("unused")
    fun w(msg: String, customTag: String = tag) {
        log(Log.WARN, customTag, msg)
    }

    fun e(msg: String, customTag: String = tag) {
        log(Log.ERROR, customTag, msg)
    }

    fun e(msg: Int, customTag: String = tag) {
        log(Log.ERROR, customTag, "$msg")
    }

    fun e(msg: Float, customTag: String = tag) {
        log(Log.ERROR, customTag, "$msg")
    }

    fun e(msg: Long, customTag: String = tag) {
        log(Log.ERROR, customTag, "$msg")
    }

    fun json(msg: String, customTag: String = tag) {
        val json = formatJson(msg)
        log(Log.ERROR, customTag, json)
    }

    /**
     * 格式化json
     */
    private fun formatJson(json: String): String {
        return try {
            val trimJson = json.trim()
            when {
                trimJson.startsWith("{") -> JSONObject(trimJson).toString(4)
                trimJson.startsWith("[") -> JSONArray(trimJson).toString(4)
                else -> trimJson
            }
        } catch (e: JSONException) {
            e.printStackTrace().toString()
        }
    }

    /**
     * 输出日志
     * @param priority 日志级别
     */
    private fun log(priority: Int, customTag: String, msg: String, path: String = pathLogOther) {
        if (!logEnabled) return
        val elements = Thread.currentThread().stackTrace
        val index = findIndex(elements)
        val element = elements[index]
        val tag = handleTag(element, customTag)
        val content = "(${element.fileName}:${element.lineNumber}).${element.methodName}:  $msg"
        Log.println(priority, tag, content)
        if (isSaveLog) {
            point(path, tag, content)
        }
    }


    /**
     * 处理tag逻辑
     */
    private fun handleTag(element: StackTraceElement, customTag: String): String = when {
        customTag.isNotBlank() -> customTag
        else -> element.className.substringAfterLast(".")
    }

    /**
     * 寻找当前调用类在[elements]中的下标
     */
    private fun findIndex(elements: Array<StackTraceElement>): Int {
        var index = 5
        while (index < elements.size) {
            val className = elements[index].className
            if (className != LogUtil::class.java.name && !elements[index].methodName.startsWith("log")) {
                return index
            }
            index++
        }
        return -1
    }

    @SuppressLint("SimpleDateFormat")
    private fun point(path: String, tag: String, msg: String) {
        if (isSaveLog) {
            var out: BufferedWriter? = null
            try {
                val dir = File(path).listFiles()
                if (dir != null && dir.size > 20) {//5*4 5天
                    dir[0].delete()
                }
                val date = Date()
                val dateFormat = SimpleDateFormat("", Locale.SIMPLIFIED_CHINESE)
                val p = "$path${SimpleDateFormat("yyyy-MM-dd").format(Date())}.log"
                dateFormat.applyPattern("[yyyy-MM-dd HH:mm:ss.SSS]")
                val time = dateFormat.format(date)
                val file = File(p)
                if (!file.exists())
                    createDipPath(p)
                out = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true)))
                out.write("$time $tag $msg\r\n")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    out?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 根据文件路径 递归创建文件
     */
    private fun createDipPath(file: String) {
        val parentFile = file.substring(0, file.lastIndexOf("/"))
        val file1 = File(file)
        val parent = File(parentFile)
        if (!file1.exists()) {
            parent.mkdirs()
            try {
                file1.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}


/**
 * 格式化json字符串
 */
fun formatJson(jsonStr: String?): String {
    if (null == jsonStr || "" == jsonStr) return ""
    val sb = StringBuilder()
    var last: Char
    var current = '\u0000'
    var indent = 0
    for (element in jsonStr) {
        last = current
        current = element
        //遇到{ [换行，且下一行缩进
        when (current) {
            '{', '[' -> {
                sb.append(current)
                sb.append('\n')
                indent++
                addIndentBlank(sb, indent)
            }
            //遇到} ]换行，当前行缩进
            '}', ']' -> {
                sb.append('\n')
                indent--
                addIndentBlank(sb, indent)
                sb.append(current)
            }
            //遇到,换行
            ',' -> {
                sb.append(current)
                if (last != '\\') {
                    sb.append('\n')
                    addIndentBlank(sb, indent)
                }
            }
            else -> sb.append(current)
        }
    }
    return sb.toString()
}

/**
 * 添加space
 */
private fun addIndentBlank(sb: StringBuilder, indent: Int) {
    for (i in 0 until indent) {
        sb.append('\t')
    }
}


/**
 * http 请求数据返回 json 中中文字符为 unicode 编码转汉字转码
 */
fun decodeUnicode(theString: String): String {
    var aChar: Char
    val len = theString.length
    val outBuffer = StringBuffer(len)
    var x = 0
    while (x < len) {
        aChar = theString[x++]
        if (aChar == '\\') {
            aChar = theString[x++]
            if (aChar == 'u') {
                var value = 0
                for (i in 0..3) {
                    aChar = theString[x++]
                    value = when (aChar) {
                        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> (value shl 4) + aChar.toInt() - '0'.toInt()
                        'a', 'b', 'c', 'd', 'e', 'f' -> (value shl 4) + 10 + aChar.toInt() - 'a'.toInt()
                        'A', 'B', 'C', 'D', 'E', 'F' -> (value shl 4) + 10 + aChar.toInt() - 'A'.toInt()
                        else -> throw IllegalArgumentException("Malformed   \\uxxxx   encoding.")
                    }

                }
                outBuffer.append(value.toChar())
            } else {
                when (aChar) {
                    't' -> aChar = '\t'
                    'r' -> aChar = '\r'
                    'n' -> aChar = '\n'
                }
                outBuffer.append(aChar)
            }
        } else
            outBuffer.append(aChar)
    }
    return outBuffer.toString()
}

