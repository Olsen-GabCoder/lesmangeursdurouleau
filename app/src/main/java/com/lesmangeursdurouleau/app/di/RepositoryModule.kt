package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService // Assure-toi que ce chemin est correct
import com.lesmangeursdurouleau.app.data.repository.UserRepository // Interface
import com.lesmangeursdurouleau.app.data.repository.UserRepositoryImpl // Implémentation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent // Ou une portée plus adaptée si besoin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Les dépendances seront des singletons
object RepositoryModule {

    // Hilt sait déjà comment créer FirebaseStorageService car il a @Inject constructor et @Singleton
    // et il sait comment créer FirebaseStorage (grâce à FirebaseModule).

    @Provides
    @Singleton // Une seule instance de UserRepository pour toute l'app
    fun provideUserRepository(
        firestore: FirebaseFirestore, // Hilt saura le fournir grâce à FirebaseModule
        firebaseAuth: FirebaseAuth,   // Hilt saura le fournir grâce à FirebaseModule
        firebaseStorageService: FirebaseStorageService // Hilt saura le fournir car il est @Inject constructor
    ): UserRepository { // Important: retourner l'interface
        return UserRepositoryImpl(firestore, firebaseAuth, firebaseStorageService)
    }

    // Plus tard, tu ajouteras ici comment fournir BookRepository, MeetingRepository, etc.
    // Exemple pour BookRepository (en supposant que tu as nettoyé les duplications) :
    /*
    import com.lesmangeursdurouleau.app.repository.BookRepository // La bonne interface
    import com.lesmangeursdurouleau.app.repository.BookRepositoryImpl // La bonne implémentation

    @Provides
    @Singleton
    fun provideBookRepository(
        firestore: FirebaseFirestore // Hilt le fournit
    ): BookRepository {
        return BookRepositoryImpl(firestore)
    }
    */
}