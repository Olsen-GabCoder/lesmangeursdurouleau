package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val messageId: String = "", // ID unique du message, sera généré par Firestore ou localement
    val senderId: String = "", // UID de l'expéditeur
    val senderUsername: String = "", // Pseudo de l'expéditeur (pour affichage)
    val text: String = "",
    @ServerTimestamp // Important pour que Firestore gère le timestamp automatiquement à l'écriture
    val timestamp: Date? = null // Sera un Timestamp Firestore, converti en Date à la lecture
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    constructor() : this("", "", "", "", null)
}