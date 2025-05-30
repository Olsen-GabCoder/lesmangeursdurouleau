package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.ChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepositoryImpl
import com.lesmangeursdurouleau.app.repository.BookRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth,
        firebaseStorageService: FirebaseStorageService
    ): UserRepository {
        return UserRepositoryImpl(firestore, firebaseAuth, firebaseStorageService)
    }

    @Provides
    @Singleton
    fun provideBookRepository(
        firestore: FirebaseFirestore // Hilt le fournit
    ): BookRepositoryImpl {
        // Assure-toi que BookRepositoryImpl n'instancie pas FirebaseFirestore lui-même
        // mais le prend bien en constructeur.
        // Si BookRepositoryImpl a un constructeur par défaut firestore = FirebaseFirestore.getInstance(),
        // Hilt injectera quand même celui fourni ici si le paramètre est présent.
        return BookRepositoryImpl(firestore)
    }

    // NOUVEAU PROVIDER CI-DESSOUS
    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth
    ): ChatRepository {
        return ChatRepositoryImpl(firestore, firebaseAuth)
    }
}