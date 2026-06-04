package com.escobar.pasedegol.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ActivityMatchDetailBinding
import com.escobar.pasedegol.model.Match
import com.escobar.pasedegol.model.Team
import com.escobar.pasedegol.ui.cart.CartActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

class MatchDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchDetailBinding

    // Instancia de Firestore para consultas a la base de datos
    private val db = FirebaseFirestore.getInstance()

    // Instancia de FirebaseAuth para comprobar el estado de autenticacion
    private val auth = FirebaseAuth.getInstance()

    // datos del partido recibidos por intent
    private var matchId = ""
    private var homeTeamName = ""
    private var awayTeamName = ""
    private var homeBadgeUrl = ""
    private var awayBadgeUrl = ""
    private var stadium = ""
    private var price = 0.0
    private var stock = 0
    private var matchDate: Long = 0L

    // cantidad de entradas seleccionadas por el usuario (por defecto 0)
    private var quantity = 0

    // añadimos la variable del listener para gestionar el stock de entradas en tiempo real
    private var matchStockListener: ListenerRegistration? = null

    // constantes estaticas
    companion object {
        private const val TAG = "MatchDetailActivity"

        // claves para el intent
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_HOME_TEAM = "home_team"
        const val EXTRA_AWAY_TEAM = "away_team"
        const val EXTRA_HOME_BADGE = "home_badge"
        const val EXTRA_AWAY_BADGE = "away_badge"
        const val EXTRA_STADIUM = "stadium"
        const val EXTRA_PRICE = "price"
        const val EXTRA_STOCK = "stock"
        const val EXTRA_DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // recuperar datos del intent
        extractIntentData()
        // configurar la interfaz con los datos del partido
        setupUI()
        // configurar los botones de cantidad y carrito
        setupButtons()
        // configuramos el listener para gestionar el stock de entradas en tiempo real
        setupStockListener()
    }

    // metodo para recuperar los datos del partido enviados desde HomeFragment
    private fun extractIntentData() {
        matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: ""
        homeTeamName = intent.getStringExtra(EXTRA_HOME_TEAM) ?: ""
        awayTeamName = intent.getStringExtra(EXTRA_AWAY_TEAM) ?: ""
        homeBadgeUrl = intent.getStringExtra(EXTRA_HOME_BADGE) ?: ""
        awayBadgeUrl = intent.getStringExtra(EXTRA_AWAY_BADGE) ?: ""
        stadium = intent.getStringExtra(EXTRA_STADIUM) ?: ""
        price = intent.getDoubleExtra(EXTRA_PRICE, 0.0)
        stock = intent.getIntExtra(EXTRA_STOCK, 0)
        matchDate = intent.getLongExtra(EXTRA_DATE, 0L)
        // cantidad inicial (posteriormente se actualiza)
        quantity = if (stock > 0) 1 else 0
    }

    // metodo para configurar la interfaz con los datos del partido
    private fun setupUI() {
        // configurar toolbar con boton de retroceso (la flecha de la izquierda superior)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // formatear la fecha y hora del partido
        val locale = Locale.forLanguageTag("es-ES")
        val date = java.util.Date(matchDate)
        val timeFormat = SimpleDateFormat("HH:mm", locale)
        val dateFormat = SimpleDateFormat("dd 'de' MMMM", locale)

        // establecemos la fecha y hora
        binding.tvTime.text = timeFormat.format(date)
        binding.tvDate.text = dateFormat.format(date)

        // nombres de los equipos
        binding.tvHomeTeam.text = homeTeamName
        binding.tvAwayTeam.text = awayTeamName

        // estadio
        binding.tvStadium.text = stadium

        // precio por entrada
        binding.tvPrice.text = String.format(locale, "%.2f €", price)

        // configuramos el stock que se muestra
        setupStockDisplay()

        // cargar escudos con Glide
        Glide.with(this)
            .load(homeBadgeUrl) // local
            .placeholder(R.drawable.ic_catalog)
            .error(R.drawable.ic_catalog)
            .into(binding.ivHomeBadge)

        Glide.with(this)
            .load(awayBadgeUrl) // visitante
            .placeholder(R.drawable.ic_catalog)
            .error(R.drawable.ic_catalog)
            .into(binding.ivAwayBadge)
    }

    // metodo para configurar los botones de cantidad y carrito (que no baje de 0)
    private fun setupButtons() {
        // boton para disminuir la cantidad
        binding.btnMinus.setOnClickListener {
            if (quantity > 0) {
                quantity--
                updateQuantityDisplay()
            }
        }

        // boton para aumentar la cantidad (el maximo no superar el stock)
        binding.btnPlus.setOnClickListener {
            if (quantity < stock) {
                quantity++
                updateQuantityDisplay()
            } else {
                Toast.makeText(this, "No hay más entradas disponibles", Toast.LENGTH_SHORT).show()
            }
        }

        // boton para añadir al carrito
        binding.btnAddToCart.setOnClickListener {
            addToCart()
        }

        // boton para ir al carrito (se mostrara despues de añadir al carrito)
        binding.btnGoToCart.setOnClickListener {
            val intent = Intent(this, CartActivity::class.java)
            startActivity(intent)
        }
    }

    // metodo para actualizar la cantidad mostrada y el total calculado
    private fun updateQuantityDisplay() {
        val locale = Locale.forLanguageTag("es-ES")
        // actualizar la cantidad mostrada
        binding.tvQuantity.text = quantity.toString()
        binding.tvQuantityLabel.text = "Cantidad: $quantity"
        // actualizar el total
        val total = price * quantity
        binding.tvTotal.text = String.format(locale, "Total: %.2f €", total)
    }

    // metodo para añadir el partido al carrito del usuario en Firestore
    private fun addToCart() {
        val user = auth.currentUser
        // no deberia poder haber llegado hasta aqui si no esta autenticado, pero por prevenir
        if (user == null) {
            Toast.makeText(this, "Debes iniciar sesión para comprar", Toast.LENGTH_SHORT).show()
            return
        }

        // verificar que la cantidad sea valida
        if (quantity <= 0) {
            Toast.makeText(this, "Debes seleccionar una cantidad", Toast.LENGTH_SHORT).show()
            return
        }
        else if (quantity > stock) {
            Toast.makeText(this, "Cantidad no válida, no hay suficientes entradas disponibles", Toast.LENGTH_SHORT).show()
            return
        }

        // desactivar el boton temporalmente para que el usuario no haga doble clic rapido
        binding.btnAddToCart.isEnabled = false
        binding.btnAddToCart.text = "AÑADIENDO..."

        // referencia a la subcoleccion cart del usuario
        val cartRef = db.collection("users").document(user.uid).collection("cart")

        // comprobar si ya existe este partido en el carrito
        cartRef.document(matchId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // si ya existe, actualizar la cantidad de entradas sumando la nueva
                    val currentQuantity = doc.getLong("quantity")?.toInt() ?: 0
                    val newQuantity = currentQuantity + quantity

                    // no superar el stock disponible
                    if (newQuantity > stock) {
                        Toast.makeText(
                            this,
                            "No puedes añadir más de $stock entradas para este partido",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }

                    cartRef.document(matchId).update("quantity", newQuantity)
                        .addOnSuccessListener { onCartSuccess() } // si se ha actualizado correctamente
                        .addOnFailureListener { handleCartError(it) } // si ocurre un error
                } else {
                    // si no existe, crear nuevo documento en el carrito
                    val cartItem = hashMapOf(
                        "matchId" to matchId,
                        "homeTeamName" to homeTeamName,
                        "awayTeamName" to awayTeamName,
                        "homeBadgeUrl" to homeBadgeUrl,
                        "awayBadgeUrl" to awayBadgeUrl,
                        "stadium" to stadium,
                        "quantity" to quantity,
                        "price" to price,
                        "matchDate" to matchDate
                    )

                    cartRef.document(matchId).set(cartItem)
                        .addOnSuccessListener { onCartSuccess() } // si se ha creado correctamente
                        .addOnFailureListener { handleCartError(it) } // si ocurre un error
                }
            }
            .addOnFailureListener { handleCartError(it) } // en caso de error
    }

    // metodo que se ejecuta cuando se ha añadido correctamente al carrito
    private fun onCartSuccess() {
        Toast.makeText(this, "Añadido al carrito", Toast.LENGTH_SHORT).show()
        // resetear el boton para que pueda ser pulsado de nuevo
        resetAddToCartButton()
        // mostrar el boton de ir al carrito
        binding.btnGoToCart.visibility = View.VISIBLE
    }

    // metodo que se ejecuta cuando ocurre un error al añadir al carrito
    private fun handleCartError(e: Exception) {
        Log.e(TAG, "Error en carrito", e)
        Toast.makeText(this, "Error al añadir al carrito", Toast.LENGTH_SHORT).show()
        resetAddToCartButton()
    }

    // metodo para resetear el boton de añadir al carrito
    private fun resetAddToCartButton() {
        binding.btnAddToCart.isEnabled = true
        binding.btnAddToCart.text = "AÑADIR AL CARRITO"
    }

    private fun setupStockDisplay() {
        // stock disponible (en caso de no haber stock, mostramos agotado y deshabilitamos los botones)
        if (stock > 0) {
            binding.tvStock.text = "Entradas disponibles: $stock"
            // asegurarnos de habilitarlos por si antes estaba agotado
            binding.btnAddToCart.isEnabled = true
            binding.btnAddToCart.text = "AÑADIR AL CARRITO"
            binding.btnPlus.isEnabled = true
            binding.btnMinus.isEnabled = true
        } else {
            binding.tvStock.text = "Entradas agotadas"
            binding.btnAddToCart.isEnabled = false
            binding.btnAddToCart.text = "AGOTADO"
            binding.btnPlus.isEnabled = false
            binding.btnMinus.isEnabled = false
            quantity = 0 // si no hay stock, forzamos cantidad a 0
        }

        // cantidad inicial y total
        updateQuantityDisplay()
    }

    private fun setupStockListener() {
        // escuchar solo el documento de este partido
        matchStockListener = db.collection("matches").document(matchId)
            .addSnapshotListener { snapshot, error ->
                // en caso de error, notificar y terminar
                if (error != null) {
                    Log.e(TAG, "Error al escuchar cambios en el stock", error)
                    Toast.makeText(this, "Error al actualizar la disponibilidad de entradas", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // si el documento no existe, el partido ha sido eliminado, gestionamos el error en lugar de proseguir
                if (snapshot == null || !snapshot.exists()) {
                    // desuscribirse del listener para evitar multiples disparos
                    matchStockListener?.remove()

                    // mostrar dialogo informativo y cerrar la actividad
                    AlertDialog.Builder(this)
                        .setTitle("Partido no disponible")
                        .setMessage("Este partido ya no está disponible. Es posible que haya sido eliminado.")
                        .setCancelable(false)
                        .setPositiveButton("Aceptar") { _, _ -> finish() }
                        .show()

                    return@addSnapshotListener
                }

                // obtener el nuevo stock
                val newStock = snapshot.getLong("stock")?.toInt() ?: 0

                // si el stock ha cambiado respecto al que teniamos del Intent, se actualiza
                if (this.stock != newStock) {
                    this.stock = newStock

                    // comprobar si no quedan entradas disponibles y actualizar interfaz
                    if (this.stock <= 0) {
                        // no quedan entradas disponibles
                        // si el usuario ya tenia entradas seleccionadas y se queda sin stock, reseteamos la seleccion
                        this.quantity = 0
                        setupStockDisplay()
                    } else {
                        // quedan entradas disponibles
                        // si el usuario ya tenia entradas seleccionadas y el stock baja, le bajamos la cantidad al maximo disponible
                        if (this.quantity > this.stock) {
                            this.quantity = this.stock
                            Toast.makeText(this, "El stock ha bajado, cantidad ajustada", Toast.LENGTH_SHORT).show()
                        }
                        setupStockDisplay()
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // antes de destruir la vista, desuscribirnos al listener
        matchStockListener?.remove()
    }
}
