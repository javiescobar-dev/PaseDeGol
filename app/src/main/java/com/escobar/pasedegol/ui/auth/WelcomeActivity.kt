package com.escobar.pasedegol.ui.auth

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.escobar.pasedegol.MainActivity
import com.escobar.pasedegol.databinding.ActivityWelcomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class WelcomeActivity : AppCompatActivity() {

    // Instancia de binding para acceder a los elementos de la vista
    private lateinit var binding: ActivityWelcomeBinding
    // Instancia de FirebaseAuth para autenticar al usuario
    private lateinit var auth: FirebaseAuth
    // Instancia de FirebaseFirestore para interactuar con la base de datos
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // obtenemos las instancias de Authentication y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // configuramos el boton de continuar
        binding.btnContinue.setOnClickListener {
            saveUserName()
        }
    }

    // metodo para comprobar si hay conexion a internet disponible
    private fun isNetworkAvailable(): Boolean {
        // obtener el servicio ConnectivityManager para comprobar estado de conexion
        // en caso de no obtener el servicio, no hay conexion, devolvemos false
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Metodo que guarda el nombre de usuario introducido en Firestore
    private fun saveUserName() {
        val name = binding.etDisplayName.text.toString().trim()

        // realizamos validacion para evitar que el nombre este vacio
        if (name.isEmpty()) {
            binding.tilDisplayName.error = "El nombre no puede estar vacío"
            return
        }
        binding.tilDisplayName.error = null

        // verificar conexion a internet antes de intentar guardar los cambios
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
            return
        }

        // obtenemos el UID del usuario actual y su email
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""

        // creamos el documento del usuario en Firestore
        val user = hashMapOf(
            "id" to uid,
            "email" to email,
            "name" to name,
            "isAdmin" to false,
            "notificationsEnabled" to true
        )

        // guardamos el documento en Firestore
        db.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener {
                // suscribimos al tema de noticias de Firebase Cloud Messaging por defecto al crear la cuenta
                FirebaseMessaging.getInstance().subscribeToTopic("noticias")
                    .addOnFailureListener { e ->
                        // depurar error al suscribirse a notificaciones
                        Log.e("WelcomeActivity", "Error al suscribirse a notificaciones", e)
                    }
                // documento creado, mostramos mensaje de bienvenida
                playAnimationAndNavigate(name)
            }
            .addOnFailureListener { e ->
                // en caso de error, mostramos mensaje al usuario
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Metodo para mostrar la animacion de bienvenida y enviar a MainActivity
    private fun playAnimationAndNavigate(name: String) {
        // ocultar el formulario al mostrar la bienvenida
        binding.tilDisplayName.visibility = android.view.View.GONE
        binding.btnContinue.visibility = android.view.View.GONE

        // cambiar titulo a mensaje de bienvenida
        binding.tvTitle.text = "¡Bienvenido $name!"

        // reproducir animacion Lottie
        binding.lottieAnimation.apply {
            playAnimation()
            addAnimatorUpdateListener { animation ->
                // cuando la animacion llegue al 90% navegar a MainActivity
                if (animation.animatedFraction >= 0.9f) {
                    navigateToMain()
                }
            }
        }
    }

    // Metodo para navegar a MainActivity
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        // limpiamos el back stack para que el usuario no pueda volver al login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
