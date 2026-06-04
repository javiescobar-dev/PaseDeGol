package com.escobar.pasedegol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ItemMatchBinding
import com.escobar.pasedegol.model.Match
import java.text.SimpleDateFormat
import java.util.Locale

class MatchAdapter(
    private val onMatchClick: (Match) -> Unit
) : ListAdapter<Match, MatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MatchViewHolder(
        private val binding: ItemMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // formateamos la fecha para mostrarla en español
        private val locale = Locale.forLanguageTag("es-ES")
        private val dateFormat = SimpleDateFormat("dd MMM", locale)
        private val timeFormat = SimpleDateFormat("HH:mm", locale)

        fun bind(match: Match) {
            // convertir timestamp que obtenemos de firestore a date
            val date = match.date.toDate()

            // Fecha: dia, mes abreviado y hora
            binding.tvMatchDate.text = dateFormat.format(date)
            binding.tvMatchTime.text = timeFormat.format(date)

            // Equipos
            binding.tvHomeTeam.text = match.homeTeam.name
            binding.tvAwayTeam.text = match.awayTeam.name

            // Estadio
            binding.tvStadium.text = match.stadium

            // cargar los escudos de los equipos en formato circular con Glide
            // local
            Glide.with(binding.ivHomeBadge.context)
                .load(match.homeTeam.badgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivHomeBadge)
            // visitante
            Glide.with(binding.ivAwayBadge.context)
                .load(match.awayTeam.badgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivAwayBadge)

            // configurar clic en la tarjeta para ir al detalle del partido
            binding.root.setOnClickListener {
                onMatchClick(match)
            }
        }
    }

    // utilizamos DiffUtil para comprobar si hay cambios en la lista de partidos
    // actualizando el RecyclerView de forma eficiente, solo con los items que han cambiado
    // evitando tener que actualizar toda la lista
    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem == newItem
        }
    }
}
