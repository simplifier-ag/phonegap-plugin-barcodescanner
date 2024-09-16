package com.phonegap.plugins.barcodescanner

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import org.apache.cordova.LOG
import java.io.Closeable
import java.io.IOException

class BeepManager(val activity: Activity, private val shouldPlayBeep: Boolean = true) :
	MediaPlayer.OnErrorListener, Closeable {
	private val TAG: String = BeepManager::class.java.simpleName
	private val BEEP_VOLUME: Float = 0.10f

	private var mediaPlayer: MediaPlayer? = null

	init {
		if (shouldPlayBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
			// so we now play on the music stream.
			activity.volumeControlStream = AudioManager.STREAM_MUSIC
			mediaPlayer = buildMediaPlayer(activity)
		}
	}

	private fun buildMediaPlayer(activity: Context): MediaPlayer? {
		val mediaPlayer = MediaPlayer()
		try {
			activity.resources.openRawResourceFd(R.getRawId(activity, "beep")).use { file ->
				mediaPlayer.setDataSource(
					file.fileDescriptor,
					file.startOffset,
					file.length
				)
				mediaPlayer.setOnErrorListener(this)
				mediaPlayer.setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.build()
				)
				mediaPlayer.isLooping = false
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME)
				mediaPlayer.prepare()
				return mediaPlayer
			}
		} catch (ioe: IOException) {
			LOG.w(TAG, ioe)
			mediaPlayer.release()
			return null
		}
	}

	@Synchronized
	fun playBeepSound() {
		if (shouldPlayBeep) {
			mediaPlayer?.start()
		}
	}

	@Synchronized
	override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
		if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
			// we are finished, so put up an appropriate error toast if required and finish
			activity.finish()
		} else {
			// possibly media player error, so release and recreate
			close()
		}
		return true
	}

	@Synchronized
	override fun close() {
		if (mediaPlayer != null) {
			mediaPlayer?.release()
			mediaPlayer = null
		}
	}
}