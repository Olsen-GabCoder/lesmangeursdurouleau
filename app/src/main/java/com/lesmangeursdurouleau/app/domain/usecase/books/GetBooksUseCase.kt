package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

class GetBooksUseCase(
    private val bookRepository: BookRepository // Dépendance à l'interface
) {
    operator fun invoke(): Flow<Resource<List<Book>>> {
        return bookRepository.getAllBooks()
    }
}