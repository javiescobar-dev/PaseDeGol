package com.escobar.pasedegol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ItemCartBinding
import com.escobar.pasedegol.model.CartItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CartAdapter(
    private val onDeleteClick: (CartItem) -> Unit
) : ListAdapter<CartItem, CartAdapter.CartViewHolder>(CartDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartViewHolder(
        private val binding: ItemCartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val locale = Locale.forLanguageTag("es-ES")

        fun bind(cartItem: CartItem) {
            // fecha y hora del partido
            val date = Date(cartItem.matchDate)
            binding.tvMatchDate.text = SimpleDateFormat("dd MMM", locale).format(date)
            binding.tvMatchTime.text = SimpleDateFormat("HH:mm", locale).format(date)

            // nombres de los equipos
            binding.tvHomeTeam.text = cartItem.homeTeamName
            binding.tvAwayTeam.text = cartItem.awayTeamName

            // info: cantidad de entradas y estadio
            binding.tvCartInfo.text = "${cartItem.quantity} Entradas - ${cartItem.stadium}"

            // total del item
            binding.tvCartTotal.text = String.format(locale, "%.2f €", cartItem.calculateTotal())

            // cargar escudos con Glide en formato circular
            Glide.with(binding.ivHomeBadge.context) // equipo local
                .load(cartItem.homeBadgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivHomeBadge)

            Glide.with(binding.ivAwayBadge.context) // equipo visitante
                .load(cartItem.awayBadgeUrl)
                .placeholder(R.drawable.ic_catalog)
                .error(R.drawable.ic_catalog)
                .circleCrop()
                .into(binding.ivAwayBadge)

            // boton de eliminar el item del carrito
            binding.btnDelete.setOnClickListener {
                onDeleteClick(cartItem)
            }
        }
    }

    // utilizamos DiffUtil para comprobar si hay cambios en la lista de items del carrito
    // actualizando el RecyclerView de forma eficiente, solo con los items que han cambiado
    // evitando tener que actualizar toda la lista
    class CartDiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem.matchId == newItem.matchId
        }

        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem == newItem
        }
    }
}
