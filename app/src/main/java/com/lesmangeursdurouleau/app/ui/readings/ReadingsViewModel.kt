package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log // AJOUT pour le Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.repository.BookRepository // Interface
import com.lesmangeursdurouleau.app.repository.BookRepositoryImpl // Implémentation
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.catch // AJOUT pour la gestion d'erreur dans le Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReadingsViewModel : ViewModel() {

    private val _books = MutableLiveData<List<Book>>()
    val books: LiveData<List<Book>> = _books

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Pour l'instant, instanciation directe. Plus tard -> Injection de Dépendances (Hilt)
    private val bookRepository: BookRepository = BookRepositoryImpl()
    private val getBooksUseCase: GetBooksUseCase = GetBooksUseCase(bookRepository)

    init {
        loadBooksFromFirestore()
    }

    private fun loadBooksFromFirestore() {
        viewModelScope.launch {
            getBooksUseCase() // Exécute le use case qui retourne un Flow
                .catch { e -> // Intercepte les exceptions non gérées dans le Flow lui-même
                    Log.e("ReadingsViewModel", "Exception in flow collection", e)
                    _isLoading.value = false
                    _error.value = "Une erreur technique est survenue: ${e.localizedMessage}"
                    _books.value = emptyList()
                }
                .collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            _isLoading.value = true
                            _error.value = null
                            Log.d("ReadingsViewModel", "Loading books...")
                        }
                        is Resource.Success -> {
                            _isLoading.value = false
                            _books.value = resource.data ?: emptyList()
                            _error.value = null
                            Log.d("ReadingsViewModel", "Books loaded successfully: ${resource.data?.size ?: 0} items")
                            if (resource.data.isNullOrEmpty() && _isLoading.value == false) { // Vérifier isLoading pour éviter faux positifs
                                Log.d("ReadingsViewModel", "No books found in Firestore or list is empty.")
                                // Le fragment gère déjà l'affichage "Pas de lectures disponibles"
                            }
                        }
                        is Resource.Error -> {
                            _isLoading.value = false
                            _error.value = resource.message ?: "Une erreur inconnue est survenue."
                            _books.value = emptyList()
                            Log.e("ReadingsViewModel", "Error loading books: ${resource.message}")
                        }
                    }
                }
        }
    }

    // L'ancienne méthode loadBooks() avec les données factices est maintenant obsolète
    // Tu peux la supprimer complètement.
}