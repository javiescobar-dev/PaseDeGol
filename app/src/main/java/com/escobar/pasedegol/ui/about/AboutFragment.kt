package com.escobar.pasedegol.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.escobar.pasedegol.BuildConfig
import com.escobar.pasedegol.databinding.FragmentAboutBinding
import com.google.android.material.transition.MaterialFadeThrough
import java.util.Calendar

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
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
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // mostramos la version de la aplicacion obtenida del BuildConfig
        binding.tvVersion.text = "Versión ${BuildConfig.VERSION_NAME}"

        // mostramos el copyright con el año actual
        val year = Calendar.getInstance().get(Calendar.YEAR)
        binding.tvCopyright.text = "© $year PaseDeGol. Todos los derechos reservados."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
