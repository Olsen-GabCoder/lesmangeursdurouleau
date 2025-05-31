package com.lesmangeursdurouleau.app.services // Package correct

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "MyFMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token) // Bonne pratique d'appeler super
        Log.i(TAG, "Nouveau token FCM généré: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        if (token == null) {
            Log.w(TAG, "Token FCM est null, impossible de le sauvegarder.")
            return
        }

        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "Utilisateur non connecté, le token FCM '$token' ne sera pas sauvegardé pour l'instant.")
            // Optionnel : Stocker le token dans SharedPreferences pour une sauvegarde ultérieure après connexion.
            return
        }

        Log.d(TAG, "Tentative de sauvegarde du token FCM '$token' pour l'utilisateur '$currentUserId'.")
        serviceScope.launch {
            // À l'étape suivante, nous ajouterons la méthode à UserRepository
            // userRepository.updateUserFcmToken(currentUserId, token)
            Log.i(TAG, "LOGIQUE DE SAUVEGARDE DU TOKEN DANS UserRepository (pour l'étape suivante) pour UID: $currentUserId avec Token: $token")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage) // Bonne pratique
        Log.d(TAG, "Message FCM reçu de: ${remoteMessage.from}")

        // Vérifier la charge utile de données
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Charge utile de données: ${remoteMessage.data}")
            // TODO: Extraire les données et appeler une fonction de NotificationHelper.kt
            // val title = remoteMessage.data["title"] ?: "Nouveau Message"
            // val body = remoteMessage.data["body"] ?: "Vous avez un nouveau message."
            // val notificationHelper = ... (comment obtenir une instance ou appeler une fonction statique ?)
            // notificationHelper.showNotification(applicationContext, title, body)
        }

        // Vérifier la charge utile de notification (généralement quand l'app est au premier plan)
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification FCM reçue au premier plan: Title='${it.title}', Body='${it.body}'")
            // Si tu veux afficher ta propre notification même si l'app est au premier plan
            // (et non pas laisser le système gérer si c'est un message de notification pur)
            // val title = it.title ?: "Nouveau Message"
            // val body = it.body ?: "Vous avez un nouveau message."
            // TODO: Appeler une fonction de NotificationHelper.kt
            // notificationHelper.showNotification(applicationContext, title, body)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "MyFirebaseMessagingService détruit, scope de coroutine annulé.")
    }
}