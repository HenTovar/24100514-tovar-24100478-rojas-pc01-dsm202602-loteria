package com.esan.pc01.loteria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoteriaBase() }
    }
}

@Preview(showBackground = true)
@Composable
fun LoteriaBase() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Lotería PC01", style = MaterialTheme.typography.headlineLarge)
                Text("Proyecto base · Kotlin y Jetpack Compose")
                Text("Próximos pasos: iniciar sesión, registrar seis números y seguir el sorteo en tiempo real.")
                Text("Esta entrega aún no autentica ni escribe en Firebase.")
            }
        }
    }
}
