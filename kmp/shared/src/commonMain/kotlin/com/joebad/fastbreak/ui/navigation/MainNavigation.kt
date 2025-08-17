package com.joebad.fastbreak.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import AuthRepository
import com.joebad.fastbreak.data.cache.FastbreakCache
import com.joebad.fastbreak.data.global.AppDataState
import com.joebad.fastbreak.ui.theme.LocalColors

enum class NavigationTab {
    HOME, PROFILE
}

@Composable
fun MainNavigationScreen(appDataState: AppDataState, onLogout: () -> Unit = {}, authRepository: AuthRepository, fastbreakCache: FastbreakCache) {
    val colors = LocalColors.current
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Main content
        when (selectedTab) {
            NavigationTab.HOME -> com.joebad.fastbreak.ui.screens.HomeScreen(appDataState = appDataState, onLogout = {}, authRepository = authRepository, fastbreakCache = fastbreakCache)
            NavigationTab.PROFILE -> com.joebad.fastbreak.ui.screens.ProfileScreen(appDataState = appDataState, onLogout = onLogout)
        }
        
        // Bottom navigation
        BottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

@Composable
private fun BottomNavigation(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    val colors = LocalColors.current
    
    Column {
        Divider(
            color = colors.onSurface.copy(alpha = 0.2f),
            thickness = 0.5.dp
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomNavItem(
                text = "HOME",
                isSelected = selectedTab == NavigationTab.HOME,
                onClick = { onTabSelected(NavigationTab.HOME) }
            )
            
            BottomNavItem(
                text = "PROFILE", 
                isSelected = selectedTab == NavigationTab.PROFILE,
                onClick = { onTabSelected(NavigationTab.PROFILE) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalColors.current
    
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = if (isSelected) colors.accent else colors.onSurface.copy(alpha = 0.6f),
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}