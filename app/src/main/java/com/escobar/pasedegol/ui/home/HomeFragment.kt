package com.escobar.pasedegol.ui.home

import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.escobar.pasedegol.adapter.MatchAdapter
import com.escobar.pasedegol.databinding.FragmentHomeBinding
import com.escobar.pasedegol.model.Match
import com.escobar.pasedegol.ui.cart.CartActivity
import com.google.android.material.transition.MaterialFadeThrough
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Instancia de Firestore para consultas a la base de datos
    private val db = FirebaseFirestore.getInstance()

    // Instancia de FirebaseAuth para comprobar el estado de autenticacion
    private val auth = FirebaseAuth.getInstance()

    // Referencia al listener para poder desuscribirnos al destruir la vista
    private var matchesListener: ListenerRegistration? = null

    // Adapter del RecyclerView con callback de click
    private lateinit var matchAdapter: MatchAdapter

    // Guardamos la referencia del listener para poder detenerlo luego (evitar crash con Firestore al cambiar de pantalla)
    private var cartListener: ListenerRegistration? = null

    // handler y runnable para gestionar el timeout de carga del catalogo
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var loadingTimeoutRunnable: Runnable? = null

    companion object {
        private const val TAG = "HomeFragment"
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // configurar la interfaz para mostrar los partidos
        setupRecyclerView()
        // configurar el boton flotante del carrito
        setupFab()
        // registrar un listener para escuchar cambios en la coleccion matches de Firestore (actualizar en tiempo real)
        listenToMatches()
        // registrar un listener para actualizar la cantidad de items en el carrito (el badge rojo)
        listenToCartUpdates()
    }

    // metodo para mostrar el catalogo de partidos
    private fun setupRecyclerView() {
        matchAdapter = MatchAdapter { match ->
            // si el usuario esta autenticado, navegar al detalle del partido
            if (auth.currentUser != null) {
                // le pasamos los datos del partido a la actividad a la que navegamos
                val intent = Intent(requireContext(), MatchDetailActivity::class.java).apply {
                    putExtra(MatchDetailActivity.EXTRA_MATCH_ID, match.id)
                    putExtra(MatchDetailActivity.EXTRA_HOME_TEAM, match.homeTeam.name)
                    putExtra(MatchDetailActivity.EXTRA_AWAY_TEAM, match.awayTeam.name)
                    putExtra(MatchDetailActivity.EXTRA_HOME_BADGE, match.homeTeam.badgeUrl)
                    putExtra(MatchDetailActivity.EXTRA_AWAY_BADGE, match.awayTeam.badgeUrl)
                    putExtra(MatchDetailActivity.EXTRA_STADIUM, match.stadium)
                    putExtra(MatchDetailActivity.EXTRA_PRICE, match.price)
                    putExtra(MatchDetailActivity.EXTRA_STOCK, match.stock)
                    putExtra(MatchDetailActivity.EXTRA_DATE, match.date.toDate().time)
                }
                startActivity(intent)
            } else {
                // si no esta autenticado, mostrar un mensaje
                Toast.makeText(requireContext(), "Debes iniciar sesión para acceder a este contenido", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvMatches.adapter = matchAdapter
    }

    // metodo para configurar el boton flotante del carrito
    private fun setupFab() {
        // Mostrar FAB solo si el usuario esta autenticado
        val isAuthenticated = auth.currentUser != null
        binding.fabCart.visibility = if (isAuthenticated) View.VISIBLE else View.GONE

        // configurar el clic del boton flotante para ir al carrito
        binding.fabCart.setOnClickListener {
            val intent = Intent(requireContext(), CartActivity::class.java)
            startActivity(intent)
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

    // metodo para escuchar cambios en la coleccion de partidos, cada vez que se realiza un cambio en firestore se actualiza la lista
    private fun listenToMatches() {
        // no hay conexion, avisamos y evitamos intentar cargar
        if (!isNetworkAvailable()) {
            showEmpty()
            Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu red.", Toast.LENGTH_LONG).show()
            return
        }

        // Mostrar indicador de carga inicialmente
        showLoading()

        // configurar un timeout de 10 segundos para evitar una carga infinita cuando no hay conexion ni datos en cache
        loadingTimeoutRunnable = Runnable {
            if (isAdded && binding.progressBar.visibility == View.VISIBLE) {
                showEmpty()
                Toast.makeText(requireContext(), "Sin conexión a Internet. Comprueba tu conexión e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
        loadingHandler.postDelayed(loadingTimeoutRunnable!!, 10000)

        // cargamos la lista de partidos desde Firestore (coleccion matches)
        matchesListener = db.collection("matches")
            .whereGreaterThan("date", Timestamp.now())  // solo partidos futuros
            .orderBy("date", Query.Direction.ASCENDING) // orden ascendente (los mas proximos primero)
            .addSnapshotListener { snapshots, error ->
                // cancelar el timeout de carga ya que hemos recibido respuesta
                loadingTimeoutRunnable?.let { loadingHandler.removeCallbacks(it) }

                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en partidos", error)
                    showEmpty()
                    Toast.makeText(requireContext(), "Error al cargar los partidos. Comprueba tu conexión", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // si no hay documentos, mostrar vacio
                if (snapshots == null || snapshots.isEmpty) {
                    showEmpty()
                    return@addSnapshotListener
                }

                // Convertir documentos de Firestore a objetos Match
                val matches = snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Match::class.java)?.copy(id = doc.id)
                }

                // actualizar la lista del adapter y mostrar el contenido
                matchAdapter.submitList(matches)
                showContent()

                // para depurar, mostrar en el log el numero de partidos cargados
                Log.d(TAG, "Catalogo actualizado: ${matches.size} partidos cargados")
            }
    }

    // metodo para mostrar un indicador de carga y ocultar el contenido
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvMatches.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    // metodo para ocultar el indicador de carga y mostrar el contenido
    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.rvMatches.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    // metodo para ocultar el contenido y mostrar el estado de vacio
    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvMatches.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    // Metodo para escuchar en tiempo real los cambios en el carrito y actualizar el badge del carrito
    private fun listenToCartUpdates() {
        val user = auth.currentUser ?: return

        // alamcenamos el listener en una variable para poder detenerlo luego y evitar crash
        cartListener = db.collection("users").document(user.uid).collection("cart")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // depurar error al escuchar cambios en el carrito
                    Log.e(TAG, "Error al escuchar cambios en el carrito", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    var totalItems = 0L

                    for (document in snapshot.documents) {
                        val quantity = document.getLong("quantity") ?: 0L
                        totalItems += quantity
                    }

                    if (totalItems > 0) {
                        binding.tvCartBadge.visibility = View.VISIBLE
                        // si hay mas de 99, mostramos 99+ (por no descuadrar el circulito)
                        binding.tvCartBadge.text = if (totalItems > 99) "99+" else totalItems.toString()
                    } else {
                        binding.tvCartBadge.visibility = View.GONE
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Desuscribirse del listener para evitar fugas de memoria
        matchesListener?.remove()
        cartListener?.remove() // Detenemos el listener del carrito cuando se destruye la vista
        // cancelar el timeout de carga pendiente si existe
        loadingTimeoutRunnable?.let { loadingHandler.removeCallbacks(it) }
        _binding = null
    }
}
