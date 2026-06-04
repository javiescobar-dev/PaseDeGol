package com.escobar.pasedegol.ui.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.escobar.pasedegol.adapter.AdminTeamAdapter
import com.escobar.pasedegol.databinding.ActivityManageTeamsBinding
import com.escobar.pasedegol.model.Team
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage

class ManageTeamsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageTeamsBinding

    // instancias de Firebase
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // adapter del RecyclerView
    private lateinit var teamAdapter: AdminTeamAdapter

    // listener para escuchar cambios en tiempo real
    private var teamsListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "ManageTeamsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageTeamsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // configurar RecyclerView
        setupRecyclerView()

        // escuchar cambios en la coleccion de equipos en tiempo real
        listenToTeams()

        // configurar boton flotante para crear nuevo equipo
        binding.fabAddTeam.setOnClickListener {
            // enviamos el intent sin extras para crear un nuevo equipo
            val intent = Intent(this, TeamEditActivity::class.java)
            startActivity(intent)
        }
    }

    // metodo para configurar el RecyclerView con el adapter y los callbacks de editar y eliminar
    private fun setupRecyclerView() {
        teamAdapter = AdminTeamAdapter(
            onEditClick = { team ->
                // navegar a la edicion del equipo enviando el id en el intent
                // al enviar el intent con extra del id del equipo, se abre el activity en modo edicion
                val intent = Intent(this, TeamEditActivity::class.java).apply {
                    putExtra(TeamEditActivity.EXTRA_TEAM_ID, team.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { team ->
                // mostrar dialogo de confirmacion antes de eliminar
                showDeleteConfirmation(team)
            }
        )
        binding.rvTeams.adapter = teamAdapter
    }

    // metodo para escuchar cambios en la coleccion teams de Firestore en tiempo real
    private fun listenToTeams() {
        // mostrar estado de carga inicialmente
        showLoading()

        // cargamos los equipos en orden alfabetico
        teamsListener = db.collection("teams")
            .orderBy("name")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en equipos", error)
                    showEmpty()
                    Toast.makeText(this, "Error al cargar los equipos. Comprueba tu conexión", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // validaciones de seguridad
                if (snapshots == null || snapshots.isEmpty) {
                    showEmpty()
                    return@addSnapshotListener
                }

                // convertir documentos a objetos Team
                val teams = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Team::class.java)?.copy(id = doc.id)
                }

                // enviar la lista de equipos al adapter
                teamAdapter.submitList(teams)
                showContent()

                Log.d(TAG, "Lista de equipos actualizada: ${teams.size} equipos")
            }
    }

    // metodo mostrar dialogo de confirmacion antes de eliminar un equipo
    private fun showDeleteConfirmation(team: Team) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar equipo")
            .setMessage("¿Estás seguro de que deseas eliminar el equipo \"${team.name}\"? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                deleteTeam(team)
            }
            .show()
    }

    // metodo para eliminar equipo de Firestore y su imagen de Storage
    private fun deleteTeam(team: Team) {
        // primero eliminar el documento de Firestore
        db.collection("teams").document(team.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Equipo eliminado correctamente", Toast.LENGTH_SHORT).show()

                // si el equipo tiene imagen, eliminarla de Storage
                if (team.badgeUrl.isNotEmpty()) {
                    try {
                        // buscar en Storage la imagen del equipo por su URL y la borramos
                        storage.getReferenceFromUrl(team.badgeUrl).delete()
                            .addOnSuccessListener {
                                Log.d(TAG, "Imagen del equipo eliminada de Storage")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error al eliminar imagen de Storage", e)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "URL de imagen no valida para Storage", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al eliminar equipo", e)
                Toast.makeText(this, "Error al eliminar el equipo", Toast.LENGTH_SHORT).show()
            }
    }

    // metodos para gestionar los estados de la interfaz
    // mostrar estado de carga
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvTeams.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }
    // mostrar el contenido
    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.rvTeams.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }
    // mostrar contenido vacio
    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvTeams.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // desuscribirse del listener para evitar fugas de memoria
        teamsListener?.remove()
    }
}
