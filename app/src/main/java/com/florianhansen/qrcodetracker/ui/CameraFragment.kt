package com.florianhansen.qrcodetracker.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.View
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import com.florianhansen.qrcodetracker.R
import com.florianhansen.qrcodetracker.database.BarcodeService
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class CameraFragment : Fragment(R.layout.fragment_camera), CameraXConfig.Provider,
    ImageAnalysis.Analyzer {
    private var isProcessing: Boolean = false
    private lateinit var scanner: BarcodeScanner
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var cameraProvider : ProcessCameraProvider

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreviewView = view.findViewById(R.id.cameraPreviewView)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        scanner = BarcodeScanning.getClient(options)

        startCameraPreview()
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context!!)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), this)

        preview.setSurfaceProvider(cameraPreviewView.surfaceProvider)
        cameraProvider.bindToLifecycle(
            requireContext() as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    private fun processScanResults(results: MutableList<Barcode>) {
        if (results.size > 0 && !isProcessing) {
            isProcessing = true
            val barcodeId = Integer.parseInt(results[0].rawValue!!)

            val service = Retrofit.Builder()
                .baseUrl("http://192.168.2.217:8080/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BarcodeService::class.java)

            val barcodeRequest = service.registerBarcode(barcodeId)
            barcodeRequest.enqueue(object : Callback<com.florianhansen.qrcodetracker.model.Barcode> {
                override fun onResponse(
                    call: Call<com.florianhansen.qrcodetracker.model.Barcode>,
                    response: Response<com.florianhansen.qrcodetracker.model.Barcode>
                ) {
                    var barcode = response.body()

                    if (barcode == null) {
                        barcode = com.florianhansen.qrcodetracker.model.Barcode()
                        barcode.id = barcodeId
                    }

                    val action = CameraFragmentDirections.actionCameraFragmentToSuccessFragment(barcode)
                    findNavController().navigate(action)
                    isProcessing = false
                }

                override fun onFailure(
                    call: Call<com.florianhansen.qrcodetracker.model.Barcode>,
                    t: Throwable
                ) {
                }
            })


        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null && !isProcessing) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { results ->
                    processScanResults(results)
                }
                .addOnFailureListener {
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider.unbindAll()
    }

}
