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

package com.phonegap.plugins.barcodescanner

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.phonegap.plugins.barcodescanner.scanner.BarcodeScannerProcessor
import org.apache.cordova.LOG

/** Live preview demo app for ML Kit APIs using CameraX. */
@KeepName
class BarcodeScanActivity :
	AppCompatActivity(), CompoundButton.OnCheckedChangeListener {

	private var previewView: PreviewView? = null
	private var graphicOverlay: GraphicOverlay? = null
	private var cameraProvider: ProcessCameraProvider? = null
	private var camera: Camera? = null
	private var previewUseCase: Preview? = null
	private var analysisUseCase: ImageAnalysis? = null
	private var imageProcessor: VisionImageProcessor? = null
	private var needUpdateGraphicOverlayImageSourceInfo = false
	private var lensFacing = CameraSelector.LENS_FACING_BACK
	private var cameraSelector: CameraSelector? = null
	private var beepManager: BeepManager? = null

	private var buttonTorch: AppCompatImageButton? = null
	private var isTorchEnabled = false
	private var showTorchButton = true

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		LOG.d(TAG, "onCreate")

		setContentView(
			R.getLayoutId(this, "barcode_scan_activity")
		)

		val lockedOrientation = intent.getStringExtra(Scan.ORIENTATION_LOCK)
		when (lockedOrientation) {
			"landscape" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
			"portrait" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}

		val shouldPlayBeep = intent.getBooleanExtra(Scan.BEEP_ON_SCAN, true)
		beepManager = BeepManager(this, shouldPlayBeep)

		if (intent.getBooleanExtra(Scan.PREFER_FRONT_CAMERA, false)) {
			lensFacing = CameraSelector.LENS_FACING_FRONT
		}

		cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

		previewView = findViewById(R.getId(this, "preview_view"))
		if (previewView == null) {
			LOG.d(TAG, "previewView is null")
		}

		val textPrompt: AppCompatTextView = findViewById(R.getId(this, "text_prompt"))
		val promptText = intent.getStringExtra(Scan.PROMPT_MESSAGE)

		if (promptText != null) {
			textPrompt.text = promptText
			textPrompt.visibility = VISIBLE
		} else {
			textPrompt.visibility = GONE
		}

		// Camera Switch Button
		val buttonCameraSwitch: AppCompatImageButton =
			findViewById(R.getId(this, "button_cameraswitch"))
		val showFacingSwitch = intent.getBooleanExtra(Scan.SHOW_FLIP_CAMERA_BUTTON, true)
		buttonCameraSwitch.visibility = if (showFacingSwitch) VISIBLE else GONE
		buttonCameraSwitch.setOnClickListener {
			if (cameraProvider == null) {
				return@setOnClickListener
			}

			val newLensFacing =
				if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
					CameraSelector.LENS_FACING_BACK
				} else {
					CameraSelector.LENS_FACING_FRONT
				}

			val newCameraSelector =
				CameraSelector.Builder().requireLensFacing(newLensFacing).build()

			try {
				if (cameraProvider!!.hasCamera(newCameraSelector)) {
					LOG.d(TAG, "Set facing to $newLensFacing")
					lensFacing = newLensFacing
					cameraSelector = newCameraSelector
					bindAllCameraUseCases()
					return@setOnClickListener
				}
			} catch (e: CameraInfoUnavailableException) {
				// Falls through
			}

			Toast.makeText(
				applicationContext,
				"This device does not have lens with facing: $newLensFacing",
				Toast.LENGTH_SHORT,
			).show()
		}

		buttonTorch = findViewById(R.getId(this, "button_torch"))
		showTorchButton = intent.getBooleanExtra(Scan.SHOW_FLIP_CAMERA_BUTTON, true)
		buttonTorch?.visibility = if (showTorchButton) VISIBLE else GONE
		buttonTorch?.setOnClickListener {
			camera?.cameraControl?.enableTorch(!isTorchEnabled)
			isTorchEnabled = !isTorchEnabled
		}

		ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
			.get(BarcodeScanViewModel::class.java)
			.processCameraProvider
			.observe(
				this,
			) { provider ->
				cameraProvider = provider
				bindAllCameraUseCases()
			}
	}

	override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
		if (cameraProvider == null) {
			return
		}
		val newLensFacing =
			if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
				CameraSelector.LENS_FACING_BACK
			} else {
				CameraSelector.LENS_FACING_FRONT
			}
		val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()
		try {
			if (cameraProvider!!.hasCamera(newCameraSelector)) {
				LOG.d(TAG, "Set facing to $newLensFacing")
				lensFacing = newLensFacing
				cameraSelector = newCameraSelector
				bindAllCameraUseCases()
				return
			}
		} catch (e: CameraInfoUnavailableException) {
			// Falls through
		}
		Toast.makeText(
			applicationContext,
			"This device does not have lens with facing: $newLensFacing",
			Toast.LENGTH_SHORT,
		).show()
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			setResult(RESULT_CANCELED)
			finish()
			return true
		}
		return false
	}

	public override fun onResume() {
		super.onResume()
		bindAllCameraUseCases()
	}

	override fun onPause() {
		super.onPause()

		imageProcessor?.run { this.stop() }
	}

	public override fun onDestroy() {
		super.onDestroy()
		imageProcessor?.run { this.stop() }
	}

	private fun bindAllCameraUseCases() {
		if (cameraProvider != null) {
			// As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
			cameraProvider!!.unbindAll()
			bindPreviewUseCase()
			bindAnalysisUseCase()
		}
	}

	private fun bindPreviewUseCase() {
		if (cameraProvider == null) {
			return
		}
		if (previewUseCase != null) {
			cameraProvider!!.unbind(previewUseCase)
		}

		graphicOverlay = findViewById(R.getId(this, "graphic_overlay"))
		if (graphicOverlay == null) {
			LOG.d(TAG, "graphicOverlay is null")
		}

		val builder = Preview.Builder()
		previewUseCase = builder.build()
		previewUseCase?.setSurfaceProvider(previewView?.getSurfaceProvider())

		val oCamera = cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)

		if (oCamera.cameraInfo.hasFlashUnit()) {
			isTorchEnabled = intent.getBooleanExtra(Scan.TORCH_ON, false)
			oCamera.cameraControl.enableTorch(isTorchEnabled)
			buttonTorch?.visibility = if (showTorchButton) VISIBLE else GONE
		} else {
			buttonTorch?.visibility = GONE
		}

		camera = oCamera
	}

	private fun bindAnalysisUseCase() {
		if (cameraProvider == null) {
			return
		}

		cameraProvider?.unbind(analysisUseCase)
		imageProcessor?.stop()

		val formatString = intent.getStringExtra(Scan.FORMATS) ?: ""
		val formats = formatString.split(",").map {
			getMLKitFormatDescription(it)
		}.filter { it != Barcode.FORMAT_UNKNOWN }

		imageProcessor =
			try {
				LOG.i(TAG, "Using Barcode Detector Processor")
				BarcodeScannerProcessor(this, formats, object : BarcodeScannerProcessor.Callback {
					override fun onResult(barcode: Barcode) {
						imageProcessor?.stop()
						processScanResult(barcode)
					}
				})
			} catch (e: Exception) {
				LOG.e(TAG, "Can not create image processor: $BARCODE_SCANNING", e)
				Toast.makeText(
					applicationContext,
					"Can not create image processor: " + e.localizedMessage,
					Toast.LENGTH_LONG,
				)
					.show()
				return
			}

		val builder = ImageAnalysis.Builder()
		analysisUseCase = builder.build()

		needUpdateGraphicOverlayImageSourceInfo = true

		analysisUseCase?.setAnalyzer(
			// imageProcessor.processImageProxy will use another thread to run the detection underneath,
			// thus we can just runs the analyzer itself on main thread.
			ContextCompat.getMainExecutor(this),
		) { imageProxy: ImageProxy ->
			try {
				imageProcessor?.processImageProxy(imageProxy, graphicOverlay)
			} catch (e: MlKitException) {
				LOG.e(TAG, "Failed to process image. Error: " + e.localizedMessage)
				Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
			}
		}
		cameraProvider!!.bindToLifecycle(this, cameraSelector!!, analysisUseCase)
	}

	private fun processScanResult(barcode: Barcode) {
		beepManager?.playBeepSound()

		val durationStr = intent.getStringExtra(Scan.RESULT_DISPLAY_DURATION_MS)
		if (durationStr == null) {
			finishWithResult(barcode)
			return
		}

		val resultDurationMS = try {
			durationStr.toLong()
		} catch (e: NumberFormatException) {
			LOG.e(TAG, "Could not parse $durationStr to Long", e)
			finishWithResult(barcode)
			return
		}

		if (resultDurationMS <= 0) {
			finishWithResult(barcode)
			return
		}

		val textPrompt: AppCompatTextView = findViewById(R.getId(this, "text_prompt"))
		textPrompt.text = "Found plain text: ${barcode.displayValue}"
		textPrompt.visibility = VISIBLE

		Handler(Looper.getMainLooper()).postDelayed({ finishWithResult(barcode) }, resultDurationMS)
	}

	private fun finishWithResult(barcode: Barcode) {
		val intent = Intent()
		intent.putExtra(Scan.RESULT, barcode.displayValue)
		intent.putExtra(Scan.RESULT_FORMAT, getCordovaFormatDescription(barcode.format))
		setResult(RESULT_OK, intent)
		finish()
	}

	private val conversionFormatMap = mapOf(
		"QR_CODE" to Barcode.FORMAT_QR_CODE,
		"DATA_MATRIX" to Barcode.FORMAT_DATA_MATRIX,
		"UPC_A" to Barcode.FORMAT_UPC_A,
		"UPC_E" to Barcode.FORMAT_UPC_E,
		"EAN_8" to Barcode.FORMAT_EAN_8,
		"EAN_13" to Barcode.FORMAT_EAN_13,
		"CODE_39" to Barcode.FORMAT_CODE_39,
		"CODE_93" to Barcode.FORMAT_CODE_93,
		"CODE_128" to Barcode.FORMAT_CODE_128,
		"CODABAR" to Barcode.FORMAT_CODABAR,
		"ITF" to Barcode.FORMAT_ITF,
		"PDF_417" to Barcode.FORMAT_PDF417,
		"AZTEC" to Barcode.FORMAT_AZTEC
	)

	private fun getMLKitFormatDescription(format: String): Int {
		return conversionFormatMap[format] ?: Barcode.FORMAT_UNKNOWN
	}

	private fun getCordovaFormatDescription(mlkitFormat: Int): String? {
		return conversionFormatMap
			.filter { it.value == mlkitFormat }
			.keys.firstOrNull()
	}

	companion object {
		private const val TAG = "CameraXLivePreview"
		private const val BARCODE_SCANNING = "Barcode Scanning"
		private const val STATE_SELECTED_MODEL = "selected_model"
	}
}
