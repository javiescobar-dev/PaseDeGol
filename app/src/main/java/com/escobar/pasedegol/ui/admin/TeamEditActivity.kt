package com.escobar.pasedegol.ui.admin

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ActivityTeamEditBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class TeamEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamEditBinding

    // instancias de Firebase
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // URI de la imagen seleccionada desde la galeria
    private var selectedImageUri: Uri? = null

    // ID del equipo, si es nulo, estamos en modo creacion, sino, estamos en modo edicion
    private var teamId: String? = null

    // URL actual de la imagen del equipo (para edicion)
    private var currentBadgeUrl: String = ""

    companion object {
        private const val TAG = "TeamEditActivity"
        const val EXTRA_TEAM_ID = "extra_team_id"
    }

    // launcher para seleccionar imagen desde la galeria
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            // mostrar la imagen seleccionada en el ImageView
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(binding.ivBadge)
            // quitar el padding del icono placeholder al mostrar la imagen real
            binding.ivBadge.setPadding(0, 0, 0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // comprobar si estamos en modo edicion
        teamId = intent.getStringExtra(EXTRA_TEAM_ID)
        if (teamId != null) {
            // modo edicion: cambiar titulo y cargar datos del equipo
            binding.toolbar.title = "Editar Equipo"
            loadTeamData(teamId!!)
        }

        // configurar clic en la imagen para seleccionar desde la galeria del dispositivo
        binding.cardBadge.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // configurar boton guardar
        binding.btnSave.setOnClickListener {
            saveTeam()
        }
    }

    // metodo para cargar datos del equipo desde Firestore (modo edicion)
    private fun loadTeamData(id: String) {
        db.collection("teams").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: ""
                    currentBadgeUrl = doc.getString("badgeUrl") ?: ""

                    binding.etTeamName.setText(name)

                    // cargar imagen actual si existe
                    if (currentBadgeUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(currentBadgeUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_add_image)
                            .into(binding.ivBadge)
                        binding.ivBadge.setPadding(0, 0, 0, 0)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar datos del equipo", e)
                Toast.makeText(this, "Error al cargar el equipo", Toast.LENGTH_SHORT).show()
                finish()
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

    // metodo para validar y guardar el equipo
    private fun saveTeam() {
        val name = binding.etTeamName.text.toString().trim()

        // validar que el nombre no este vacio
        if (name.isEmpty()) {
            binding.etTeamName.error = "El nombre es obligatorio"
            return
        }

        // validar que se ha seleccionado una imagen (obligatorio solo si es nuevo)
        if (selectedImageUri == null && teamId == null) {
            Toast.makeText(this, "Selecciona una imagen para el escudo", Toast.LENGTH_SHORT).show()
            return
        }

        // comprobar conexion a internet y mostrar mensaje informativo si no la hay, ya que
        // firestore encola la operacion y la sincroniza automaticamente cuando se recupera la conexion
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Sin conexión a Internet. Los cambios se guardarán automáticamente cuando se recupere la conexión.", Toast.LENGTH_SHORT).show()
            return
        }

        // deshabilitar boton y mostrar progreso
        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // comprobar si hay nueva imagen y si es el caso, subirla a Storage y luego guardar en Firestore
        if (selectedImageUri != null) {
            // hay nueva imagen: subir primero a Storage y luego guardar en Firestore
            uploadImageAndSave(name)
        } else {
            // no hay nueva imagen (modo edicion sin cambiar imagen): guardar directamente
            saveToFirestore(name, currentBadgeUrl)
        }
    }

    // metodo para subir imagen a Firebase Storage y luego guardar el equipo
    private fun uploadImageAndSave(name: String) {
        // generar un nombre unico aleatorio para la imagen
        val imageRef = storage.reference.child("teams/${UUID.randomUUID()}.jpg")

        // subir la imagen a Storage
        imageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                // imagen subida con exito: obtener la URL publica de la imagen subida
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // si estamos editando y habia una imagen anterior, eliminarla de Storage
                    if (teamId != null && currentBadgeUrl.isNotEmpty()) {
                        try {
                            storage.getReferenceFromUrl(currentBadgeUrl).delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al eliminar imagen anterior", e)
                        }
                    }
                    // guardar el equipo con la URL de la imagen subida
                    saveToFirestore(name, downloadUri.toString())
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener URL de la imagen", e)
                    Toast.makeText(this, "Error al obtener la URL de la imagen", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                // error al subir imagen a Storage, mostrar mensaje de error y volver a habilitar el boton y ocultar el progreso
                Log.e(TAG, "Error al subir imagen a Storage", e)
                Toast.makeText(this, "Error al subir la imagen", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
    }

    // metodo para guardar o actualizar el equipo en Firestore
    private fun saveToFirestore(name: String, badgeUrl: String) {
        // construir los datos del equipo almacenando nombre y URL del escudo directamente en un mapa
        val teamData = hashMapOf(
            "name" to name,
            "badgeUrl" to badgeUrl
        )

        // comprobar el id del equipo para saber si es edicion o creacion
        if (teamId != null) {
            // modo edicion: actualizar documento existente
            db.collection("teams").document(teamId!!).set(teamData)
                .addOnSuccessListener {
                    // mostrar mensaje de exito y volver a la actividad anterior
                    Toast.makeText(this, "Equipo actualizado correctamente", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    // mostrar mensaje de error y volver a habilitar el boton y ocultar el progreso
                    Log.e(TAG, "Error al actualizar equipo", e)
                    Toast.makeText(this, "Error al actualizar el equipo", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
        } else {
            // modo creacion: insertar nuevo documento
            db.collection("teams").add(teamData)
                .addOnSuccessListener {
                    // mostrar mensaje de exito y volver a la actividad anterior
                    Toast.makeText(this, "Equipo creado correctamente", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    // mostrar mensaje de error y volver a habilitar el boton y ocultar el progreso
                    Log.e(TAG, "Error al crear equipo", e)
                    Toast.makeText(this, "Error al crear el equipo", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
        }
    }
}
