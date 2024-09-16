package com.phonegap.plugins.barcodescanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.LOG
import org.apache.cordova.PermissionHelper
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BarcodeScanner : CordovaPlugin() {
	private val REQUEST_CODE: Int = 0x0ba7c

	private val permissions = arrayOf(Manifest.permission.CAMERA)

	private val SCAN = "scan"
	private val CANCELLED = "cancelled"
	private val FORMAT = "format"
	private val TEXT = "text"
	private val PREFER_FRONTCAMERA = "preferFrontCamera"
	private val ORIENTATION = "orientation"
	private val SHOW_FLIP_CAMERA_BUTTON = "showFlipCameraButton"
	private val RESULTDISPLAY_DURATION = "resultDisplayDuration"
	private val SHOW_TORCH_BUTTON = "showTorchButton"
	private val TORCH_ON = "torchOn"
	private val ASSUME_GS1 = "assumeGS1"
	private val DISABLE_BEEP = "disableSuccessBeep"
	private val FORMATS = "formats"
	private val PROMPT = "prompt"

	private val LOG_TAG = "BarcodeScanner"

	private var requestArgs: JSONArray? = null
	private var callbackContext: CallbackContext? = null
	private var assumeGS1 = false

	override fun execute(
		action: String?,
		args: JSONArray?,
		callbackContext: CallbackContext?
	): Boolean {

		this.callbackContext = callbackContext
		this.requestArgs = args

		if (action == SCAN) {
			if (args == null) {
				return false
			}

			//android permission auto add
			if (!hasPermisssion()) {
				requestPermissions(0)
			} else {
				cordova.getThreadPool().execute {
					scan(args)
				}
			}
		} else {
			return false
		}
		return true
	}

	/**
	 * Starts an intent to scan and decode a barcode.
	 */
	fun scan(args: JSONArray) {
		val intentScan = Intent(
			cordova.activity.baseContext,
			BarcodeScanActivity::class.java
		)
		intentScan.addCategory(Intent.CATEGORY_DEFAULT)

		if (args.length() <= 0) {
			return
		}

		var obj: JSONObject
		var names: JSONArray?
		var key: String
		var value: Any

		for (i in 0..<args.length()) {
			try {
				obj = args.getJSONObject(i)
			} catch (e: JSONException) {
				LOG.i(LOG_TAG, e.localizedMessage)
				continue
			}

			names = obj.names()

			for (j in 0..<names.length()) {

				try {
					key = names.getString(j)
					value = obj[key]

					if (value is Int) {
						intentScan.putExtra(key, value)
					} else if (value is String) {
						intentScan.putExtra(key, value)
					}
				} catch (e: JSONException) {
					LOG.i("CordovaLog", e.localizedMessage)
				}

				intentScan.putExtra(
					//TODO Umbenennen
					Scan.PREFER_FRONT_CAMERA,
					obj.optBoolean(PREFER_FRONTCAMERA, false)
				)

				intentScan.putExtra(
					Scan.SHOW_FLIP_CAMERA_BUTTON, obj.optBoolean(
						SHOW_FLIP_CAMERA_BUTTON, false
					)
				)

				intentScan.putExtra(
					Scan.SHOW_TORCH_BUTTON, obj.optBoolean(
						SHOW_TORCH_BUTTON, false
					)
				)

				intentScan.putExtra(
					Scan.TORCH_ON,
					obj.optBoolean(TORCH_ON, false)
				)

				assumeGS1 = obj.optBoolean(ASSUME_GS1, false)
				intentScan.putExtra(Scan.ASSUME_GS1, assumeGS1)

				val beep = obj.optBoolean(DISABLE_BEEP, false)
				intentScan.putExtra(Scan.BEEP_ON_SCAN, !beep)

				if (obj.has(RESULTDISPLAY_DURATION)) {
					intentScan.putExtra(
						Scan.RESULT_DISPLAY_DURATION_MS,
						"" + obj.optLong(RESULTDISPLAY_DURATION)
					)
				}

				if (obj.has(FORMATS)) {
					intentScan.putExtra(Scan.FORMATS, obj.optString(FORMATS))
				}

				if (obj.has(PROMPT)) {
					intentScan.putExtra(
						Scan.PROMPT_MESSAGE,
						obj.optString(PROMPT)
					)
				}

				if (obj.has(ORIENTATION)) {
					intentScan.putExtra(
						Scan.ORIENTATION_LOCK,
						obj.optString(ORIENTATION)
					)
				}
			}
		}

		// avoid calling other phonegap apps
		intentScan.setPackage(cordova.getActivity().getApplicationContext().getPackageName())
		cordova.startActivityForResult(this, intentScan, REQUEST_CODE)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		LOG.e(LOG_TAG, "onActivityResult")

		if (requestCode == REQUEST_CODE && this.callbackContext != null) {
			when (resultCode) {

				Activity.RESULT_OK -> {
					val obj = JSONObject()
					try {
						var scanResult = intent!!.getStringExtra(Scan.RESULT)

						if (assumeGS1) {
							scanResult = scanResult!!.replace("]C1", "")
						}

						obj.put(TEXT, scanResult)
						obj.put(FORMAT, intent!!.getStringExtra(Scan.RESULT_FORMAT))
						obj.put(CANCELLED, false)
					} catch (e: JSONException) {
						LOG.d(LOG_TAG, "This should never happen")
					}
					//this.success(new PluginResult(PluginResult.Status.OK, obj), this.callback);
					callbackContext!!.success(obj)
				}

				Activity.RESULT_CANCELED -> {
					val obj = JSONObject()
					try {
						obj.put(TEXT, "")
						obj.put(FORMAT, "")
						obj.put(CANCELLED, true)
					} catch (e: JSONException) {
						LOG.d(LOG_TAG, "This should never happen")
					}
					//this.success(new PluginResult(PluginResult.Status.OK, obj), this.callback);
					callbackContext!!.success(obj)
				}

				else -> {
					//this.error(new PluginResult(PluginResult.Status.ERROR), this.callback);
					callbackContext!!.error("Unexpected error")
				}
			}
		}
	}

	override fun requestPermissions(requestCode: Int) {
		PermissionHelper.requestPermissions(this, requestCode, permissions)
	}

	override fun hasPermisssion(): Boolean {
		for (p in permissions) {
			if (!PermissionHelper.hasPermission(this, p)) {
				return false
			}
		}
		return true
	}

	/*override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>?,
		grantResults: IntArray?
	) {
		val result: PluginResult
		for (r in grantResults!!) {
			if (r == PackageManager.PERMISSION_DENIED) {
				LOG.d(LOG_TAG, "Permission Denied!")
				result = PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION)
				callbackContext?.sendPluginResult(result)
				return
			}
		}

		when (requestCode) {
			0 -> scan(requestArgs!!)
		}
	}*/

	override fun onRequestPermissionResult(
		requestCode: Int,
		permissions: Array<out String>?,
		grantResults: IntArray?
	) {
		val result: PluginResult
		for (r in grantResults!!) {
			if (r == PackageManager.PERMISSION_DENIED) {
				LOG.d(LOG_TAG, "Permission Denied!")
				result = PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION)
				callbackContext?.sendPluginResult(result)
				return
			}
		}

		when (requestCode) {
			0 -> scan(requestArgs!!)
		}
	}

}

object Scan {
	const val ACTION: String = "com.google.zxing.client.android.SCAN"
	const val MODE: String = "SCAN_MODE"
	const val PRODUCT_MODE: String = "PRODUCT_MODE"
	const val ONE_D_MODE: String = "ONE_D_MODE"
	const val QR_CODE_MODE: String = "QR_CODE_MODE"
	const val DATA_MATRIX_MODE: String = "DATA_MATRIX_MODE"
	const val AZTEC_MODE: String = "AZTEC_MODE"
	const val PDF417_MODE: String = "PDF417_MODE"
	const val FORMATS: String = "SCAN_FORMATS"
	const val PREFER_FRONT_CAMERA: String = "PREFER_FRONT_CAMERA"
	const val CHARACTER_SET: String = "CHARACTER_SET"
	const val WIDTH: String = "SCAN_WIDTH"
	const val HEIGHT: String = "SCAN_HEIGHT"
	const val RESULT_DISPLAY_DURATION_MS: String = "RESULT_DISPLAY_DURATION_MS"
	const val PROMPT_MESSAGE: String = "PROMPT_MESSAGE"
	const val RESULT: String = "SCAN_RESULT"
	const val RESULT_FORMAT: String = "SCAN_RESULT_FORMAT"
	const val RESULT_UPC_EAN_EXTENSION: String = "SCAN_RESULT_UPC_EAN_EXTENSION"
	const val RESULT_BYTES: String = "SCAN_RESULT_BYTES"
	const val RESULT_ORIENTATION: String = "SCAN_RESULT_ORIENTATION"
	const val RESULT_ERROR_CORRECTION_LEVEL: String = "SCAN_RESULT_ERROR_CORRECTION_LEVEL"
	const val RESULT_BYTE_SEGMENTS_PREFIX: String = "SCAN_RESULT_BYTE_SEGMENTS_"
	const val SHOW_FLIP_CAMERA_BUTTON: String = "SHOW_FLIP_CAMERA_BUTTON"
	const val SHOW_TORCH_BUTTON: String = "SHOW_TORCH_BUTTON"
	const val TORCH_ON: String = "TORCH_ON"
	const val BEEP_ON_SCAN: String = "BEEP_ON_SCAN"
	const val BULK_SCAN: String = "BULK_SCAN"
	const val ORIENTATION_LOCK: String = "ORIENTATION_LOCK"
	const val ASSUME_GS1: String = "ASSUME_GS1"
}