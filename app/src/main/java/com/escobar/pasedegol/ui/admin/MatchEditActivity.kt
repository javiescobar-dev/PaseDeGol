package com.escobar.pasedegol.ui.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ActivityMatchEditBinding
import com.escobar.pasedegol.model.Team
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MatchEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchEditBinding

    // instancia de Firestore
    private val db = FirebaseFirestore.getInstance()

    // lista de equipos cargados desde Firestore
    private val teamsList = mutableListOf<Team>()

    // calendario para la fecha seleccionada
    private val selectedDate = Calendar.getInstance()
    private var dateSelected = false

    // formato de fecha para mostrar en el campo
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))

    // ID del partido, si es nulo, estamos en modo creacion, sino, estamos en modo edicion
    private var matchId: String? = null

    companion object {
        private const val TAG = "MatchEditActivity"
        const val EXTRA_MATCH_ID = "extra_match_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // comprobar si estamos en modo edicion
        matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        if (matchId != null) {
            binding.toolbar.title = "Editar Partido"
            binding.btnPublish.text = "ACTUALIZAR CAMBIOS"
        }

        // configurar el campo de fecha para abrir DatePicker y TimePicker
        binding.etDate.setOnClickListener { showDateTimePicker() }

        // configurar boton para crear/guardar partido
        binding.btnPublish.setOnClickListener { saveMatch() }

        // cargar la lista de equipos para los spinners (listas desplegables)
        loadTeams()
    }

    // metodo para cargar equipos desde Firestore para las listas desplegables
    private fun loadTeams() {
        // mostrar estado de carga inicialmente
        binding.progressBar.visibility = View.VISIBLE

        // cargar equipos desde Firestore
        db.collection("teams").orderBy("name").get()
            .addOnSuccessListener { snapshots ->
                // limpiar la lista y proceder a cargar los equipos
                teamsList.clear()

                // recorremos los documentos, los convertimos a objetos Team y los añadimos a la lista
                for (doc in snapshots.documents) {
                    val team = doc.toObject(Team::class.java)?.copy(id = doc.id)
                    if (team != null) {
                        teamsList.add(team)
                    }
                }

                // validar que haya al menos 2 equipos, de lo contrario, no permitir crear un partido
                if (teamsList.size < 2) {
                    // precondicion no cumplida: necesitamos al menos 2 equipos
                    Toast.makeText(this, "Se necesitan al menos 2 equipos para crear un partido. Crea los equipos primero.", Toast.LENGTH_LONG).show()
                    binding.btnPublish.isEnabled = false
                    binding.progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                // configurar los spinners con la lista de equipos
                setupSpinners()

                // ocultar estado de carga y mostrar el contenido
                binding.progressBar.visibility = View.GONE

                // cargar los datos del partido en caso de ser en modo edicion
                if (matchId != null) {
                    loadMatchData(matchId!!)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar equipos", e)
                Toast.makeText(this, "Error al cargar los equipos", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    // metodo para configurar los spinners con un adapter personalizado que muestra escudo + nombre
    private fun setupSpinners() {
        val adapter = object : ArrayAdapter<Team>(this, android.R.layout.simple_spinner_item, teamsList) {
            // metodo sobrescrito para mostrar el escudo y nombre en el spinner
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createTeamView(position)
            }

            // metodo sobrescrito para mostrar el escudo y nombre en el spinner desplegable
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createTeamView(position)
            }

            // metodo para crear la vista de cada equipo en el spinner
            private fun createTeamView(position: Int): View {
                // obtener el equipo en la posicion actual
                val team = teamsList[position]

                // crear un LinearLayout para mostrar el escudo y el nombre
                val layout = LinearLayout(this@MatchEditActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 12, 16, 12)
                }

                // crear un ImageView para mostrar el escudo
                val imageView = ImageView(this@MatchEditActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                        marginEnd = 16
                    }
                }

                // cargar escudo con Glide
                Glide.with(this@MatchEditActivity)
                    .load(team.badgeUrl)
                    .placeholder(R.drawable.ic_teams)
                    .error(R.drawable.ic_teams)
                    .circleCrop()
                    .into(imageView)

                // crear un TextView para mostrar el nombre
                val textView = TextView(this@MatchEditActivity).apply {
                    text = team.name
                    textSize = 16f

                    // cambiamos el color del texto segun el tema
                    val typedValue = android.util.TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                    setTextColor(typedValue.data)
                }

                // añadir los elementos al LinearLayout
                layout.addView(imageView)
                layout.addView(textView)

                return layout
            }
        }

        // configurar adaptadores para los spinners
        binding.spinnerHomeTeam.adapter = adapter
        binding.spinnerAwayTeam.adapter = adapter

        // seleccionar equipo visitante por defecto al segundo equipo (si hay mas de uno)
        if (teamsList.size > 1) {
            binding.spinnerAwayTeam.setSelection(1)
        }
    }

    // metodo para mostrar la seleccion de fecha y hora
    private fun showDateTimePicker() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        // primero mostrar DatePicker
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedDate.set(Calendar.YEAR, selectedYear)
            selectedDate.set(Calendar.MONTH, selectedMonth)
            selectedDate.set(Calendar.DAY_OF_MONTH, selectedDay)

            // una vez seleccionada la fecha, mostrar TimePicker
            val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
            val minute = selectedDate.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedDate.set(Calendar.MINUTE, selectedMinute)
                selectedDate.set(Calendar.SECOND, 0)
                dateSelected = true

                // mostrar la fecha formateada en el campo
                binding.etDate.setText(dateFormat.format(selectedDate.time))
            }, hour, minute, true).show()

        }, year, month, day).show()
    }

    // metodo para cargar datos del partido desde Firestore (modo edicion)
    private fun loadMatchData(id: String) {
        db.collection("matches").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // obtener datos del documento
                    val homeTeamMap = doc.get("homeTeam") as? Map<*, *>
                    val awayTeamMap = doc.get("awayTeam") as? Map<*, *>
                    val homeTeamName = homeTeamMap?.get("name") as? String ?: ""
                    val awayTeamName = awayTeamMap?.get("name") as? String ?: ""
                    val stadium = doc.getString("stadium") ?: ""
                    val price = doc.getDouble("price") ?: 0.0
                    val stock = doc.getLong("stock")?.toInt() ?: 0
                    val date = doc.getTimestamp("date")

                    // establecer valores en los campos del formulario
                    binding.etStadium.setText(stadium)
                    binding.etPrice.setText(String.format(Locale.US, "%.2f", price))
                    binding.etStock.setText(stock.toString())

                    // establecer fecha
                    if (date != null) {
                        selectedDate.time = date.toDate()
                        dateSelected = true
                        binding.etDate.setText(dateFormat.format(selectedDate.time))
                    }

                    // seleccionar equipos en los spinners
                    val homeIndex = teamsList.indexOfFirst { it.name == homeTeamName }
                    val awayIndex = teamsList.indexOfFirst { it.name == awayTeamName }
                    if (homeIndex >= 0) binding.spinnerHomeTeam.setSelection(homeIndex)
                    if (awayIndex >= 0) binding.spinnerAwayTeam.setSelection(awayIndex)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar datos del partido", e)
                Toast.makeText(this, "Error al cargar el partido", Toast.LENGTH_SHORT).show()
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

    // metodo para validar y guardar el partido
    private fun saveMatch() {
        // obtener equipos seleccionados
        val homeTeamIndex = binding.spinnerHomeTeam.selectedItemPosition
        val awayTeamIndex = binding.spinnerAwayTeam.selectedItemPosition

        // validar que se hayan seleccionado ambos equipos
        if (homeTeamIndex < 0 || awayTeamIndex < 0) {
            Toast.makeText(this, "Selecciona ambos equipos", Toast.LENGTH_SHORT).show()
            return
        }

        // obtener objetos equipo en base a los equipos seleccionados
        val homeTeam = teamsList[homeTeamIndex]
        val awayTeam = teamsList[awayTeamIndex]

        // validar que no sean el mismo equipo
        if (homeTeam.id == awayTeam.id) {
            Toast.makeText(this, "El equipo local y visitante no pueden ser el mismo", Toast.LENGTH_SHORT).show()
            return
        }

        // validar fecha seleccionada
        if (!dateSelected) {
            Toast.makeText(this, "Selecciona la fecha y hora del partido", Toast.LENGTH_SHORT).show()
            return
        }

        // validar estadio
        val stadium = binding.etStadium.text.toString().trim()
        if (stadium.isEmpty()) {
            binding.etStadium.error = "El estadio es obligatorio"
            return
        }

        // validar precio
        val priceText = binding.etPrice.text.toString().trim()
        val price = priceText.toDoubleOrNull()
        if (price == null || price <= 0) {
            binding.etPrice.error = "Introduce un precio válido"
            return
        }

        // validar stock
        val stockText = binding.etStock.text.toString().trim()
        val stock = stockText.toIntOrNull()
        if (stock == null || stock <= 0) {
            binding.etStock.error = "Introduce un stock válido"
            return
        }

        // comprobar conexion a internet y mostrar mensaje informativo si no la hay, ya que
        // firestore encola la operacion y la sincroniza automaticamente cuando se recupera la conexion
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Sin conexión a Internet. Los cambios se guardarán automáticamente cuando se recupere la conexión.", Toast.LENGTH_SHORT).show()
            return
        }

        // mostrar estado de carga (deshabilitar boton y mostrar progreso)
        binding.btnPublish.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // construir los datos del partido almacenando nombre y URL del escudo de cada equipo directamente
        // en un mapa, para almacenar los datos del partido en Firestore directamente y asi evitar consultas innecesarias posteriormente
        val matchData = hashMapOf(
            "homeTeam" to hashMapOf(
                "name" to homeTeam.name,
                "badgeUrl" to homeTeam.badgeUrl
            ),
            "awayTeam" to hashMapOf(
                "name" to awayTeam.name,
                "badgeUrl" to awayTeam.badgeUrl
            ),
            "stadium" to stadium,
            "date" to Timestamp(selectedDate.time),
            "price" to price,
            "stock" to stock
        )

        // comprobar el id del partido para saber si es edicion o creacion
        if (matchId != null) {
            // modo edicion: actualizar documento existente
            db.collection("matches").document(matchId!!).set(matchData)
                .addOnSuccessListener {
                    // mostrar mensaje de exito y volver a la actividad anterior
                    Toast.makeText(this, "Partido actualizado correctamente", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    // mostrar mensaje de error y volver a habilitar el boton y ocultar el progreso
                    Log.e(TAG, "Error al actualizar partido", e)
                    Toast.makeText(this, "Error al actualizar el partido", Toast.LENGTH_SHORT).show()
                    binding.btnPublish.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
        } else {
            // modo creacion: insertar nuevo documento
            db.collection("matches").add(matchData)
                .addOnSuccessListener {
                    // mostrar mensaje de exito y volver a la actividad anterior
                    Toast.makeText(this, "Partido publicado correctamente", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    // mostrar mensaje de error y volver a habilitar el boton y ocultar el progreso
                    Log.e(TAG, "Error al crear partido", e)
                    Toast.makeText(this, "Error al crear el partido", Toast.LENGTH_SHORT).show()
                    binding.btnPublish.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
        }
    }
}
