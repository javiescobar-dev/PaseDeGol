package com.escobar.pasedegol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ItemAdminMatchBinding
import com.escobar.pasedegol.model.Match
import java.text.SimpleDateFormat
import java.util.Locale

class AdminMatchAdapter(
    private val onEditClick: (Match) -> Unit,
    private val onDeleteClick: (Match) -> Unit
) : ListAdapter<Match, AdminMatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemAdminMatchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MatchViewHolder(
        private val binding: ItemAdminMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // formato de fecha en español
        private val locale = Locale.forLanguageTag("es-ES")
        private val dateFormat = SimpleDateFormat("dd MMM", locale)
        private val timeFormat = SimpleDateFormat("HH:mm", locale)

        fun bind(match: Match) {
            // convertir timestamp que obtenemos de firestore a date
            val date = match.date.toDate()

            // fecha: dia, mes abreviado y hora
            binding.tvMatchDate.text = dateFormat.format(date)
            binding.tvMatchTime.text = timeFormat.format(date)

            // equipos
            binding.tvHomeTeam.text = match.homeTeam.name
            binding.tvAwayTeam.text = match.awayTeam.name

            // estadio
            binding.tvStadium.text = match.stadium

            // info: precio, stock
            val price = String.format(Locale.forLanguageTag("es-ES"), "%.2f €", match.price)
            binding.tvMatchInfo.text = "Precio por Entrada: $price · Stock: ${match.stock}"

            // cargar escudos con Glide
            Glide.with(binding.ivHomeBadge.context)
                .load(match.homeTeam.badgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivHomeBadge)

            Glide.with(binding.ivAwayBadge.context)
                .load(match.awayTeam.badgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivAwayBadge)

            // configurar botones de accion
            binding.btnEdit.setOnClickListener { onEditClick(match) }
            binding.btnDelete.setOnClickListener { onDeleteClick(match) }
        }
    }

    // DiffUtil para actualizar solo los items que han cambiado
    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem == newItem
        }
    }
}
