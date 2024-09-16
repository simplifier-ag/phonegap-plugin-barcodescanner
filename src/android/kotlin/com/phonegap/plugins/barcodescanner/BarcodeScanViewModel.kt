package com.phonegap.plugins.barcodescanner

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.apache.cordova.LOG
import java.util.concurrent.ExecutionException

class BarcodeScanViewModel
/**
 * Create an instance which interacts with the camera service via the given application context.
 */
	(application: Application) : AndroidViewModel(application) {
	private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null

	val processCameraProvider: LiveData<ProcessCameraProvider>
		get() {
			if (cameraProviderLiveData == null) {
				cameraProviderLiveData = MutableLiveData()

				val cameraProviderFuture =
					ProcessCameraProvider.getInstance(getApplication())
				cameraProviderFuture.addListener(
					{
						try {
							cameraProviderLiveData!!.setValue(cameraProviderFuture.get())
						} catch (e: ExecutionException) {
							// Handle any errors (including cancellation) here.
							LOG.e(TAG, "Unhandled exception", e)
						} catch (e: InterruptedException) {
							LOG.e(TAG, "Unhandled exception", e)
						}
					},
					ContextCompat.getMainExecutor(getApplication())
				)
			}

			return cameraProviderLiveData!!
		}

	companion object {
		private const val TAG = "BarcodeScanViewModel"
	}
}
