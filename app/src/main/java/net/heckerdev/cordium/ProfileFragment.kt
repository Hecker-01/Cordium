package net.heckerdev.cordium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        // Navigate to settings when clicked
        view.findViewById<View>(R.id.settingsItem).setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }
        
        return view
    }
}
