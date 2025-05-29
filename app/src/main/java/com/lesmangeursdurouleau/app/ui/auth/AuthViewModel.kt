package com.lesmangeursdurouleau.app.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.repository.UserRepository // Chemin vers l'interface
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel // IMPORT AJOUTÉ
import kotlinx.coroutines.launch
import javax.inject.Inject

// AuthResultWrapper reste inchangé
sealed class AuthResultWrapper {
    data class Success(val user: FirebaseUser) : AuthResultWrapper()
    data class Error(val exception: Exception) : AuthResultWrapper()
    object Loading : AuthResultWrapper()
}

@HiltViewModel // ANNOTATION AJOUTÉE
class AuthViewModel @Inject constructor(
    application: Application, // Reste car c'est un AndroidViewModel
    private val userRepository: UserRepository, // Sera injecté par Hilt
    // Ajout des dépendances Firebase si elles ne sont pas déjà dans userRepository
    private val firebaseAuthInstance: FirebaseAuth, // Nom différent pour éviter confusion avec le firebaseAuth local
    private val firestoreInstance: FirebaseFirestore  // Nom différent
) : AndroidViewModel(application) {

    // Tu peux maintenant supprimer ces initialisations directes si Hilt les fournit via le constructeur
    // private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    // private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    // Utilise plutôt firebaseAuthInstance et firestoreInstance passés par Hilt

    private val _registrationResult = MutableLiveData<AuthResultWrapper?>()
    val registrationResult: MutableLiveData<AuthResultWrapper?> = _registrationResult

    private val _loginResult = MutableLiveData<AuthResultWrapper?>()
    val loginResult: MutableLiveData<AuthResultWrapper?> = _loginResult

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _justRegistered = MutableLiveData<Boolean>(false)
    val justRegistered: LiveData<Boolean> = _justRegistered

    private val _userDisplayName = MutableLiveData<String?>()
    val userDisplayName: LiveData<String?> = _userDisplayName

    private val _profileUpdateResult = MutableLiveData<Resource<Unit>?>()
    val profileUpdateResult: LiveData<Resource<Unit>?> = _profileUpdateResult

    private val _profilePictureUpdateResult = MutableLiveData<Resource<String>?>()
    val profilePictureUpdateResult: LiveData<Resource<String>?> = _profilePictureUpdateResult

    init {
        _currentUser.value = firebaseAuthInstance.currentUser // Utilise l'instance injectée
        _currentUser.value?.uid?.let { fetchUserDisplayName(it) }
    }

    fun registerUser(email: String, password: String, username: String) {
        _registrationResult.value = AuthResultWrapper.Loading
        _justRegistered.value = false
        firebaseAuthInstance.createUserWithEmailAndPassword(email, password) // Utilise l'instance injectée
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.user != null) {
                    val firebaseUser = task.result!!.user!!
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)
                        .addOnCompleteListener { profileUpdateTask ->
                            if (profileUpdateTask.isSuccessful) {
                                Log.d("AuthViewModel", "Firebase Auth displayName updated.")
                            } else {
                                Log.w("AuthViewModel", "Failed to update Firebase Auth displayName.", profileUpdateTask.exception)
                            }
                        }

                    val userDocument = hashMapOf(
                        "uid" to firebaseUser.uid,
                        "username" to username,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    firestoreInstance.collection("users").document(firebaseUser.uid) // Utilise l'instance injectée
                        .set(userDocument)
                        .addOnSuccessListener {
                            Log.d("AuthViewModel", "User profile created in Firestore for ${firebaseUser.uid}")
                            _registrationResult.value = AuthResultWrapper.Success(firebaseUser)
                            _currentUser.value = firebaseUser
                            _userDisplayName.value = username
                            _justRegistered.value = true
                        }
                        .addOnFailureListener { e ->
                            Log.w("AuthViewModel", "Error creating user profile in Firestore", e)
                            // Même en cas d'échec de Firestore, l'utilisateur est créé dans Auth.
                            // On le signale comme succès pour Auth, le problème Firestore est loggué.
                            _registrationResult.value = AuthResultWrapper.Success(firebaseUser)
                            _currentUser.value = firebaseUser
                            _userDisplayName.value = username
                            _justRegistered.value = true
                        }
                } else {
                    _registrationResult.value = AuthResultWrapper.Error(task.exception ?: Exception("Erreur d'inscription inconnue"))
                }
            }
    }

    fun loginUser(email: String, password: String) {
        _loginResult.value = AuthResultWrapper.Loading
        _justRegistered.value = false
        firebaseAuthInstance.signInWithEmailAndPassword(email, password) // Utilise l'instance injectée
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result?.user != null) {
                    val firebaseUser = task.result!!.user!!
                    _loginResult.value = AuthResultWrapper.Success(firebaseUser)
                    _currentUser.value = firebaseUser
                    fetchUserDisplayName(firebaseUser.uid)
                } else {
                    _loginResult.value = AuthResultWrapper.Error(task.exception ?: Exception("Erreur de connexion inconnue"))
                }
            }
    }

    fun fetchUserDisplayName(userId: String) {
        firestoreInstance.collection("users").document(userId) // Utilise l'instance injectée
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val usernameFromDb = document.getString("username")
                    _userDisplayName.value = usernameFromDb
                    Log.d("AuthViewModel", "Username fetched from Firestore: $usernameFromDb")
                } else {
                    Log.d("AuthViewModel", "No such user document in Firestore for $userId, trying Firebase Auth displayName")
                    _userDisplayName.value = firebaseAuthInstance.currentUser?.displayName // Utilise l'instance injectée
                }
            }
            .addOnFailureListener { exception ->
                Log.w("AuthViewModel", "Error fetching username from Firestore for $userId", exception)
                _userDisplayName.value = firebaseAuthInstance.currentUser?.displayName // Utilise l'instance injectée
            }
    }

    fun updateUserProfile(userId: String, username: String) {
        _profileUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserProfile(userId, username)
            _profileUpdateResult.value = result
            if (result is Resource.Success) {
                _userDisplayName.value = username // Mettre à jour le display name localement
            }
        }
    }

    fun updateProfilePicture(userId: String, imageData: ByteArray) {
        _profilePictureUpdateResult.value = Resource.Loading()
        viewModelScope.launch {
            val result = userRepository.updateUserProfilePicture(userId, imageData)
            _profilePictureUpdateResult.value = result
            // Si succès, l'observer dans ProfileFragment s'occupera de rafraîchir Glide avec la nouvelle URL
        }
    }

    fun logoutUser() {
        firebaseAuthInstance.signOut() // Utilise l'instance injectée
        _currentUser.value = null
        _userDisplayName.value = null
        _justRegistered.value = false
        // Réinitialiser les résultats pour éviter qu'ils ne soient redéclenchés
        _loginResult.value = null // Optionnel: ou une valeur neutre si nécessaire
        _registrationResult.value = null // Optionnel
        _profileUpdateResult.value = null
        _profilePictureUpdateResult.value = null
    }

    fun consumeJustRegisteredEvent() {
        _justRegistered.value = false
    }
}