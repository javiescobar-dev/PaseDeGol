package com.escobar.pasedegol.ui.cart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.escobar.pasedegol.MainActivity
import com.escobar.pasedegol.adapter.CartAdapter
import com.escobar.pasedegol.databinding.ActivityCartBinding
import com.escobar.pasedegol.model.CartItem
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.PaymentSheet.Builder
import java.util.Locale
import java.util.UUID

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding

    // instancias de Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // adapter del RecyclerView
    private lateinit var cartAdapter: CartAdapter

    // listener para escuchar cambios en el carrito en tiempo real
    private var cartListener: ListenerRegistration? = null

    // instancia de Firebase Functions
    private lateinit var functions: FirebaseFunctions

    // instancia de PaymentSheet de Stripe
    private lateinit var paymentSheet: PaymentSheet
    private var currentClientSecret: String? = null

    companion object {
        private const val TAG = "CartActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // inicializar el PaymentSheet de Stripe
        setupStripe()

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // configurar el RecyclerView
        setupRecyclerView()

        // configurar los botones de pagar y vaciar carrito
        setupButtons()

        // escuchar los cambios del carrito en tiempo real
        listenToCart()
    }

    private fun setupStripe() {
        // inicializar Firebase Functions
        functions = Firebase.functions

        // inicializar PaymentSheet (el callback maneja el resultado del pago)
        // preparar la pantalla de pago y al finalizar avisar a onPaymentSheetResult
        paymentSheet = Builder(::onPaymentSheetResult).build(this)
    }

    // metodo para configurar el RecyclerView con el adapter del carrito
    private fun setupRecyclerView() {
        cartAdapter = CartAdapter { cartItem ->
            // al pulsar el boton de eliminar, mostrar dialogo de confirmacion
            showDeleteConfirmation(cartItem)
        }
        binding.rvCart.layoutManager = LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter
    }

    // metodo para configurar los botones de la seccion inferior
    private fun setupButtons() {
        // boton de pagar (placeholder para Stripe)
        binding.btnPay.setOnClickListener { startPayment() }

        // boton de vaciar carrito con dialogo de confirmacion
        binding.btnClearCart.setOnClickListener {
            showClearCartConfirmation()
        }
    }

    // metodo para escuchar los cambios del carrito del usuario en tiempo real
    private fun listenToCart() {
        val user = auth.currentUser ?: return

        // mostrar indicador de carga mientras se obtienen los datos
        showLoading()

        // registrar el SnapshotListener sobre la subcoleccion cart del usuario
        cartListener = db.collection("users").document(user.uid).collection("cart")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al escuchar el carrito", error)
                    Toast.makeText(this, "Error al cargar el carrito", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // convertir los documentos de Firestore a objetos CartItem
                    val cartItems = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CartItem::class.java)?.copy(id = doc.id)
                    }

                    // actualizar el adapter con la nueva lista
                    cartAdapter.submitList(cartItems)

                    // actualizar el total y la visibilidad de los elementos
                    updateTotal(cartItems)
                    updateVisibility(cartItems)
                }
            }
    }

    // metodo para actualizar el total del carrito
    private fun updateTotal(items: List<CartItem>) {
        val locale = Locale.forLanguageTag("es-ES")
        val total = items.sumOf { it.calculateTotal() }
        binding.tvTotal.text = String.format(locale, "Total: %.2f €", total)
    }

    // metodo para gestionar la visibilidad de los elementos dependiendo del estado del carrito
    private fun updateVisibility(items: List<CartItem>) {
        if (items.isEmpty()) {
            // carrito vacio: mostrar mensaje y ocultar lista y botones
            showEmpty()
        } else {
            // carrito con items: mostrar lista y botones
            showContent()
        }
    }

    // metodos para gestionar los estados visuales del carrito
    // metodo que muestra un indicador de carga mientras se obtienen los datos
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvCart.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutBottom.visibility = View.GONE
    }
    // metodo para mostrar el estado de vacio
    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvCart.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.layoutBottom.visibility = View.GONE
    }
    // metodo para mostrar el contenido del carrito
    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.rvCart.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutBottom.visibility = View.VISIBLE
    }

    // metodo para mostrar un dialogo de confirmacion antes de eliminar un item del carrito
    private fun showDeleteConfirmation(cartItem: CartItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar del carrito")
            .setMessage("¿Quieres eliminar las entradas de ${cartItem.homeTeamName} vs ${cartItem.awayTeamName} del carrito?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteCartItem(cartItem)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // metodo para eliminar un item concreto del carrito en Firestore
    private fun deleteCartItem(cartItem: CartItem) {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).collection("cart")
            .document(cartItem.matchId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Entrada eliminada del carrito", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al eliminar la entrada", e)
                Toast.makeText(this, "Error al eliminar la entrada", Toast.LENGTH_SHORT).show()
            }
    }

    // metodo para mostrar un dialogo de confirmacion antes de vaciar el carrito
    private fun showClearCartConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Vaciar carrito")
            .setMessage("¿Estás seguro de que quieres vaciar todo el carrito?")
            .setPositiveButton("Vaciar") { _, _ ->
                clearCart()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // metodo para vaciar el carrito del usuario eliminando todos los documentos de la subcoleccion
    private fun clearCart() {
        // obtener referencia al carrito del usuario
        val user = auth.currentUser ?: return
        val cartRef = db.collection("users").document(user.uid).collection("cart")

        // obtener todos los documentos del carrito y eliminarlos uno a uno
        // ya que Firestore no permite eliminar subcolecciones enteras directamente
        cartRef.get()
            .addOnSuccessListener { snapshot ->
                // si no hay documentos, no hacer nada
                if (snapshot.isEmpty) return@addOnSuccessListener
                // utilizamos batch para realizar las eliminaciones de forma atomica, como una transaccion
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(cartRef.document(doc.id))
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Carrito vaciado", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al vaciar el carrito", e)
                        Toast.makeText(this, "Error al vaciar el carrito", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener items del carrito", e)
                Toast.makeText(this, "Error al vaciar el carrito", Toast.LENGTH_SHORT).show()
            }
    }

    // metodo para realizar validaciones previas antes de procesar el pago en Stripe
    private fun startPayment() {
        auth.currentUser ?: return

        // validar que el carrito no este vacio
        val cartItems = cartAdapter.currentList
        if (cartItems.isEmpty()) return

        // obtener el total del carrito en centimos y validar que no sea 0
        val totalDouble = cartAdapter.currentList.sumOf { it.calculateTotal() }
        val totalCents = Math.round(totalDouble * 100).toInt()
        if (totalCents <= 0) return

        // crear una lista de tareas para leer el estado real de los partidos en Firestore
        val validationTasks = cartItems.map { item ->
            db.collection("matches").document(item.matchId).get()
        }

        // deshabilitar boton mientras se realiza la validacion de los partidos
        binding.btnPay.isEnabled = false
        binding.btnPay.text = "Procesando..."

        // ejecutar todas las lecturas simultaneamente
        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(validationTasks)
            .addOnSuccessListener { snapshots ->
                var isValid = true
                var errorMessage = ""

                // comprobar el estado de cada partido devuelto por el servidor
                for ((index, snapshot) in snapshots.withIndex()) {
                    val item = cartItems[index]

                    // si el partido ha sido eliminado, informamos y finalizamos la validacion
                    if (!snapshot.exists()) {
                        isValid = false
                        errorMessage = "El partido ${item.homeTeamName} vs ${item.awayTeamName} ya no está disponible. Por favor, elimínalo de tu carrito."
                        break
                    }

                    // si el partido existe, comprobar si el stock sigue siendo suficiente
                    val currentStock = snapshot.getLong("stock") ?: 0L
                    if (currentStock < item.quantity) {
                        isValid = false
                        errorMessage = "Stock insuficiente para ${item.homeTeamName} vs ${item.awayTeamName}. Disponibles: $currentStock"
                        break
                    }
                }

                // si es correcto, procedemos a generar el cobro con Stripe
                if (isValid) {
                    callPaymentFunction(totalCents)
                } else {
                    // en caso de error, abortamos y avisamos al usuario
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    resetPayButton()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al validar el carrito", e)
                Toast.makeText(this, "Error al conectar con el servidor", Toast.LENGTH_SHORT).show()
                resetPayButton()
            }
    }

    // metodo para iniciar el proceso de pago con Stripe
    private fun callPaymentFunction(totalCents: Int) {
        // preparamos los datos para enviarlos a la funcion de Cloud Firestore
        val data = hashMapOf("amount" to totalCents)

        // llamamos a la funcion de Cloud Firestore para crear el PaymentIntent
        functions
            .getHttpsCallable("createPaymentIntent")
            .call(data)
            .addOnSuccessListener { result ->
                // si la llamada es exitosa, obtenemos el clientSecret de la respuesta
                val secret = (result.data as? Map<*, *>)?.get("clientSecret") as? String
                if (secret != null) {
                    currentClientSecret = secret
                    // iniciamos el proceso de pago con Stripe
                    presentPaymentSheet(secret)
                } else {
                    Toast.makeText(this, "Error al iniciar el pago", Toast.LENGTH_SHORT).show()
                    resetPayButton()
                }
            }
            .addOnFailureListener {
                Log.e("CartActivity", "Error Cloud Function", it)
                Toast.makeText(this, "Error al conectar con el servidor de pagos", Toast.LENGTH_SHORT).show()
                resetPayButton()
            }
    }

    // metodo para mostrar la pantalla de pago de Stripe
    private fun presentPaymentSheet(clientSecret: String) {
        // configuracion especifica de la pasarela de pago de Google Pay (opcional, Google Pay se configura por separado)
        val googlePayConfiguration = PaymentSheet.GooglePayConfiguration(
            environment = PaymentSheet.GooglePayConfiguration.Environment.Test, // cambiar a .Production para real
            countryCode = "ES",
            currencyCode = "EUR"
        )

        // cnfiguracion general del PaymentSheet para añadir la pasarela de pago de Google
        val configuration =
            PaymentSheet.Configuration.Builder(merchantDisplayName = "PaseDeGol")
                .googlePay(googlePayConfiguration)
                .allowsDelayedPaymentMethods(true) // para permitir mas posibilidades de metodos de pago
                .build()

        // mostrar la pasarela de pago, desde el dashboard de Stripe habilitamos los metodos de pago que queremos usar
        paymentSheet.presentWithPaymentIntent(clientSecret, configuration)
    }

    // metodo para manejar el resultado del pago de Stripe
    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> onPaymentSuccess()
            is PaymentSheetResult.Canceled -> {
                Toast.makeText(this, "Pago cancelado", Toast.LENGTH_SHORT).show()
                resetPayButton()
            }
            is PaymentSheetResult.Failed -> {
                Toast.makeText(this, "Error en el pago: ${result.error.message}", Toast.LENGTH_SHORT).show()
                resetPayButton()
            }
        }
    }

    // metodo para resetear el boton de pagar
    private fun resetPayButton() {
        binding.btnPay.isEnabled = true
        binding.btnPay.text = "PAGAR"
    }

    // metodo para manejar la generacion de tickets y vaciar el carrito al completar el pago con exito
    // utiliza una transaccion de Firestore para verificar el stock antes de generar los tickets
    private fun onPaymentSuccess() {
        val user = auth.currentUser ?: return
        val cartItems = cartAdapter.currentList

        // si el carrito esta vacio, no hacer nada
        if (cartItems.isEmpty()) return

        // deshabilitamos botones mientras se generan los tickets
        binding.btnPay.isEnabled = false
        binding.btnPay.text = "Generando entradas..."
        binding.btnClearCart.isEnabled = false

        // referencia a la subcoleccion de tickets del usuario
        val ticketsRef = db.collection("users").document(user.uid).collection("tickets")
        // referencia a la subcoleccion de cart del usuario
        val cartRef = db.collection("users").document(user.uid).collection("cart")

        // utilizamos una transaccion de Firestore para verificar el stock disponible antes de generar los tickets
        // inicialmente se realizo con un batch, pero al tener que realizar una lectura previa, es necesario usar transaction
        // esto evita condiciones de carrera donde dos usuarios compren las ultimas entradas simultaneamente
        db.runTransaction { transaction ->
            // primero realizar la lectura para verificar el stock disponible para cada partido del carrito
            val matchStocks = mutableMapOf<String, Long>()
            for (item in cartItems) {
                val matchDoc = transaction.get(db.collection("matches").document(item.matchId))
                val currentStock = matchDoc.getLong("stock") ?: 0L
                // verificar que haya suficientes entradas disponibles
                if (currentStock < item.quantity) {
                    throw FirebaseFirestoreException(
                        "Stock insuficiente para ${item.homeTeamName} vs ${item.awayTeamName}. Disponibles: $currentStock",
                        FirebaseFirestoreException.Code.ABORTED
                    )
                }
                matchStocks[item.matchId] = currentStock
            }
            // luego se procede con la escritura para crear tickets, vaciar carrito y actualizar stock
            for (item in cartItems) {
                // por cada item del carrito, creamos un ticket con un codigo QR unico
                val ticketId = UUID.randomUUID().toString()
                val qrCodigo = UUID.randomUUID().toString()
                val ticketData = hashMapOf(
                    "ticketId" to ticketId,
                    "userId" to user.uid,
                    "matchId" to item.matchId,
                    "homeTeamName" to item.homeTeamName,
                    "awayTeamName" to item.awayTeamName,
                    "homeBadgeUrl" to item.homeBadgeUrl,
                    "awayBadgeUrl" to item.awayBadgeUrl,
                    "stadium" to item.stadium,
                    "quantity" to item.quantity,
                    "totalPrice" to item.calculateTotal(),
                    "matchDate" to item.matchDate,
                    "purchaseDate" to Timestamp.now(),
                    "qrCodigo" to qrCodigo
                )
                // crear el ticket
                transaction.set(ticketsRef.document(ticketId), ticketData)
                // eliminar el item del carrito
                transaction.delete(cartRef.document(item.matchId))
                // actualizar el stock del partido restando la cantidad comprada
                val matchRef = db.collection("matches").document(item.matchId)
                val newStock = matchStocks[item.matchId]!! - item.quantity
                transaction.update(matchRef, "stock", newStock)
            }
        }
        .addOnSuccessListener {
            // la transaccion se ha realizado sin errores, se han generado los tickets y el carrito se ha vaciado
            Toast.makeText(this, "¡Pago realizado con éxito! Entradas generadas.", Toast.LENGTH_LONG).show()
            // navegar a MainActivity y abrir el fragment de Mis Entradas
            // enviamos en el intent el nombre del fragment a abrir
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "tickets")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
        .addOnFailureListener { e ->
            // la transaccion ha dado error
            Log.e(TAG, "Error al generar tickets", e)
            // diferenciar entre error de stock insuficiente y otros errores para mostrar el mensaje correspondiente al usuario
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ABORTED) {
                Toast.makeText(this, e.message ?: "No hay suficientes entradas disponibles.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error al generar las entradas. Contacta con soporte.", Toast.LENGTH_LONG).show()
            }
            resetPayButton()
            binding.btnClearCart.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // desuscribirse del listener para evitar fugas de memoria
        cartListener?.remove()
    }
}
