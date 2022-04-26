package com.lepu.health.util

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*

/**
  *
  * 文字转语音
  * zrj 2020/10/31
 */
class Gossip(private val context: Context): TextToSpeech.OnInitListener  {

    val textToSpeech: TextToSpeech = TextToSpeech(context, this)

    private var activity: Activity? = null

    private var initialized = false
    var isMuted = false
        private set
    private var playOnInit: String = ""
    private var queueMode = TextToSpeech.QUEUE_FLUSH

    private val onStartJobs = HashMap<String, suspend () -> Unit>()
    private val onDoneJobs = HashMap<String, suspend () -> Unit>()
    private val onErrorJobs = HashMap<String, suspend () -> Unit>()

    val availableLanguages: Set<Locale>
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        get() = textToSpeech.availableLanguages


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            playInternal(playOnInit, UTTERANCE_ID_NONE)
        } else {
            LogUtil.d("Initialization failed.")
        }
    }

    fun setActivity(activity: Activity) {
        this.activity = activity
        enableVolumeControl(this.activity)
    }

    fun talk(text: CharSequence) {
        talk(text.toString(), null, null, null)
    }

    fun talkAndOnStart(text: String, onStart: (suspend () -> Unit)?) {
        talk(text, onStart, null, null)
    }

    fun talkAndOnDone(text: String, onDone: (suspend () -> Unit)?) {
        talk(text, null, onDone, null)
    }

    fun talkAndOnError(text: String, onError: (suspend () -> Unit)?) {
        talk(text, null, null, onError)
    }

    @Synchronized
    fun talk(
        text: String,
        onStart: (suspend () -> Unit)?,
        onDone: (suspend () -> Unit)?,
        onError: (suspend () -> Unit)?
    ) {
        if (initialized) {
            val utteranceId = UUID.randomUUID().toString()
            if (onStart != null) {
                onStartJobs[utteranceId] = onStart
            }
            if (onDone != null) {
                onDoneJobs[utteranceId] = onDone
            }
            if (onError != null) {
                onErrorJobs[utteranceId] = onError
            }
            playInternal(text, utteranceId)
        } else {
            playOnInit = text
        }
    }

    fun stop() {
        if (::job.isInitialized) {
            job.cancel()
        }
        textToSpeech.stop()
    }

    open fun isPlaying(): Boolean = textToSpeech.isSpeaking

    private fun playInternal(text: String, utteranceId: String) {
        if (isMuted) {
            return
        }
        LogUtil.d("utteranceId: $utteranceId")
        LogUtil.d("Playing: $text")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
           val result = textToSpeech.speak(text, queueMode, null, utteranceId)
            LogUtil.d("result: $result")  //SUCCESS 0  ERROR -1
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            textToSpeech.speak(text, queueMode, params)
        }
    }

    fun mute() {
        isMuted = true
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
    }

    fun unmute() {
        isMuted = false
    }

    fun setSpeed(speed1: Float) {
        textToSpeech.setSpeechRate(speed1)
    }

    fun requestAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(
            audioFocusChangeListener, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    fun abandonAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocus(audioFocusChangeListener)
    }

    fun enableVolumeControl(activity: Activity?) {
        if (activity != null) {
            activity.volumeControlStream = AudioManager.STREAM_MUSIC
        }
    }

    fun disableVolumeControl(activity: Activity?) {
        if (activity != null) {
            activity.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        }
    }

    fun setQueueMode(queueMode: Int) {
        this.queueMode = queueMode
    }

    fun setLanguage(locale: Locale) {
        val er = textToSpeech.setLanguage(locale)
        if (er == TextToSpeech.LANG_MISSING_DATA || er == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.language = Locale.getDefault()
        }
    }


    /**
     * 关闭[TextToSpeech]对象并注销活动生命周期回调
     */
    fun shutdown() {
        textToSpeech.shutdown()
        if (::job.isInitialized) {
            job.cancel()
        }
    }

    /**
     * Find the runnable for a given utterance id, run it on the main thread and then remove
     * it from the map
     * @param utteranceId the id key to use
     * @param hashMap utteranceIds to runnable map to use
     * @return whether value was found
     */
    lateinit var job: Job

    @Synchronized
    private fun detectAndRun(utteranceId: String, hashMap: HashMap<String, suspend () -> Unit>): Boolean {
        return if (hashMap.containsKey(utteranceId)) {
            job = Job()
            val call = hashMap[utteranceId]
            val scope = CoroutineScope(Dispatchers.Main + job)
            scope.launch {
                call?.invoke()
            }
            true
        } else {
            false
        }
    }

    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> textToSpeech.setPitch(FOCUS_PITCH)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> textToSpeech.setPitch(DUCK_PITCH)
            }
        }


    private var utteranceProgressListener: UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            LogUtil.e("onStart  $utteranceId")
            detectAndRun(utteranceId, onStartJobs)
        }

        override fun onDone(utteranceId: String) {
            LogUtil.e("onDone  $utteranceId")
            if (detectAndRun(utteranceId, onDoneJobs)) {
                if (onErrorJobs.containsKey(utteranceId)) {
                    onErrorJobs.remove(utteranceId)
                }
            }
        }

        override fun onError(utteranceId: String) {
            LogUtil.e("onError  $utteranceId")
            if (detectAndRun(utteranceId, onErrorJobs)) {
                if (onDoneJobs.containsKey(utteranceId)) {
                    onDoneJobs.remove(utteranceId)
                }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            super.onError(utteranceId, errorCode)
            LogUtil.e("errorCode  $errorCode")

        }
    }

    init {
        this.textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener)
    }

    companion object {
        /**
         * 当我们集中精力时
         */
        private val FOCUS_PITCH = 1.0f
        /**
         *当我们应该回避其他应用程序的音频时
         */
        private val DUCK_PITCH = 0.5f
        /**
         * 不说文字时的ID
         */
        val UTTERANCE_ID_NONE = "-1"
    }


}
