package com.escobar.pasedegol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.escobar.pasedegol.R
import com.escobar.pasedegol.databinding.ItemAdminTeamBinding
import com.escobar.pasedegol.model.Team

class AdminTeamAdapter(
    private val onEditClick: (Team) -> Unit,
    private val onDeleteClick: (Team) -> Unit
) : ListAdapter<Team, AdminTeamAdapter.TeamViewHolder>(TeamDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val binding = ItemAdminTeamBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TeamViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TeamViewHolder(
        private val binding: ItemAdminTeamBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(team: Team) {
            binding.tvTeamName.text = team.name

            // cargar el escudo del equipo con Glide en formato circular
            Glide.with(binding.ivTeamBadge.context)
                .load(team.badgeUrl)
                .placeholder(R.drawable.ic_teams)
                .error(R.drawable.ic_teams)
                .circleCrop()
                .into(binding.ivTeamBadge)

            // configurar botones de accion
            binding.btnEdit.setOnClickListener { onEditClick(team) }
            binding.btnDelete.setOnClickListener { onDeleteClick(team) }
        }
    }

    // DiffUtil para actualizar solo los items que han cambiado
    class TeamDiffCallback : DiffUtil.ItemCallback<Team>() {
        override fun areItemsTheSame(oldItem: Team, newItem: Team): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Team, newItem: Team): Boolean {
            return oldItem == newItem
        }
    }
}
