package com.joebad.fastbreak.data.profile

import AuthRepository
import AuthedUser
import GoogleUser
import ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class ProfileState(
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class ProfileSideEffect {
    object NavigateToHome : ProfileSideEffect()
}

sealed class ProfileAction {
    data class InitializeProfile(val googleUser: GoogleUser) : ProfileAction()
    object ClearError : ProfileAction()
}

class ProfileViewModel(
    private val authRepository: AuthRepository
) : ContainerHost<ProfileState, ProfileSideEffect> {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override val container: Container<ProfileState, ProfileSideEffect> = viewModelScope.container(ProfileState())

    fun handleAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.InitializeProfile -> initializeProfile(action.googleUser)
            ProfileAction.ClearError -> clearError()
        }
    }

    private fun initializeProfile(googleUser: GoogleUser) = intent {
        try {
            reduce {
                state.copy(
                    isLoading = true,
                    error = null
                )
            }

            val profileRepository = ProfileRepository(authRepository)
            val result = profileRepository.initializeProfile(googleUser)
            
            if (result != null) {
                // Create AuthedUser with the userId from the backend
                val authedUser = AuthedUser(
                    email = googleUser.email,
                    exp = googleUser.exp,
                    idToken = googleUser.idToken,
                    userId = result.response.userId
                )
                authRepository.storeAuthedUser(authedUser)
                
                reduce {
                    state.copy(
                        isLoading = false,
                        error = null
                    )
                }
                
                postSideEffect(ProfileSideEffect.NavigateToHome)
            } else {
                reduce {
                    state.copy(
                        isLoading = false,
                        error = "Failed to initialize user profile"
                    )
                }
            }
        } catch (e: Exception) {
            reduce {
                state.copy(
                    isLoading = false,
                    error = "Profile initialization error: ${e.message}"
                )
            }
        }
    }

    private fun clearError() = intent {
        reduce {
            state.copy(error = null)
        }
    }
}