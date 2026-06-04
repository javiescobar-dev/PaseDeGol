package com.escobar.pasedegol.ui.profile

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.escobar.pasedegol.MainActivity
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val FCM_TOPIC = "noticias"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // instancias de Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // variable para almacenar el estado original de las notificaciones para detectar cambios
    private var originalNotificationsEnabled = true

    // Firebase Cloud Messaging: Lanzador para pedir permiso de notificaciones en ejecucion en Android 13+ (requerido)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // permiso concedido, el switch se queda activado
            binding.switchNotifications.isChecked = true
        } else {
            // permiso denegado, poner off el switch
            binding.switchNotifications.isChecked = false

            // comprobar si el usuario ha seleccionado "No volver a preguntar"
            if (!shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationPermissionDialog()
            } else {
                Toast.makeText(requireContext(), "Necesitas dar permiso para recibir notificaciones", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // configuramos una transicion suave al navegar entre fragments
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // obtenemos las instancias de Authentication y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // cargar los datos del perfil desde Firestore
        loadProfileData()

        // configurar los listeners de los botones
        setupListeners()
    }

    // metodo para cargar los datos del perfil del usuario desde Firestore
    private fun loadProfileData() {
        val user = auth.currentUser ?: return

        // mostramos el email del usuario (no editable, viene de FirebaseAuth)
        binding.tvEmail.text = user.email ?: ""

        // consultamos el documento del usuario en Firestore para obtener nombre y preferencias
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                // verificacion de seguridad, si se esta destruyendo la vista, no cargamos los datos (evita crash)
                 if (_binding == null) return@addOnSuccessListener

                if (doc.exists()) {
                    // cargamos el nombre del usuario en el campo editable
                    val name = doc.getString("name") ?: ""
                    binding.etName.setText(name)

                    // cargamos las preferencias de notificaciones
                    val notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true

                    // comprobamos el permiso de notificaciones en el sistema operativo del dispositivo
                    val hasOsPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        // versiones anteriores a Android 13 no necesitan el permiso
                        true
                    }

                    // Si Firebase dice que sí, pero el dispositivo dice que no, forzamos switch off visualmente
                    val finalState = notificationsEnabled && hasOsPermission

                    originalNotificationsEnabled = finalState
                    binding.switchNotifications.isChecked = finalState
                }

                // cargamos la foto de perfil (aunque el documento no exista)
                loadAvatar(user, binding.etName.text.toString())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar el perfil", e)
                Toast.makeText(requireContext(), "Error al cargar el perfil", Toast.LENGTH_SHORT).show()
            }
    }

    // metodo para cargar la foto de perfil
    private fun loadAvatar(user: FirebaseUser?, username: String?) {
        // obtenemos la foto de perfil del usuario de Firebase
        val foto = user?.photoUrl
        // cargar la imagen
        if (foto != null) {
            Glide.with(this).load(foto).circleCrop().into(binding.ivAvatar)
        } else {
            // si no hay foto, generar la inicial
            createAvatar(username)
        }
    }

    // metodo para generar la inicial del avatar
    private fun createAvatar(nombre: String?) {
        // validamos si el nombre es nulo o esta vacio para evitar el crash
        val inicial = if (!nombre.isNullOrBlank()) {
            nombre.trim().first().uppercase()
        } else {
            "U" // U de usuario por defecto
        }

        // usamos la API de UI Avatars (es segura y eficiente)
        val urlString = "https://ui-avatars.com/api/?name=$inicial&background=random&color=fff&size=1024"

        // verificamos que el fragmento no se esta destruyendo antes de cargar con Glide
        if (isAdded && _binding != null) {
            Glide.with(this)
                .load(urlString)
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_help) // muestra algo mientras carga
                .error(android.R.drawable.stat_notify_error) // muestra esto si falla la carga
                .into(binding.ivAvatar)
        }
    }

    // metodo para configurar los listeners de los botones
    private fun setupListeners() {
        // boton guardar cambios
        binding.btnSave.setOnClickListener { saveProfileChanges() }
        // boton cerrar sesion
        binding.btnLogout.setOnClickListener { confirmLogout() }
        // boton eliminar cuenta
        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }
        // switch notificaciones
        binding.switchNotifications.setOnCheckedChangeListener { buttonView, isChecked ->
            // isPressed asegura que solo se ejecute si es el usuario quien lo toca con el dedo
            if (buttonView.isPressed && isChecked) {
                checkNotificationPermission()
            }
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

    // metodo para guardar los cambios realizados en el perfil
    private fun saveProfileChanges() {
        val user = auth.currentUser ?: return

        // validamos que el nombre no este vacio
        val newName = binding.etName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.tilName.error = "El nombre no puede estar vacío"
            return
        }
        binding.tilName.error = null

        // verificar conexion a internet antes de intentar guardar los cambios
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        val notificationsEnabled = binding.switchNotifications.isChecked

        // deshabilitamos el boton mientras se guardan los cambios
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Guardando..."

        // preparamos los datos a actualizar en Firestore
        val updates = hashMapOf<String, Any>(
            "name" to newName,
            "notificationsEnabled" to notificationsEnabled
        )

        // actualizamos el documento del usuario en Firestore
        db.collection("users").document(user.uid).update(updates)
            .addOnSuccessListener {
                // verificacion de seguridad, si se esta destruyendo la vista, no cargamos los datos (evita crash)
                if (_binding == null) return@addOnSuccessListener

                // actualizamos el avatar
                loadAvatar(user, binding.etName.text.toString())

                // si la preferencia de notificaciones ha cambiado, suscribimos/desuscribimos del tema de Firebase Cloud Messaging
                if (notificationsEnabled != originalNotificationsEnabled) {
                    if (notificationsEnabled) {
                        // suscribir al tema de noticias
                        FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC)
                            // depurar resultado
                            .addOnSuccessListener { Log.d(TAG, "Suscrito al tema $FCM_TOPIC") }
                            .addOnFailureListener { e -> Log.e(TAG, "Error al suscribirse a $FCM_TOPIC", e) }
                    } else {
                        // desuscribir del tema de noticias
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(FCM_TOPIC)
                            // depurar resultado
                            .addOnSuccessListener { Log.d(TAG, "Desuscrito del tema $FCM_TOPIC") }
                            .addOnFailureListener { e -> Log.e(TAG, "Error al desuscribirse de $FCM_TOPIC", e) }
                    }
                    originalNotificationsEnabled = notificationsEnabled
                }

                Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar el perfil", e)
                Toast.makeText(requireContext(), "Error al guardar los cambios", Toast.LENGTH_SHORT).show()
                resetSaveButton()
            }
    }

    // metodo para restaurar el estado del boton guardar
    private fun resetSaveButton() {
        binding.btnSave.isEnabled = true
        binding.btnSave.text = "GUARDAR CAMBIOS"
    }

    // metodo para mostrar dialogo de confirmacion antes de cerrar sesion
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->
                performLogout()
            }
            .show()
    }

    // metodo para cerrar la sesion del usuario
    private fun performLogout() {
        // verificar conexion a internet antes de intentar guardar los cambios
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        // cerramos sesion en Firebase Authentication
        auth.signOut()

        // actualizamos el menu de navegacion para reflejar el estado de usuario anonimo
        (requireActivity() as? MainActivity)?.updateMenuForAuthState(false)

        // navegamos al HomeFragment (catalogo)
        findNavController().navigate(R.id.homeFragment)

        // informamos al usuario
        Toast.makeText(requireContext(), "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
    }

    // metodo para mostrar dialogo de confirmacion antes de eliminar la cuenta
    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar cuenta")
            .setMessage("Esta acción eliminará permanentemente tu cuenta y todos tus datos. Esta acción no se puede deshacer. ¿Deseas continuar?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                performDeleteAccount()
            }
            .show()
    }

    // metodo para eliminar la cuenta del usuario y sus datos de Firestore
    private fun performDeleteAccount() {
        // verificar conexion a internet antes de intentar guardar los cambios
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        val user = auth.currentUser ?: return

        // validacion de seguridad para evitar dejar datos fantasma
        // al borrar una cuenta en Firestore (datos sensibles), requiere que haya autenticado recientemente
        // comprobamos si el usuario ha hecho login recientemente y en caso negativo,
        // indicamos que debe cerrar sesion y volver a autenticarse para poder proceder al borrado con exito
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        val now = System.currentTimeMillis()
        val timeSinceLastLogin = now - lastSignIn

        // comprobar que el tiempo de login sea mayor a 5 minutos
        if (timeSinceLastLogin > 5 * 60 * 1000) {
            showReAuthRequiredDialog()
            return // no borramos datos hasta que no se haya autenticado recientemente
        }

        // deshabilitamos botones durante la operacion
        binding.btnDeleteAccount.isEnabled = false
        binding.btnLogout.isEnabled = false
        binding.btnSave.isEnabled = false

        // referencias a los datos del usuario en Firestore
        val userRef = db.collection("users").document(user.uid) // documento usuario
        val cartRef = userRef.collection("cart") // subcoleccion carrito del usuario
        val ticketsRef = userRef.collection("tickets") // subcoleccion de entradas del usuario

        // primero obtenemos todos los documentos de la subcoleccion cart
        cartRef.get().addOnSuccessListener { cartSnapshot ->
            // luego obtenemos todos los documentos de la subcoleccion tickets
            ticketsRef.get().addOnSuccessListener { ticketsSnapshot ->
                // preparamos el WriteBatch para ejecutar una operacion atomica (como una transaccion)
                val batch = db.batch()

                // añadimos al batch el borrado de cada item del carrito
                for (doc in cartSnapshot) {
                    batch.delete(doc.reference)
                }

                // añadimos al batch el borrado de cada entrada
                for (doc in ticketsSnapshot) {
                    batch.delete(doc.reference)
                }

                // añadimos al batch el borrado del documento padre (el usuario en si)
                batch.delete(userRef)

                // ejecutamos el borrado conjunto de forma atomica
                batch.commit()
                    .addOnSuccessListener {
                        // datos eliminados sin dejar huerfanos, desuscribimos de Firebase Cloud Messaging
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(FCM_TOPIC)
                            .addOnFailureListener { e -> Log.e(TAG, "Error al desuscribirse de $FCM_TOPIC al eliminar cuenta", e) }

                        // finalizamos borrando los datos de la cuenta en Firebase Authentication
                        user.delete()
                            .addOnSuccessListener {
                                (requireActivity() as? MainActivity)?.updateMenuForAuthState(false)
                                findNavController().navigate(R.id.homeFragment)
                                Toast.makeText(requireContext(), "Cuenta y datos eliminados correctamente", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { manageErrorMessage("Error al eliminar cuenta de Auth", "Error al eliminar la cuenta: ${it.message}") }
                    }
                    .addOnFailureListener { manageErrorMessage("Error en el batch de borrado de Firestore", "Error al eliminar los datos de la cuenta") }
            }.addOnFailureListener { manageErrorMessage("Error al obtener tickets para borrar", "Error conectando con la base de datos") }
        }.addOnFailureListener { manageErrorMessage("Error al obtener carrito para borrar", "Error conectando con la base de datos") }
    }

    // metodo para mostrar un mensaje de error y reactivar los botones
    private fun manageErrorMessage(logMessage: String, toastMessage: String) {
        Log.e(TAG, logMessage)
        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show()
        enableButtons()
    }

    // metodo para pedirle al usuario que cierre sesion y vuelva a autenticarse para borrar con exito sus datos
    private fun showReAuthRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Re-autenticación necesaria")
            .setMessage("Por motivos de seguridad, para eliminar tu cuenta permanentemente debes haber iniciado sesión recientemente. Por favor, cierra sesión y vuelve a entrar para completar el proceso.")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // metodo para reactivar los botones tras un error
    private fun enableButtons() {
        binding.btnDeleteAccount.isEnabled = true
        binding.btnLogout.isEnabled = true
        binding.btnSave.isEnabled = true
    }

    // metodo para comprobar el permiso de notificaciones en ejecucion segun version del dispositivo
    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // ya tiene el permiso, no hacemos nada, dejamos que guarde los cambios luego
            } else {
                // lanzamos la peticion emergente
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // metodo para mostrar un cuadro de dialogo para informar de permiso denegado y acceder a ajustes para habilitar las notificaciones
    private fun showNotificationPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permisos bloqueados")
            .setMessage("Has denegado las notificaciones permanentemente. Para activarlas, ve a los ajustes de tu teléfono.")
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
