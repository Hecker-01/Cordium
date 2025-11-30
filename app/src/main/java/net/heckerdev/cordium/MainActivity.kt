package net.heckerdev.cordium

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Material You dynamic colors
        DynamicColors.applyToActivityIfAvailable(this)
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up bottom navigation with NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        
        // Handle reselection of profile tab to navigate to settings
        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.navigation_profile) {
                if (navController.currentDestination?.id == R.id.navigation_profile) {
                    navController.navigate(R.id.action_profile_to_settings)
                }
            }
        }
        
        // Hide bottom navigation on settings page
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.settingsFragment) {
                bottomNav.visibility = View.GONE
            } else {
                bottomNav.visibility = View.VISIBLE
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            // Apply bottom padding to bottom navigation
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            
            insets
        }
    }
}