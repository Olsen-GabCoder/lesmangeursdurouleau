package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val savedStateHandle: SavedStateHandle // Pour récupérer les arguments de navigation
) : ViewModel() {

    private val _userProfile = MutableLiveData<Resource<User>>()
    val userProfile: LiveData<Resource<User>> = _userProfile

    // Récupérer l'userId des arguments de navigation
    // La clé "userId" doit correspondre au nom de l'argument défini dans main_nav_graph.xml
    private val userId: String? = savedStateHandle.get<String>("userId")

    init {
        if (!userId.isNullOrBlank()) {
            fetchUserProfile(userId)
        } else {
            Log.e("PublicProfileViewModel", "User ID est null ou vide dans SavedStateHandle.")
            _userProfile.value = Resource.Error("Impossible de charger le profil : ID utilisateur manquant.")
        }
    }

    private fun fetchUserProfile(id: String) {
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            userRepository.getUserById(id)
                .catch { e ->
                    Log.e("PublicProfileViewModel", "Exception lors de la récupération du profil pour $id", e)
                    _userProfile.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _userProfile.value = resource
                    when (resource) {
                        is Resource.Success -> Log.d("PublicProfileViewModel", "Profil chargé pour $id: ${resource.data?.username}")
                        is Resource.Error -> Log.e("PublicProfileViewModel", "Erreur chargement profil $id: ${resource.message}")
                        is Resource.Loading -> Log.d("PublicProfileViewModel", "Chargement du profil pour $id...")
                    }
                }
        }
    }

    // Si tu veux permettre un rafraîchissement manuel plus tard
    fun refreshProfile() {
        if (!userId.isNullOrBlank()) {
            fetchUserProfile(userId)
        }
    }
}