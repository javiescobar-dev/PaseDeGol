package com.escobar.pasedegol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ItemTicketBinding
import com.escobar.pasedegol.model.Ticket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TicketAdapter(
    private val onTicketClick: (Ticket) -> Unit
) : ListAdapter<Ticket, TicketAdapter.TicketViewHolder>(TicketDiffCallback()) {

    // locale en español para formatear fechas y precios
    private val locale = Locale.forLanguageTag("es-ES")

    inner class TicketViewHolder(private val binding: ItemTicketBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ticket: Ticket) {
            // fecha y hora del partido
            val matchDate = Date(ticket.matchDate)
            binding.tvMatchDate.text = SimpleDateFormat("dd MMM", locale).format(matchDate)
            binding.tvMatchTime.text = SimpleDateFormat("HH:mm", locale).format(matchDate)

            // nombres de los equipos
            binding.tvHomeTeam.text = ticket.homeTeamName
            binding.tvAwayTeam.text = ticket.awayTeamName

            // info: cantidad de entradas y estadio
            val entrada = if (ticket.quantity > 1) "Entradas" else "Entrada"
            binding.tvTicketInfo.text = "${ticket.quantity} ${entrada} - ${ticket.stadium}"

            // precio total pagado
            binding.tvTotalPrice.text = String.format(locale, "%.2f €", ticket.totalPrice)

            // fecha de compra
            val purchaseDate = ticket.purchaseDate.toDate()
            binding.tvPurchaseDate.text = "Comprado el ${SimpleDateFormat("dd MMM yyyy", locale).format(purchaseDate)}"

            // escudo equipo local con Glide en formato circular
            Glide.with(binding.root.context)
                .load(ticket.homeBadgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivHomeBadge)

            // escudo equipo visitante con Glide en formato circular
            Glide.with(binding.root.context)
                .load(ticket.awayBadgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivAwayBadge)

            // al pulsar el cardview de la entrada, ir al detalle para poder ver el QR generado
            binding.root.setOnClickListener { onTicketClick(ticket) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val binding = ItemTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TicketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // utilizamos DiffUtil para comprobar si hay cambios en la lista de tickets
    // actualizando el RecyclerView de forma eficiente, solo con los items que han cambiado
    // evitando tener que actualizar toda la lista
    class TicketDiffCallback : DiffUtil.ItemCallback<Ticket>() {
        override fun areItemsTheSame(oldItem: Ticket, newItem: Ticket): Boolean =
            oldItem.ticketId == newItem.ticketId

        override fun areContentsTheSame(oldItem: Ticket, newItem: Ticket): Boolean =
            oldItem == newItem
    }
}
