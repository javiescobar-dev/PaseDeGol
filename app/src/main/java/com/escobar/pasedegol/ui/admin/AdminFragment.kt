package com.escobar.pasedegol.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.escobar.pasedegol.databinding.FragmentAdminBinding
import com.google.android.material.transition.MaterialFadeThrough

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // configuramos los botones para navegar a la gestion de equipos y partidos
        // gestion de equipos
        binding.cardTeams.setOnClickListener {
            val intent = Intent(requireContext(), ManageTeamsActivity::class.java)
            startActivity(intent)
        }

        // gestion de partidos
        binding.cardMatches.setOnClickListener {
            val intent = Intent(requireContext(), ManageMatchesActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
