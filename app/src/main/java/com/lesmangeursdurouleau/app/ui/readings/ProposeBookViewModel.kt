package com.lesmangeursdurouleau.app.ui.readings // Package cohérent avec le chemin fourni

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.domain.usecase.books.ProposeBookUseCase
// Pour l'instanciation directe (temporaire)
import com.lesmangeursdurouleau.app.data.model.Book // Importé pour le ProposeBookUseCase
import com.lesmangeursdurouleau.app.repository.BookRepositoryImpl // Importé pour le ProposeBookUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.launch

class ProposeBookViewModel : ViewModel() { // Constructeur vide si pas d'injection Hilt pour l'instant

    private val _proposalResult = MutableLiveData<Resource<Unit>>()
    val proposalResult: LiveData<Resource<Unit>> = _proposalResult

    // TODO: Injecter avec Hilt plus tard
    // Instanciation directe du repository pour le use case (temporaire)
    private val bookRepository = BookRepositoryImpl()
    private val proposeBookUseCase = ProposeBookUseCase(bookRepository)

    fun proposeBook(title: String, author: String, synopsis: String?, coverImageUrl: String?) {
        viewModelScope.launch {
            _proposalResult.value = Resource.Loading()
            Log.d("ProposeBookViewModel", "Attempting to propose book: $title by $author")
            try {
                // L'UseCase attend title, author, synopsis, coverImageUrl
                val result = proposeBookUseCase.invoke(
                    title = title,
                    author = author,
                    synopsis = synopsis,
                    coverImageUrl = coverImageUrl
                )
                _proposalResult.value = result
                if (result is Resource.Success) {
                    Log.i("ProposeBookViewModel", "Book proposed successfully: $title")
                } else if (result is Resource.Error) {
                    Log.e("ProposeBookViewModel", "Error proposing book: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e("ProposeBookViewModel", "Exception proposing book", e)
                _proposalResult.value = Resource.Error("Une erreur inattendue est survenue: ${e.localizedMessage}")
            }
        }
    }
}