package com.lepu.health.util

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.Notification.EXTRA_CHANNEL_ID
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.lepu.health.R
import java.io.*
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


/**
 * zrj 2019/4/22
 */
object CommonUtil {

     fun getTime(context: Context,percentage: Int, totalTime: Int): String {
        val sec = percentage * totalTime / 100
        val h = sec / 3600
        val temp = (sec % 3600) / 60
        val m = if (temp == 0 && sec % 3600 > 0) {
            "<1"
        } else {
            "$temp"
        }
        return if (h > 0) {
            "$h ${context.getString(R.string.hour)} $m ${context.getString(R.string.min)}"
        } else {
            "$m ${context.getString(R.string.min)}"
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun getPeriod(index: Int): List<String> {
        var startTime = ""
        var endTime = ""
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val c = Calendar.getInstance()
        when (index) {
            1 -> {
                c.time = Date()
                c.add(Calendar.MONTH, -1)
                val m = c.time
                startTime = format.format(m)
            }
            2 -> {
                c.time = Date()
                c.add(Calendar.MONTH, -1)
                val m = c.time
                endTime = format.format(m)
                c.add(Calendar.MONTH, -2)
                startTime = format.format(c.time)
            }
            3 -> {
                c.time = Date()
                c.add(Calendar.MONTH, -3)
                val m = c.time
                endTime = format.format(m)
                c.add(Calendar.MONTH, -3)
                startTime = format.format(c.time)
            }
            4 -> {
                c.time = Date()
                c.add(Calendar.MONTH, -6)
                val m = c.time
                endTime = format.format(m)
                c.add(Calendar.MONTH, -6)
                startTime = format.format(c.time)
            }
        }
        val list = mutableListOf<String>()
        list.add(startTime)
        list.add(endTime)
        return list
    }


    fun getFormattedStopWatchTIme(ms: Long, includeMillis: Boolean = false): String {
        var milliseconds = ms
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        if (!includeMillis) {
            return "${if (hours < 10) "0" else ""}$hours:" +
                    "${if (minutes < 10) "0" else ""}$minutes:" +
                    "${if (seconds < 10) "0" else ""}$seconds"
        }

        milliseconds -= TimeUnit.SECONDS.toMillis(seconds)
        milliseconds /= 10
        return "${if (hours < 10) "0" else ""}$hours:" +
                "${if (minutes < 10) "0" else ""}$minutes:" +
                "${if (seconds < 10) "0" else ""}$seconds:" +
                "${if (milliseconds < 10) "0" else ""}$milliseconds"
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    fun unzip(zipFile: String, descDir: String) {
        val zip = ZipFile(zipFile)
        val entries: Enumeration<*> = zip.entries()
        try {
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipEntry
                val entryName = entry.name.replace("\\", "/")
                if (entryName.contains("../")) {
                    continue
                }
                if (!unzipChildFile(descDir, zip, entry, entryName)) return
            }
        } finally {
            zip.close()
        }
    }


    @Throws(IOException::class)
    private fun unzipChildFile(
        destDir: String,
        zip: ZipFile,
        entry: ZipEntry,
        name: String
    ): Boolean {
        val file = File(destDir, name)
        if (entry.isDirectory) {
            return createOrExistsDir(file)
        } else {
            if (!createOrExistsFile(file)) return false
            var `in`: InputStream? = null
            var out: OutputStream? = null
            try {
                `in` = BufferedInputStream(zip.getInputStream(entry))
                out = BufferedOutputStream(FileOutputStream(file))
                val buffer = ByteArray(8912)
                var len: Int
                while (`in`.read(buffer).also { len = it } != -1) {
                    out.write(buffer, 0, len)
                }
            } finally {
                `in`?.close()
                out?.close()
            }
        }
        return true
    }

    private fun createOrExistsFile(file: File?): Boolean {
        if (file == null) return false
        if (file.exists()) return file.isFile
        return if (!createOrExistsDir(file.parentFile)) false else try {
            file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun createOrExistsDir(file: File?): Boolean {
        return file != null && if (file.exists()) file.isDirectory else file.mkdirs()
    }

    fun compressImage(image: Bitmap): Bitmap? {
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos) //质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        var options = 100
        while (baos.toByteArray().size / 1024 > 100) {  //循环判断如果压缩后图片是否大于100kb,大于继续压缩
            baos.reset() //重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, options, baos) //这里压缩options%，把压缩后的数据存放到baos中
            options -= 10 //每次都减少10
        }
        val isBm = ByteArrayInputStream(baos.toByteArray()) //把压缩后的数据baos存放到ByteArrayInputStream中
        val opts = BitmapFactory.Options()
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeStream(isBm, null, opts)
    }

    fun accept(context: Context, bitmap: Bitmap): File {
        val path = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)?.absolutePath
            ?: context.filesDir.absolutePath
        val imageFile = File(path, "${System.currentTimeMillis()}.jpg")
        try {
            imageFile.createNewFile()
            val fos = FileOutputStream(imageFile)
            //考虑到图片用于分享，因此选择JPEG格式，同时quality设置为50
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos)
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
            imageFile.delete()
        }
        return imageFile
    }


    fun areNotificationsEnabled(context: Context): Boolean {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            isEnableV19(context)
        } else {
            isEnableV26(context)
        }
    }


    private fun isEnableV19(context: Context): Boolean {
        val CHECK_OP_NO_THROW = "checkOpNoThrow"
        val OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION"
        val mAppOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val appInfo = context.applicationInfo
        val pkg = context.applicationContext.packageName
        val uid = appInfo.uid
        var appOpsClass: Class<*>? = null /* Context.APP_OPS_MANAGER */
        try {
            appOpsClass = Class.forName(AppOpsManager::class.java.name)
            val checkOpNoThrowMethod: Method = appOpsClass.getMethod(
                CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                String::class.java
            )
            val opPostNotificationValue: Field = appOpsClass.getDeclaredField(OP_POST_NOTIFICATION)
            val value = opPostNotificationValue.get(Int::class.java) as Int
            return checkOpNoThrowMethod.invoke(
                mAppOps,
                value,
                uid,
                pkg
            ) as Int == AppOpsManager.MODE_ALLOWED
        } catch (e: ClassNotFoundException) {
        } catch (e: NoSuchMethodException) {
        } catch (e: NoSuchFieldException) {
        } catch (e: InvocationTargetException) {
        } catch (e: IllegalAccessException) {
        } catch (e: java.lang.Exception) {
        }
        return false
    }


    private fun isEnableV26(context: Context): Boolean {
        val appInfo = context.applicationInfo
        val pkg = context.applicationContext.packageName
        val uid = appInfo.uid
        return try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sServiceField: Method =
                notificationManager.javaClass.getDeclaredMethod("getService")
            sServiceField.isAccessible = true
            val sService: Any = sServiceField.invoke(notificationManager)
            val method: Method = sService.javaClass.getDeclaredMethod(
                "areNotificationsEnabledForPackage",
                String::class.java,
                Integer.TYPE
            )
            method.isAccessible = true
            method.invoke(sService, pkg, uid) as Boolean
        } catch (e: java.lang.Exception) {
            true
        }
    }

    //应用显示通知
    fun gotoNotify(context: Context) {
        try {
            // 根据isOpened结果，判断是否需要提醒用户跳转AppInfo页面，去打开App通知权限
            val intent = Intent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //这种方案适用于 API 26, 即8.0（含8.0）以上可以用
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(EXTRA_APP_PACKAGE, context.packageName)
                intent.putExtra(EXTRA_CHANNEL_ID, context.applicationInfo.uid)
            }
            //这种方案适用于 API21——25，即 5.0——7.1 之间的版本可以使用
            intent.putExtra("app_package", context.packageName)
            intent.putExtra("app_uid", context.applicationInfo.uid)

            // 小米6 -MIUI9.6-8.0.0系统，是个特例，通知设置界面只能控制"允许使用通知圆点"——然而这个玩意并没有卵用，我想对雷布斯说：I'm not ok!!!
            //  if ("MI 6".equals(Build.MODEL)) {
            //      intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            //      Uri uri = Uri.fromParts("package", getPackageName(), null);
            //      intent.setData(uri);
            //      // intent.setAction("com.android.settings/.SubSettings");
            //  }
            context.startActivity(intent)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            // 出现异常则跳转到应用设置界面：锤子坚果3——OC105 API25
            val intent = Intent()

            //下面这种方案是直接跳转到当前应用的设置界面。
            //https://blog.csdn.net/ysy950803/article/details/71910806
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            val uri: Uri = Uri.fromParts("package", context.packageName, null)
            intent.data = uri
            context.startActivity(intent)
        }
    }

    fun isLocServiceEnable(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gps || network
    }

    fun notificationListenerEnable(context: Context): Boolean {
        var enable = false
        val flat =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        LogUtil.e(flat ?: "false")
        if (flat != null) {
            enable = flat.contains(context.packageName)
        }
        return enable
    }

    //应用通知使用权
    fun gotoNotificationAccessSetting(context: Context): Boolean {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            try {
                val intent = Intent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val cn = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$NotificationAccessSettingsActivity"
                )
                intent.component = cn
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings")
                context.startActivity(intent)
                return true
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return false
        }
    }
}