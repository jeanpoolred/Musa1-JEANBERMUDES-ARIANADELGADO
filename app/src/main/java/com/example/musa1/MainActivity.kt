package com.example.musa1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializar los botones de la interfaz
        val buttonLoadImage: Button = findViewById(R.id.buttonLoadImage)
        val buttonRealTimeDetection: Button = findViewById(R.id.buttonRealTimeDetection)

        // Configurar botón para cargar y clasificar imagen
        buttonLoadImage.setOnClickListener {
            val intent = Intent(this, LoadImageActivity::class.java)
            startActivity(intent)
        }

        // Configurar botón para la detección en tiempo real
        buttonRealTimeDetection.setOnClickListener {
            val intent = Intent(this, RealTimeDetectionActivity::class.java)
            startActivity(intent)
        }
    }
}
