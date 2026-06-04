package com.escobar.pasedegol.ui.ticket

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ActivityTicketDetailBinding
import net.glxn.qrgen.android.QRCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TicketDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTicketDetailBinding

    // constantes estaticas para las claves del intent
    companion object {
        const val EXTRA_HOME_TEAM = "home_team"
        const val EXTRA_AWAY_TEAM = "away_team"
        const val EXTRA_HOME_BADGE = "home_badge"
        const val EXTRA_AWAY_BADGE = "away_badge"
        const val EXTRA_STADIUM = "stadium"
        const val EXTRA_QUANTITY = "quantity"
        const val EXTRA_TOTAL_PRICE = "total_price"
        const val EXTRA_MATCH_DATE = "match_date"
        const val EXTRA_PURCHASE_DATE = "purchase_date"
        const val EXTRA_QR_CODIGO = "qr_codigo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar toolbar con boton de retroceso
        binding.toolbar.setNavigationOnClickListener { finish() }

        // cargar los datos del ticket desde el intent
        setupUI()
    }

    private fun setupUI() {
        val locale = Locale.forLanguageTag("es-ES")

        // datos recibidos del intent
        val homeTeamName = intent.getStringExtra(EXTRA_HOME_TEAM) ?: ""
        val awayTeamName = intent.getStringExtra(EXTRA_AWAY_TEAM) ?: ""
        val homeBadgeUrl = intent.getStringExtra(EXTRA_HOME_BADGE) ?: ""
        val awayBadgeUrl = intent.getStringExtra(EXTRA_AWAY_BADGE) ?: ""
        val stadium = intent.getStringExtra(EXTRA_STADIUM) ?: ""
        val quantity = intent.getIntExtra(EXTRA_QUANTITY, 0)
        val totalPrice = intent.getDoubleExtra(EXTRA_TOTAL_PRICE, 0.0)
        val matchDate = intent.getLongExtra(EXTRA_MATCH_DATE, 0L)
        val purchaseDateMillis = intent.getLongExtra(EXTRA_PURCHASE_DATE, 0L)
        val qrCodigo = intent.getStringExtra(EXTRA_QR_CODIGO) ?: ""

        // fecha y hora del partido
        val date = Date(matchDate)
        binding.tvDate.text = SimpleDateFormat("dd 'de' MMMM", locale).format(date)
        binding.tvTime.text = SimpleDateFormat("HH:mm", locale).format(date)

        // nombres de los equipos
        binding.tvHomeTeam.text = homeTeamName
        binding.tvAwayTeam.text = awayTeamName

        // estadio
        binding.tvStadium.text = stadium

        // cantidad de entradas
        val entrada = if (quantity > 1) "Entradas" else "Entrada"
        binding.tvQuantity.text = "$quantity $entrada"

        // precio total
        binding.tvTotalPrice.text = String.format(locale, "%.2f €", totalPrice)

        // fecha de compra
        val purchaseDate = Date(purchaseDateMillis)
        binding.tvPurchaseDate.text = "Comprado el ${SimpleDateFormat("dd MMM yyyy", locale).format(purchaseDate)}"

        // cargar escudos con Glide
        Glide.with(this)
            .load(homeBadgeUrl) // equipo local
            .placeholder(R.drawable.ic_catalog)
            .error(R.drawable.ic_catalog)
            .circleCrop()
            .into(binding.ivHomeBadge)

        Glide.with(this)
            .load(awayBadgeUrl) // equipo visitante
            .placeholder(R.drawable.ic_catalog)
            .error(R.drawable.ic_catalog)
            .circleCrop()
            .into(binding.ivAwayBadge)

        // generar el codigo QR a partir del string unico del ticket
        if (qrCodigo.isNotEmpty()) {
            val qrBitmap = QRCode.from(qrCodigo).withSize(500, 500).bitmap()
            binding.ivQrCode.setImageBitmap(qrBitmap)
        }
    }
}
