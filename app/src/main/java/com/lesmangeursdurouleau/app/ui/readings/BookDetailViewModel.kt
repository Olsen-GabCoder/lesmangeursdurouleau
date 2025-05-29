package com.lesmangeursdurouleau.app.ui.readings.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.repository.BookRepositoryImpl // Implémentation pour instanciation directe
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookDetailViewModel : ViewModel() {

    private val _bookDetails = MutableLiveData<Resource<Book>>()
    val bookDetails: LiveData<Resource<Book>> = _bookDetails

    // Pour l'instant, instanciation directe. Plus tard -> Injection de Dépendances (Hilt)
    private val bookRepository = BookRepositoryImpl() // TODO: Injecter
    private val getBookByIdUseCase = GetBookByIdUseCase(bookRepository) // TODO: Injecter

    fun loadBookDetails(bookId: String) {
        if (bookId.isBlank()) {
            _bookDetails.value = Resource.Error("ID du livre invalide.")
            Log.w("BookDetailViewModel", "loadBookDetails called with blank bookId.")
            return
        }

        Log.d("BookDetailViewModel", "Loading details for book ID: $bookId")
        viewModelScope.launch {
            getBookByIdUseCase(bookId)
                .catch { e ->
                    Log.e("BookDetailViewModel", "Exception in book detail flow for ID $bookId", e)
                    _bookDetails.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _bookDetails.value = resource // Directement assigner la ressource
                    when(resource) {
                        is Resource.Success -> Log.d("BookDetailViewModel", "Book ID $bookId loaded: ${resource.data?.title}")
                        is Resource.Error -> Log.e("BookDetailViewModel", "Error loading book ID $bookId: ${resource.message}")
                        is Resource.Loading -> Log.d("BookDetailViewModel", "Loading book ID $bookId...")
                    }
                }
        }
    }
}