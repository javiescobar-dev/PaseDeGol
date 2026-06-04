package com.escobar.pasedegol.ui.auth

import android.app.AlertDialog
import android.content.Context.CONNECTIVITY_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.escobar.pasedegol.MainActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    // Instancia de binding para acceder a los elementos de la vista
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Instancia de FirebaseAuth para autenticar al usuario
    private lateinit var auth: FirebaseAuth
    // instancia de FirebaseFirestore para interactuar con la base de datos
    private lateinit var db: FirebaseFirestore

    // instancia de CredentialManager para gestionar las credenciales del usuario al loguear con google
    // se inicializa la primera vez que se usa
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(requireContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // configuramos una transicion suave al navegar entre fragments
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // obtenemos las instancias de Authentication y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // si el usuario ya esta autenticado, redirigir directamente
        auth.currentUser?.let {
            navigateAfterLogin(it.uid)
            return
        }

        // configurar el boton de autenticacion o registro de email/pass
        binding.btnLoginRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            authenticateOrRegister(email, password)
        }

        // configurar el boton de autenticacion con Google
        binding.btnGoogle.setOnClickListener {
            launchGoogleSignIn()
        }
    }

    // metodo para comprobar si hay conexion a internet disponible
    private fun isNetworkAvailable(): Boolean {
        // obtener el servicio ConnectivityManager para comprobar estado de conexion
        // en caso de no obtener el servicio, no hay conexion, devolvemos false
        val connectivityManager = requireContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // metodo para autenticar o registrar al usuario (con email/pass)
    private fun authenticateOrRegister(email: String, password: String) {
        // primero validamos los datos introducidos por el usuario
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Introduce un email válido"
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        // verificar conexion a internet antes de intentar autenticar
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        // deshabilitamos el boton mientras se procesa la autenticacion
        binding.btnLoginRegister.isEnabled = false
        binding.btnLoginRegister.text = "Conectando..."

        // intentamos autenticar con email/pass inicialmente
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // login exitoso, redirigir a HomeFragment
                navigateAfterLogin(it.user!!.uid)
            }
            .addOnFailureListener { e ->
                // si falla porque el usuario no existe o la contraseña esta mal puesta
                if (e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException || e is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    // intentamos registrar al usuario
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            // nuevo usuario: ir a WelcomeActivity
                            val intent = Intent(requireContext(), WelcomeActivity::class.java)
                            startActivity(intent)
                            requireActivity().finish()
                        }
                        .addOnFailureListener { eRegister ->
                            // no se ha podido autenticar ni registrar, mostramos error
                            // en caso de que sea por usuario existente, significa que la contraseña es incorrecta
                            if (eRegister is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                Toast.makeText(requireContext(), "Contraseña incorrecta", Toast.LENGTH_LONG).show()
                                binding.tilPassword.error = "Contraseña incorrecta"
                            } else {
                                // otro tipo de error (sin internet...)
                                Toast.makeText(requireContext(), "Error: ${eRegister.message}", Toast.LENGTH_LONG).show()
                            }
                            // rehabilitar el boton de login
                            resetLoginButton()
                        }
                } else {
                    // otro tipo de error (demasiados intentos fallidos, sin internet...)
                    Toast.makeText(requireContext(), "Error al iniciar sesión: ${e.message}", Toast.LENGTH_LONG).show()
                    // rehabilitar el boton de login
                    resetLoginButton()
                }
            }
    }

    // metodo para rehabilitar el boton de login a su estado original
    private fun resetLoginButton() {
        binding.btnLoginRegister.isEnabled = true
        binding.btnLoginRegister.text = "ENTRAR / REGISTRARSE"
    }

    // metodo para implementar la logica de login de Google
    // se verifica si se ha seleccionado la cuenta de Google en el dispositivo del usuario
    private fun launchGoogleSignIn() {
        // verificar conexion a internet antes de intentar autenticar con Google
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        // uso de corrutinas para manejar la operacion asincrona
        lifecycleScope.launch {
            try {
                // Configuracion de googleIdOption y request
                val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false) // Permite elegir cualquier cuenta de google
                    .setServerClientId(getString(R.string.default_web_client_id)) // ID de cliente web
                    .build()

                // construimos la solicitud de credenciales
                val request: GetCredentialRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // obtenemos la credencial seleccionada
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )

                // verificamos si es un CustomCredential y si es del tipo Google ID
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // extraemos el token de los datos del CustomCredential
                        // se encarga de parsear de forma segura el token que recibes para enviárselo a Firebase.
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val googleIdToken = googleIdTokenCredential.idToken

                        // llamamos a Firebase con el token extraído para autenticar al usuario
                        firebaseAuthWithGoogle(googleIdToken)
                    } catch (e: Exception) {
                        Log.e("Login", "Error extrayendo el token de Google", e)
                    }
                } else {
                    Log.e("Login", "Tipo de credencial no reconocido: ${credential.javaClass.name}")
                }
            } catch (e: NoCredentialException) {
                Log.d("Login", "No se ha seleccionado ninguna credencial")
            } catch (e: GetCredentialCancellationException) {
                Log.d("Login", "Cancelado por usuario")
            } catch (e: Exception) {
                Log.e("Login", "Error general", e)
                showAlert()
            }
        }
    }

    // metodo para implementar la logica de login de Google con Firebase
    private fun firebaseAuthWithGoogle(idToken: String) {
        // obtenemos las credenciales de Google a partir del token
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            // Login exitoso en Firebase, redirigir a WelcomeActivity
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val isNewUser = result.additionalUserInfo?.isNewUser ?: false
                if (isNewUser) {
                    val intent = Intent(requireContext(), WelcomeActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                } else {
                    navigateAfterLogin(uid)
                }
            }
            // error en el login de Firebase, mostramos mensaje de error
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error Google: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // metodo para redirigir una vez se ha comprobado que esta logueado
    private fun navigateAfterLogin(uid: String) {
        // timeout para evitar carga infinita si no hay conexion y los datos no estan en cache
        val timeoutHandler = Handler(Looper.getMainLooper())
        var isResolved = false
        val timeoutRunnable = Runnable {
            if (!isResolved && isAdded) {
                isResolved = true
                // tras el timeout, redirigir sin comprobar rol de admin
                Toast.makeText(requireContext(), "Sin conexión. Accediendo con datos en caché.", Toast.LENGTH_SHORT).show()
                updateParentMenu(false)
                findNavController().navigate(R.id.homeFragment)
            }
        }
        // establecer timeout de 10 segundos
        timeoutHandler.postDelayed(timeoutRunnable, 10000)

        // consultar rol en Firestore para redirigir correctamente
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                // en caso de ya haber pasado el timeout, no hacemos nada
                if (isResolved) return@addOnSuccessListener
                // en caso de no haber pasado el timeout, lo marcamos como resuelto
                isResolved = true
                timeoutHandler.removeCallbacks(timeoutRunnable)

                if (doc.exists()) {
                    // documento existe, redirigir a HomeFragment
                    val isAdmin = doc.getBoolean("isAdmin") ?: false
                    updateParentMenu(isAdmin) // antes de redirigir, actualizamos el menu de la MainActivity
                    findNavController().navigate(R.id.homeFragment)
                } else {
                    // documento no existe todavia, ir a Welcome
                    val intent = Intent(requireContext(), WelcomeActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
            .addOnFailureListener {
                // en caso de ya haber pasado el timeout, no hacemos nada
                if (isResolved) return@addOnFailureListener
                // en caso de no haber pasado el timeout, lo marcamos como resuelto
                isResolved = true
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // error al consultar Firestore, redirigir a HomeFragment
                updateParentMenu(false) // antes de redirigir, actualizamos el menu de la MainActivity
                findNavController().navigate(R.id.homeFragment)
            }
    }

    // Metodo para mostrar el mensaje de error al autenticarse
    private fun showAlert() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Error")
        builder.setMessage("Se ha producido un error autenticando al usuario")
        builder.setPositiveButton("Aceptar", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    // Metodo para actualizar el menu de navegacion en la MainActivity
    private fun updateParentMenu(isAdmin: Boolean) {
        // comprobamos si el usuario es admin para actualizar el menu
        (requireActivity() as? MainActivity)?.updateMenuForAuthState(true, isAdmin)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
