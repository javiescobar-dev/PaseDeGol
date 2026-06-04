package com.escobar.pasedegol.ui.admin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.escobar.pasedegol.adapter.AdminMatchAdapter
import com.escobar.pasedegol.databinding.ActivityManageMatchesBinding
import com.escobar.pasedegol.model.Match
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ManageMatchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageMatchesBinding

    // instancia de Firestore
    private val db = FirebaseFirestore.getInstance()

    // adapter del RecyclerView
    private lateinit var matchAdapter: AdminMatchAdapter

    // listener para escuchar cambios en tiempo real
    private var matchesListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "ManageMatchesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageMatchesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // configurar RecyclerView
        setupRecyclerView()

        // escuchar cambios en la coleccion de partidos en tiempo real
        listenToMatches()

        // configurar boton flotante para crear nuevo partido
        binding.fabAddMatch.setOnClickListener {
            // enviamos el intent sin extras para crear un nuevo partido
            val intent = Intent(this, MatchEditActivity::class.java)
            startActivity(intent)
        }
    }

    // metodo para configurar el RecyclerView con el adapter y los callbacks de editar y eliminar
    private fun setupRecyclerView() {
        matchAdapter = AdminMatchAdapter(
            onEditClick = { match ->
                // navegar a la edicion del partido enviando el id en el intent
                // al enviar el intent con extra del id del partido, se abre el activity en modo edicion
                val intent = Intent(this, MatchEditActivity::class.java).apply {
                    putExtra(MatchEditActivity.EXTRA_MATCH_ID, match.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { match ->
                // mostrar dialogo de confirmacion antes de eliminar
                showDeleteConfirmation(match)
            }
        )
        binding.rvMatches.adapter = matchAdapter
    }

    // metodo para escuchar cambios en la coleccion matches de Firestore en tiempo real
    private fun listenToMatches() {
        // mostrar estado de carga inicialmente
        showLoading()

        // cargamos los partidos en orden descendente por fecha
        matchesListener = db.collection("matches")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en partidos", error)
                    showEmpty()
                    Toast.makeText(this, "Error al cargar los partidos. Comprueba tu conexión", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // validaciones de seguridad
                if (snapshots == null || snapshots.isEmpty) {
                    showEmpty()
                    return@addSnapshotListener
                }

                // convertir los documentos de Firestore a objetos Match
                val matches = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Match::class.java)?.copy(id = doc.id)
                }

                // enviar la lista de partidos al adapter
                matchAdapter.submitList(matches)
                showContent()

                Log.d(TAG, "Lista de partidos actualizada: ${matches.size} partidos")
            }
    }

    // metodo para mostrar dialogo de confirmacion antes de eliminar un partido
    private fun showDeleteConfirmation(match: Match) {
        val title = "${match.homeTeam.name} vs ${match.awayTeam.name}"
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar partido")
            .setMessage("¿Estás seguro de que deseas eliminar el partido \"$title\"? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                deleteMatch(match)
            }
            .show()
    }

    // metodo para eliminar partido de Firestore
    private fun deleteMatch(match: Match) {
        db.collection("matches").document(match.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Partido eliminado correctamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al eliminar partido", e)
                Toast.makeText(this, "Error al eliminar el partido", Toast.LENGTH_SHORT).show()
            }
    }

    // metodos para gestionar los estados de la interfaz
    // mostrar estado de carga
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvMatches.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }
    // mostrar el contenido
    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.rvMatches.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }
    // mostrar contenido vacio
    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvMatches.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // desuscribirse del listener para evitar fugas de memoria
        matchesListener?.remove()
    }
}
