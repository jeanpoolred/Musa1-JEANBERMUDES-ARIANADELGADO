package com.example.musa1

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LoadImageActivity : AppCompatActivity(){
    private lateinit var tflite: Interpreter
    private lateinit var textViewResult: TextView
    private lateinit var imageView: ImageView
    private val INPUT_SIZE = 150 // Tamaño de la imagen de entrada

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_image)

        // Inicializar los elementos de la interfaz
        val buttonSelectImage: Button = findViewById(R.id.buttonSelectImage)
        textViewResult = findViewById(R.id.textViewResult)
        imageView = findViewById(R.id.imageView)

        // Cargar el modelo TFLite
        try {
            tflite = Interpreter(loadModelFile())
            println("Modelo cargado correctamente.")
        } catch (e: IOException) {
            e.printStackTrace()
            textViewResult.text = "Error al cargar el modelo."
        }

        // Configurar botón para seleccionar imagen
        buttonSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(Intent.createChooser(intent, "Seleccionar Imagen"), 1)
        }
    }

    // Método para cargar el archivo del modelo TFLite desde la carpeta assets
    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd("micorrizas_model_final.tflite")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Método para manejar la imagen seleccionada y clasificarla
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            try {
                val uri = data.data
                val inputStream = contentResolver.openInputStream(uri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imageView.setImageBitmap(bitmap)
                val result = classifyImage(bitmap)
                textViewResult.text = result
            } catch (e: Exception) {
                e.printStackTrace()
                textViewResult.text = "Error al procesar la imagen."
            }
        }
    }

    // Método para preprocesar la imagen y hacer la clasificación con el modelo
    private fun classifyImage(bitmap: Bitmap): String {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Preprocesar la imagen para el modelo
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f) // R
            inputBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)  // G
            inputBuffer.putFloat((pixelValue and 0xFF) / 255.0f)          // B
        }

        // Realizar la predicción
        val output = Array(1) { FloatArray(7) } // Ajustar el tamaño según la cantidad de clases
        try {
            tflite.run(inputBuffer, output)
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error al ejecutar la clasificación."
        }

        // Determinar la clase con mayor probabilidad
        val classLabels = arrayOf("Ectendomicorriza", "Ectomicorriza", "Endomicorrizas",
            "Infect General", "Infect General 2", "Sin Micorrizas", "Sin Micorrizas 2")
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return if (maxIndex != -1) classLabels[maxIndex] else "Clasificación no encontrada"
    }
}