/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonegap.plugins.barcodescanner.scanner

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build.VERSION_CODES
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.android.gms.tasks.Tasks
import com.google.android.odml.image.ByteBufferMlImageBuilder
import com.google.android.odml.image.MlImage
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.phonegap.plugins.barcodescanner.FrameMetadata
import com.phonegap.plugins.barcodescanner.GraphicOverlay
import com.phonegap.plugins.barcodescanner.VisionImageProcessor
import org.apache.cordova.LOG
import java.nio.ByteBuffer

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement {@link
 * #onSuccess(T, FrameMetadata, GraphicOverlay)} to define what they want to with the detection
 * results and {@link #detectInImage(VisionImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
abstract class VisionProcessorBase<T>(context: Context) : VisionImageProcessor {

	companion object {
		const val MANUAL_TESTING_LOG = "LogTagForTest"
		private const val TAG = "VisionProcessorBase"
	}

	private var activityManager: ActivityManager =
		context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
	private val executor =
		ScopedExecutor(
			TaskExecutors.MAIN_THREAD
		)

	// Whether this processor is already shut down
	private var isShutdown = false


	// To keep the latest images and its metadata.
	@GuardedBy("this")
	private var latestImage: ByteBuffer? = null

	@GuardedBy("this")
	private var latestImageMetaData: FrameMetadata? = null

	// To keep the images and metadata in process.
	@GuardedBy("this")
	private var processingImage: ByteBuffer? = null

	@GuardedBy("this")
	private var processingMetaData: FrameMetadata? = null

	// -----------------Code for processing single still image----------------------------------------
	override fun processBitmap(bitmap: Bitmap?, graphicOverlay: GraphicOverlay) {
		val frameStartMs = SystemClock.elapsedRealtime()

		requestDetectInImage(
			InputImage.fromBitmap(bitmap!!, 0),
			graphicOverlay,            /* originalCameraImage= */            /* shouldShowFps= */
			frameStartMs
		)
	}

	// -----------------Code for processing live preview frame from Camera1 API-----------------------
	@Synchronized
	override fun processByteBuffer(
		data: ByteBuffer?, frameMetadata: FrameMetadata?, graphicOverlay: GraphicOverlay
	) {
		latestImage = data
		latestImageMetaData = frameMetadata
		if (processingImage == null && processingMetaData == null) {
			processLatestImage(graphicOverlay)
		}
	}

	@Synchronized
	private fun processLatestImage(graphicOverlay: GraphicOverlay) {
		processingImage = latestImage
		processingMetaData = latestImageMetaData
		latestImage = null
		latestImageMetaData = null
		if (processingImage != null && processingMetaData != null && !isShutdown) {
			processImage(processingImage!!, processingMetaData!!, graphicOverlay)
		}
	}

	private fun processImage(
		data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay
	) {
		val frameStartMs = SystemClock.elapsedRealtime()
		// If live viewport is on (that is the underneath surface view takes care of the camera preview
		// drawing), skip the unnecessary bitmap creation that used for the manual preview drawing.
		val bitmap: Bitmap? = null
		//if (PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.context)) null
		//else BitmapUtils.getBitmap(data, frameMetadata)

		if (isMlImageEnabled(graphicOverlay.context)) {
			val mlImage = ByteBufferMlImageBuilder(
				data, frameMetadata.width, frameMetadata.height, MlImage.IMAGE_FORMAT_NV21
			).setRotation(frameMetadata.rotation).build()
			requestDetectInImage(
				mlImage, graphicOverlay,  /* shouldShowFps= */
				frameStartMs
			).addOnSuccessListener(executor) { processLatestImage(graphicOverlay) }

			// This is optional. Java Garbage collection can also close it eventually.
			mlImage.close()
			return
		}

		requestDetectInImage(
			InputImage.fromByteBuffer(
				data,
				frameMetadata.width,
				frameMetadata.height,
				frameMetadata.rotation,
				InputImage.IMAGE_FORMAT_NV21
			), graphicOverlay,            /* shouldShowFps= */
			frameStartMs
		).addOnSuccessListener(executor) { processLatestImage(graphicOverlay) }
	}

	// -----------------Code for processing live preview frame from CameraX API-----------------------
	@RequiresApi(VERSION_CODES.LOLLIPOP)
	@ExperimentalGetImage
	override fun processImageProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay?) {
		val frameStartMs = SystemClock.elapsedRealtime()
		val image = imageProxy.image

		if (isShutdown || image == null || graphicOverlay == null) {
			return
		}

		requestDetectInImage(
			InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees),
			graphicOverlay,            /* originalCameraImage= */            /* shouldShowFps= */
			frameStartMs
		)
			// When the image is from CameraX analysis use case, must call image.close() on received
			// images when finished using them. Otherwise, new images may not be received or the camera
			// may stall.
			.addOnCompleteListener { imageProxy.close() }
	}

	// -----------------Common processing logic-------------------------------------------------------
	private fun requestDetectInImage(
		image: InputImage, graphicOverlay: GraphicOverlay, frameStartMs: Long
	): Task<T> {
		return setUpListener(
			detectInImage(image), graphicOverlay
		)
	}

	private fun requestDetectInImage(
		image: MlImage, graphicOverlay: GraphicOverlay, frameStartMs: Long
	): Task<T> {
		return setUpListener(
			detectInImage(image), graphicOverlay
		)
	}

	private fun setUpListener(
		task: Task<T>, graphicOverlay: GraphicOverlay
	): Task<T> {
		return task.addOnSuccessListener(executor, OnSuccessListener { results: T ->
			// Only log inference info once per second. When frameProcessedInOneSecondInterval is
			// equal to 1, it means this is the first frame processed during the current second.
			graphicOverlay.clear()
			this@VisionProcessorBase.onSuccess(results, graphicOverlay)
			graphicOverlay.postInvalidate()
		}).addOnFailureListener(executor, OnFailureListener { e: Exception ->
			graphicOverlay.clear()
			graphicOverlay.postInvalidate()
			val error = "Failed to process. Error: " + e.localizedMessage
			Toast.makeText(
				graphicOverlay.context, """
          $error
          Cause: ${e.cause}
          """.trimIndent(), Toast.LENGTH_SHORT
			).show()
			LOG.d(TAG, error)
			e.printStackTrace()
			this@VisionProcessorBase.onFailure(e)
		})
	}

	override fun stop() {
		executor.shutdown()
		isShutdown = true
	}

	protected abstract fun detectInImage(image: InputImage): Task<T>

	protected open fun detectInImage(image: MlImage): Task<T> {
		return Tasks.forException(
			MlKitException(
				"MlImage is currently not demonstrated for this feature",
				MlKitException.INVALID_ARGUMENT
			)
		)
	}

	protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)

	protected abstract fun onFailure(e: Exception)

	protected open fun isMlImageEnabled(context: Context?): Boolean {
		return false
	}
}
