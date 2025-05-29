package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

class GetBookByIdUseCase(
    private val bookRepository: BookRepository
) {
    operator fun invoke(bookId: String): Flow<Resource<Book>> {
        if (bookId.isBlank()) {
            // Optionnel: gérer le cas d'un ID vide en amont
            // return kotlinx.coroutines.flow.flowOf(Resource.Error("L'ID du livre ne peut pas être vide."))
        }
        return bookRepository.getBookById(bookId)
    }
}