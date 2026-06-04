package com.escobar.pasedegol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.escobar.pasedegol.databinding.ActivityMainBinding
import com.escobar.pasedegol.ui.auth.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stripe.android.PaymentConfiguration

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Instancia de FirebaseAuth para autenticar al usuario
    private lateinit var auth: FirebaseAuth
    // Instancia de FirebaseFirestore para interactuar con la base de datos
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // antes de nada, inicializamos Stripe
        // se establece la clave publica aqui directamente, sigue siendo seguro aunque la clave publica este expuesta.
        // la idea era cargarla desde local.properties sin exponerla, pero al tener que compartir el proyecto, se deja asi.
        PaymentConfiguration.init(
            applicationContext,
            "pk_test_51TG0WNFoqPIM5EUATYrAHlQNUDN0BAzz1iFz5zyGxs88xRwsjoZ23dnHRKTBGkggynYm8ilD3vFJZYd3vlQMoYij00yfT2vwCH"
        )

        // ocultar menu mientras se verifica el perfil
        updateMenuVisibility(false)

        // obtenemos las instancias de Authentication y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // comprobar el estado de autenticacion del usuario para redirigir a WelcomeActivity si hiciese falta
        checkUserProfileAndRedirect()
        // configurar la barra de navegacion en base al estado de autenticacion
        setupNavigation()

        // manejar navegacion si el flujo viene de CartActivity tras un pago exitoso
        handleNavigationIntent(intent)
    }

    // Metodo para configurar la barra de navegacion de los Fragments
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        // conectar toolbar con NavController para que muestre el titulo automaticamente
        setSupportActionBar(binding.toolbar)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.profileFragment,
                R.id.ticketFragment,
                R.id.adminFragment,
                R.id.aboutFragment,
                R.id.loginFragment
            )
        )
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
    }

    private fun updateMenuVisibility(show: Boolean) {
        if (show) {
            binding.splashOverlay.visibility = android.view.View.GONE
            binding.bottomNavigationView.visibility = android.view.View.VISIBLE
        }
        else {
            binding.splashOverlay.visibility = android.view.View.VISIBLE
            binding.bottomNavigationView.visibility = android.view.View.GONE
        }
    }

    // Metodo para actualizar el menu segun el estado de autenticacion
    // no es privado porque se requiere su uso en otros fragments
    fun updateMenuForAuthState(userAuthenticated: Boolean, isAdmin: Boolean = false) {
        // obtenemos el usuario actual de firebase (si lo hay)
        val menu = binding.bottomNavigationView.menu

        if (userAuthenticated) {
            // usuario autenticado: mostrar Perfil y Mis Entradas, ocultar Autenticarse
            menu.findItem(R.id.loginFragment)?.apply {
                isVisible = false
            }
            menu.findItem(R.id.profileFragment)?.apply {
                isVisible = true
            }
            menu.findItem(R.id.ticketFragment)?.apply {
                isVisible = true
            }
            // mostrar Admin solo si el usuario tiene rol de administrador
            menu.findItem(R.id.adminFragment)?.apply {
                isVisible = isAdmin
            }
        } else {
            // usuario anonimo: mostrar Autenticarse, ocultar Perfil, Mis Entradas y Admin
            menu.findItem(R.id.loginFragment)?.apply {
                isVisible = true
            }
            menu.findItem(R.id.profileFragment)?.apply {
                isVisible = false
            }
            menu.findItem(R.id.ticketFragment)?.apply {
                isVisible = false
            }
            menu.findItem(R.id.adminFragment)?.apply {
                isVisible = false
            }
        }
    }

    // Metodo para comprobar el estado de autenticacion del usuario y redirigir a WelcomeActivity si es necesario
    private fun checkUserProfileAndRedirect() {
        // obtenemos el usuario actual de firebase (si lo hay)
        val user = auth.currentUser
        // si el usuario no esta autenticado, ocultar overlay y continuar normalmente
        if (user == null) {
            updateMenuVisibility(true)
            updateMenuForAuthState(false)
            return
        }
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // autenticado pero sin perfil establecido (sin documento en Firestore), redirigir a WelcomeActivity
                    val intent = Intent(this, WelcomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // perfil existente, ocultar overlay y mostrar navegacion
                    val isAdmin = doc.getBoolean("isAdmin") ?: false // comprobar si el usuario tiene rol de administrador
                    updateMenuVisibility(true)
                    updateMenuForAuthState(true, isAdmin)
                }
            }
            .addOnFailureListener {
                // si falla la consulta, ocultar overlay y continuar
                updateMenuVisibility(false)
                updateMenuForAuthState(false)
            }
    }

    // metodo para manejar la navegacion cuando se recibe un intent con extras (para manejar el flujo de CartActivity tras un pago exitoso)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
    }

    // metodo auxiliar para manejar el extra navigate_to y cambiar el tab seleccionado
    private fun handleNavigationIntent(intent: Intent) {
        when (intent.getStringExtra("navigate_to")) {
            "tickets" -> {
                // seleccionar el tab de Mis Entradas en el BottomNavigationView
                binding.bottomNavigationView.selectedItemId = R.id.ticketFragment
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // verificar que el perfil existe antes de mostrar menu de autenticado
        val user = auth.currentUser
        if (user != null) {
            checkUserProfileAndRedirect()
        } else {
            updateMenuForAuthState(false)
        }
    }
}
