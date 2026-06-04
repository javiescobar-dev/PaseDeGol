package com.escobar.pasedegol.ui.ticket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.escobar.pasedegol.adapter.TicketAdapter
import com.escobar.pasedegol.databinding.FragmentTicketBinding
import com.escobar.pasedegol.model.Ticket
import com.google.android.material.transition.MaterialFadeThrough
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class TicketFragment : Fragment() {

    private var _binding: FragmentTicketBinding? = null
    private val binding get() = _binding!!

    // instancias de Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // adapter del RecyclerView
    private lateinit var ticketAdapter: TicketAdapter

    // listener para escuchar cambios en los tickets en tiempo real
    private var ticketListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "TicketFragment"
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
        _binding = FragmentTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // configurar el RecyclerView
        setupRecyclerView()

        // escuchar los tickets del cliente en tiempo real
        listenToTickets()
    }

    // metodo para configurar el RecyclerView con el adapter de tickets
    private fun setupRecyclerView() {
        ticketAdapter = TicketAdapter { ticket ->
            // al pulsar un ticket, abrir la pantalla de detalle de la entrada
            openTicketDetail(ticket)
        }
        binding.rvTickets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTickets.adapter = ticketAdapter
    }

    // metodo para abrir la pantalla de detalle de la entrada
    private fun openTicketDetail(ticket: Ticket) {
        val intent = Intent(requireContext(), TicketDetailActivity::class.java).apply {
            putExtra(TicketDetailActivity.EXTRA_HOME_TEAM, ticket.homeTeamName)
            putExtra(TicketDetailActivity.EXTRA_AWAY_TEAM, ticket.awayTeamName)
            putExtra(TicketDetailActivity.EXTRA_HOME_BADGE, ticket.homeBadgeUrl)
            putExtra(TicketDetailActivity.EXTRA_AWAY_BADGE, ticket.awayBadgeUrl)
            putExtra(TicketDetailActivity.EXTRA_STADIUM, ticket.stadium)
            putExtra(TicketDetailActivity.EXTRA_QUANTITY, ticket.quantity)
            putExtra(TicketDetailActivity.EXTRA_TOTAL_PRICE, ticket.totalPrice)
            putExtra(TicketDetailActivity.EXTRA_MATCH_DATE, ticket.matchDate)
            putExtra(TicketDetailActivity.EXTRA_PURCHASE_DATE, ticket.purchaseDate.toDate().time)
            putExtra(TicketDetailActivity.EXTRA_QR_CODIGO, ticket.qrCodigo)
        }
        startActivity(intent)
    }

    // metodo para escuchar los tickets del cliente en tiempo real
    private fun listenToTickets() {
        val user = auth.currentUser ?: return

        // mostrar indicador de carga mientras se obtienen los datos
        showLoading()

        // registrar el SnapshotListener sobre la subcoleccion tickets del usuario
        // ordenados por fecha de compra descendente (los mas recientes primero)
        ticketListener = db.collection("users").document(user.uid).collection("tickets")
            .orderBy("purchaseDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                // en caso de error, notificar y terminar
                if (error != null) {
                    Log.e(TAG, "Error al escuchar los tickets", error)
                    Toast.makeText(requireContext(), "Error al cargar las entradas", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // convertir los documentos de Firestore a objetos Ticket
                    val tickets = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Ticket::class.java)
                    }

                    // actualizar el adapter con la nueva lista
                    ticketAdapter.submitList(tickets)

                    // actualizar la visibilidad de los elementos
                    updateVisibility(tickets)
                }
            }
    }

    // metodo para gestionar la visibilidad de los elementos dependiendo del estado
    private fun updateVisibility(tickets: List<Ticket>) {
        if (tickets.isEmpty()) {
            showEmpty()
        } else {
            showContent()
        }
    }

    // metodos para gestionar los estados visuales
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvTickets.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvTickets.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
    }

    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.rvTickets.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // desuscribirse del listener para evitar fugas de memoria
        ticketListener?.remove()
        _binding = null
    }
}
