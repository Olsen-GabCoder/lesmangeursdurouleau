package com.lesmangeursdurouleau.app.domain.usecase.books // Package correct selon votre chemin

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.repository.BookRepository // Vérifier si le chemin vers BookRepository est bon
import com.lesmangeursdurouleau.app.utils.Resource

class ProposeBookUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(title: String, author: String, synopsis: String?, coverImageUrl: String?): Resource<Unit> {
        if (title.isBlank() || author.isBlank()) {
            return Resource.Error("Le titre et l'auteur sont obligatoires.")
        }
        // Crée un objet Book. L'ID sera ignoré par le repository lors de l'utilisation de .add()
        // mais il est bon de le prévoir si on voulait un jour fixer l'ID côté client.
        val newBook = Book(
            id = "", // Sera généré par Firestore ou non utilisé si on passe un Map
            title = title,
            author = author,
            synopsis = synopsis,
            coverImageUrl = coverImageUrl
        )
        return bookRepository.addBook(newBook)
    }
}