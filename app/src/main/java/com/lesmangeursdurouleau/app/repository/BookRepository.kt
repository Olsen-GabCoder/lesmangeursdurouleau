// BookRepository.kt
package com.lesmangeursdurouleau.app.repository

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<Resource<List<Book>>>
    fun getBookById(bookId: String): Flow<Resource<Book>>
    suspend fun addBook(book: Book): Resource<Unit> // La méthode qui nous intéresse ici
}