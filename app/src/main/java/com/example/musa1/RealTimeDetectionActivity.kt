package com.example.musa1

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RealTimeDetectionActivity : AppCompatActivity() {
    private lateinit var tflite: Interpreter
    private lateinit var surfaceView: SurfaceView
    private lateinit var textViewResult: TextView
    private var camera: Camera? = null
    private val INPUT_SIZE = 150
    private val CONFIDENCE_THRESHOLD = 0.7f // Ajusta el umbral según el rendimiento del modelo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_real_time_detection)

        surfaceView = findViewById(R.id.surfaceView)
        textViewResult = findViewById(R.id.textViewResult)

        // Verificar y solicitar permisos de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            setupCamera()
        }

        // Cargar el modelo TFLite
        try {
            tflite = Interpreter(loadModelFile())
            println("Modelo cargado correctamente.")
        } catch (e: IOException) {
            e.printStackTrace()
            textViewResult.text = "Error al cargar el modelo."
        }
    }

    private fun setupCamera() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    camera = Camera.open()
                    camera?.setDisplayOrientation(90) // Ajusta la orientación de la cámara
                    camera?.setPreviewDisplay(holder)
                    camera?.setPreviewCallback { data, _ ->
                        processFrame(data) // Procesar los frames de la cámara
                    }
                    camera?.startPreview()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                camera?.stopPreview()
                try {
                    camera?.setPreviewDisplay(holder)
                    camera?.startPreview()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                camera?.stopPreview()
                camera?.release()
                camera = null
            }
        })
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd("micorrizas_model_final.tflite")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun processFrame(data: ByteArray) {
        try {
            val parameters = camera?.parameters
            val width = parameters?.previewSize?.width ?: 0
            val height = parameters?.previewSize?.height ?: 0

            val yuvImage = YuvImage(data, parameters?.previewFormat ?: 0, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
            val yuvData = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(yuvData, 0, yuvData.size)

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            inputBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (pixelValue in intValues) {
                inputBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }

            val output = Array(1) { FloatArray(7) }
            tflite.run(inputBuffer, output)

            val classLabels = arrayOf(
                "Ectendomicorriza", "Ectomicorriza", "Endomicorrizas",
                "Infect General", "Infect General 2", "Sin Micorrizas", "Sin Micorrizas 2"
            )
            val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            val confidence = output[0].maxOrNull() ?: 0f

            val result = if (maxIndex != -1 && confidence >= CONFIDENCE_THRESHOLD) {
                classLabels[maxIndex]
            } else {
                "No es micorriza"
            }

            textViewResult.text = result

            // Solo dibuja el rectángulo si la detección es válida
            if (result != "No es micorriza") {
                drawRectangleOnFrame(resizedBitmap)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drawRectangleOnFrame(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f

        // Ajusta las coordenadas del rectángulo según lo que se desea encuadrar
        val left = 50f
        val top = 50f
        val right = bitmap.width - 50f
        val bottom = bitmap.height - 50f

        canvas.drawRect(left, top, right, bottom, paint)

        surfaceView.holder.lockCanvas()?.let { drawCanvas ->
            drawCanvas.drawBitmap(bitmap, 0f, 0f, null)
            surfaceView.holder.unlockCanvasAndPost(drawCanvas)
        }
    }
}
