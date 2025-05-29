package com.lesmangeursdurouleau.app.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await // NÉCESSAIRE pour .await() sur les tâches Firestore

class BookRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BookRepository {

    // ... (getAllBooks et getBookById restent inchangés)
    override fun getAllBooks(): Flow<Resource<List<Book>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("BookRepositoryImpl", "Error listening for all books updates", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val books = mutableListOf<Book>()
                    for (document in snapshot.documents) {
                        try {
                            val book = Book(
                                id = document.id,
                                title = document.getString("title") ?: "",
                                author = document.getString("author") ?: "",
                                coverImageUrl = document.getString("coverImageUrl"),
                                synopsis = document.getString("synopsis")
                            )
                            books.add(book)
                        } catch (e: Exception) {
                            Log.e("BookRepositoryImpl", "Error converting document to Book: ${document.id}", e)
                        }
                    }
                    Log.d("BookRepositoryImpl", "All Books fetched: ${books.size}")
                    trySend(Resource.Success(books))
                } else {
                    Log.d("BookRepositoryImpl", "getAllBooks snapshot is null")
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose {
            Log.d("BookRepositoryImpl", "Closing all books listener.")
            listenerRegistration.remove()
        }
    }

    override fun getBookById(bookId: String): Flow<Resource<Book>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d("BookRepositoryImpl", "Fetching book with ID: $bookId")
        val documentRef = firestore.collection(FirebaseConstants.COLLECTION_BOOKS).document(bookId)
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.w("BookRepositoryImpl", "Error listening for book ID $bookId updates", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    val book = Book(
                        id = documentSnapshot.id,
                        title = documentSnapshot.getString("title") ?: "",
                        author = documentSnapshot.getString("author") ?: "",
                        coverImageUrl = documentSnapshot.getString("coverImageUrl"),
                        synopsis = documentSnapshot.getString("synopsis")
                    )
                    Log.d("BookRepositoryImpl", "Book ID $bookId fetched: ${book.title}")
                    trySend(Resource.Success(book))
                } catch (e: Exception) {
                    Log.e("BookRepositoryImpl", "Error converting document to Book for ID $bookId", e)
                    trySend(Resource.Error("Erreur de conversion des données du livre."))
                }
            } else {
                Log.w("BookRepositoryImpl", "Book with ID $bookId does not exist.")
                trySend(Resource.Error("Livre non trouvé."))
            }
        }
        awaitClose {
            Log.d("BookRepositoryImpl", "Closing listener for book ID $bookId.")
            listenerRegistration.remove()
        }
    }

    // NOUVELLE IMPLÉMENTATION
    override suspend fun addBook(book: Book): Resource<Unit> {
        return try {
            // Firestore générera un ID si nous utilisons .add()
            // Ou nous pourrions utiliser book.id si nous voulons le fixer côté client (mais .add() est plus simple pour commencer)
            // Pour utiliser .add(), l'objet Book ne devrait pas avoir d'ID pré-défini, ou on ne l'utilise pas pour la sauvegarde.
            // Créons un Map pour ne sauvegarder que les champs nécessaires et laisser Firestore gérer l'ID.
            val bookData = hashMapOf(
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis, // Sera null si le synopsis du Book est null
                "coverImageUrl" to book.coverImageUrl, // Sera null si l'URL du Book est null
                "proposedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp() // Ajoute un timestamp de proposition
                // Tu pourrais ajouter 'proposedByUserId' = FirebaseAuth.getInstance().currentUser?.uid ici aussi
            )

            firestore.collection(FirebaseConstants.COLLECTION_BOOKS)
                .add(bookData) // .add() génère un ID automatique
                .await() // Attend la fin de l'opération
            Log.d("BookRepositoryImpl", "Book added successfully: ${book.title}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e("BookRepositoryImpl", "Error adding book: ${book.title}", e)
            Resource.Error("Erreur lors de l'ajout du livre: ${e.localizedMessage}")
        }
    }
}