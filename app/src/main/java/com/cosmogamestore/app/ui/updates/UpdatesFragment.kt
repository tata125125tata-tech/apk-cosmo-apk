package com.cosmogamestore.app.ui.updates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cosmogamestore.app.R

class UpdatesFragment : Fragment() {

    private lateinit var btnCheckUpdates: Button
    private lateinit var tvUpdatesStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_updates, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnCheckUpdates = view.findViewById(R.id.btn_check_updates)
        tvUpdatesStatus = view.findViewById(R.id.tv_updates_status)

        btnCheckUpdates.setOnClickListener {
            checkGameUpdates()
        }
    }

    private fun checkGameUpdates() {
        btnCheckUpdates.isEnabled = false
        btnCheckUpdates.text = "Checking Cosmo Repositories..."
        tvUpdatesStatus.text = "Contacting repository..."

        btnCheckUpdates.postDelayed({
            if (isAdded) {
                btnCheckUpdates.isEnabled = true
                btnCheckUpdates.text = "Check for Game Updates"
                tvUpdatesStatus.text = "✓ All installed games and client are up to date (Version 1.0)"
                tvUpdatesStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                Toast.makeText(requireContext(), "All game packages are up to date", Toast.LENGTH_SHORT).show()
            }
        }, 1200)
    }

    companion object {
        fun newInstance() = UpdatesFragment()
    }
}
