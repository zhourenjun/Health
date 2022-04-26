package com.lepu.health.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
import com.lepu.health.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer


object PhotoUtils {

    fun saveBitmap2Gallery(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //返回出一个URI
            val insert = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                /*
                这里如果不写的话 默认是保存在 /sdCard/DCIM/Pictures
                 */
                ContentValues()//这里可以啥也不设置 保存图片默认就好了
            ) ?: return false //为空的话 直接失败返回了

            //这个打开了输出流  直接保存图片就好了
            context.contentResolver.openOutputStream(insert).use {
                it ?: return false
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            return true
        } else {
            MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "title", "desc")
            return true
        }
    }

    fun saveBitmap2Gallery2(context: Context, bitmap: Bitmap): Boolean {
        val name = System.currentTimeMillis().toString()
        val photoPath = Environment.DIRECTORY_DCIM + "/Camera"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, photoPath)//保存路径
                put(MediaStore.MediaColumns.IS_PENDING, true)
            }
        }
        val insert = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false //为空的话 直接失败返回了
        //这个打开了输出流  直接保存图片就好了
        context.contentResolver.openOutputStream(insert).use {
            it ?: return false
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, false)
        }
        return true
    }

    fun saveMediaFile2(
        context: Context,
        filePath: String,
        fileName: String,
        isVideo: Boolean = false
    ) {
        val values = ContentValues().apply {
            val folderName = if (isVideo) {
                Environment.DIRECTORY_MOVIES
            } else {
                Environment.DIRECTORY_PICTURES
            }
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    folderName + "/${context.getString(R.string.app_name)}/"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1) //获取此独占访问权限
            }
        }

        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        val fileUri = context.contentResolver.insert(collection, values)

        fileUri?.let {
            if (isVideo) {
                context.contentResolver.openFileDescriptor(fileUri, "w").use { descriptor ->
                    descriptor?.let {
                        FileOutputStream(descriptor.fileDescriptor).use { out ->
                            val videoFile = File(filePath)
                            FileInputStream(videoFile).use { inputStream ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val sz = inputStream.read(buf)
                                    if (sz <= 0) break
                                    out.write(buf, 0, sz)
                                }
                            }
                        }
                    }
                }
            } else {
                context.contentResolver.openOutputStream(fileUri).use { out ->
                    val bmOptions = BitmapFactory.Options()
                    var bmp = BitmapFactory.decodeFile(filePath, bmOptions)
                    //图片旋转
                    val degree = readPictureDegree(filePath)
                    bmp = rotateImageView(degree.toFloat(), bmp)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    bmp.recycle()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //Now that we're finished, release the "pending" status
                values.put(
                    if (isVideo) MediaStore.Video.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING,
                    0
                )
            }
            context.contentResolver.update(fileUri, values, null, null)
        }
    }


    /**
     * 读取图片属性：旋转的角度
     * @param path 图片绝对路径
     * @return degree旋转的角度
     */
    fun readPictureDegree(path: String): Int {
        var degree = 0
        try {
            val exifInterface = ExifInterface(path)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
                ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
                ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return degree
    }


    /*
    * 旋转图片
    */
    private fun rotateImageView(angle: Float, bitmap: Bitmap): Bitmap {
        //旋转图片 动作
        val matrix = Matrix()
        matrix.postRotate(angle)
        // 创建新的图片
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    /**
     * 无声视频添加音频
     * 参考代码：https://stackoverflow.com/questions/31572067/android-how-to-mux-audio-file-and-video-file
     *
     * audioTrack 的 mime type 只支持：
     * MediaFormat.MIMETYPE_AUDIO_AMR_NB,  //.m4a .3gpp
     * MediaFormat.MIMETYPE_AUDIO_AMR_WB,
     * MediaFormat.MIMETYPE_AUDIO_AAC   //.aac .mp4
     */
    @SuppressLint("WrongConstant")
    fun muxAudio(context: Context, mSaveFile: File): File {
        val path =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: context.filesDir.absolutePath
        val saveFile = File(path, "${System.currentTimeMillis()}.mp4")
        if (saveFile.exists()) {
            saveFile.delete()
        }
        try {
            saveFile.createNewFile()
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(mSaveFile.absolutePath)
            val audioExtractor = MediaExtractor()
            val afdd = context.assets.openFd("aigei.m4a")
            audioExtractor.setDataSource(afdd.fileDescriptor, afdd.startOffset, afdd.length)
            val mediaMuxer =
                MediaMuxer(saveFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoExtractor.selectTrack(0)
            val videoFormat = videoExtractor.getTrackFormat(0)
            val videoTrack = mediaMuxer.addTrack(videoFormat)

            audioExtractor.selectTrack(0)
            val audioFormat = audioExtractor.getTrackFormat(0)
            val audioTrack = mediaMuxer.addTrack(audioFormat)
            var sawEOS = false
            var frameCount = 0
            val offset = 100
            val sampleSize =
                2048 * 1024 //不能太小 java.lang.IllegalArgumentException  android.media.MediaExtractor.readSampleData(Native Method)
            val videoBuf = ByteBuffer.allocate(sampleSize)
            val audioBuf = ByteBuffer.allocate(sampleSize)
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            mediaMuxer.start()
            // 每秒多少帧
            val frameRate = videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            // 得出平均每一帧间隔多少微妙
            val videoSampleTime = 1000 * 1000 / frameRate
            while (!sawEOS) {
                videoBufferInfo.offset = offset
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset)
                if (videoBufferInfo.size < 0) {
                    sawEOS = true
                    videoBufferInfo.size = 0
                } else {
                    videoBufferInfo.presentationTimeUs += videoSampleTime
                    videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                    mediaMuxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo)
                    videoExtractor.advance()
                    frameCount++
                }
            }

            var sawEOS2 = false
            var frameCount2 = 0
            while (!sawEOS2) {
                frameCount2++
                audioBufferInfo.offset = offset
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset)
                if (audioBufferInfo.size < 0) {
                    sawEOS2 = true
                    audioBufferInfo.size = 0
                } else {
                    audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    audioBufferInfo.flags = audioExtractor.sampleFlags
                    mediaMuxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo)
                    audioExtractor.advance()
                }
            }

            mediaMuxer.stop()
            mediaMuxer.release()
            videoExtractor.release()
            audioExtractor.release()
            afdd.close()
            // 删除无声视频文件
            mSaveFile.delete()
        } catch (e: Exception) {
            // 视频添加音频合成失败，直接保存视频
            mSaveFile.renameTo(saveFile)
        } finally {
            saveMediaFile2(context, saveFile.absolutePath, File(saveFile.absolutePath).name, true)
            return saveFile
        }
    }
}
