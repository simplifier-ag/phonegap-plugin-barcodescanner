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

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.phonegap.plugins.barcodescanner.FrameMetadata
import org.apache.cordova.LOG
import java.nio.ByteBuffer

class BarcodeScannerProcessor(context: Context, formatList: List<Int>, val callback: Callback) :
	VisionProcessorBase<List<Barcode>>(context) {

	interface Callback {
		fun onResult(barcode: Barcode)
	}

	private var barcodeScanner: BarcodeScanner

	init {
		val builder = BarcodeScannerOptions.Builder()

		if (formatList.isEmpty()) {
			builder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
		} else {
			builder.setBarcodeFormats(
				formatList[0],
				*formatList.subList(1, formatList.size).toIntArray()
			)
		}

		barcodeScanner = BarcodeScanning.getClient(builder.build())
	}

	override fun stop() {
		super.stop()
		barcodeScanner.close()
	}

	override fun detectInImage(image: InputImage): Task<List<Barcode>> {
		return barcodeScanner.process(image)
	}

	private var invert = false

	override fun processByteBuffer(
		data: ByteBuffer?,
		frameMetadata: FrameMetadata?
	) {
		if (data != null && invert) {
			val imageBytes = data.array()
			val ySize = frameMetadata?.width?.times(frameMetadata.height) ?: 0

			// Invert Y values
			for (i in 0 until ySize) {
				imageBytes[i] = (255 - imageBytes[i]).toByte()
			}

			// Invert U and V values
			for (i in ySize until imageBytes.size) {
				imageBytes[i] = (128 - (imageBytes[i] - 128)).toByte()
			}
		}
		invert = !invert
		super.processByteBuffer(data, frameMetadata)
	}

	override fun onSuccess(results: List<Barcode>) {
		if (results.isEmpty()) {
			LOG.v(MANUAL_TESTING_LOG, "No barcode has been detected")
			return
		}

		val barcode = results.first()
		LOG.v(
			MANUAL_TESTING_LOG,
			String.format("Barcode detected: %s", barcode.displayValue ?: "NO Value")
		)
		callback.onResult(barcode)
	}

	override fun onFailure(e: Exception) {
		LOG.e(TAG, "Barcode detection failed $e")
	}

	companion object {
		private const val TAG = "BarcodeProcessor"
	}
}
