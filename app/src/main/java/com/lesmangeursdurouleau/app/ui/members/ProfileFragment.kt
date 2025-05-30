package com.lesmangeursdurouleau.app.ui.members

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController // Cet import est déjà là, c'est bien
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentProfileBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthActivity
import com.lesmangeursdurouleau.app.ui.auth.AuthViewModel
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .circleCrop() // Ajouté pour cohérence avec les autres affichages de profil
                    .into(binding.ivProfilePicture)

                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val imageData = inputStream?.readBytes()
                inputStream?.close()
                imageData?.let { data ->
                    authViewModel.currentUser.value?.uid?.let { userId ->
                        binding.buttonSelectPicture.isEnabled = false // Désactiver pendant l'upload
                        authViewModel.updateProfilePicture(userId, data)
                    } ?: Snackbar.make(binding.root, "Utilisateur non connecté", Snackbar.LENGTH_SHORT).show()
                } ?: Snackbar.make(binding.root, "Erreur lors de la lecture de l'image", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
                binding.buttonSelectPicture.isEnabled = true // Réactiver en cas d'erreur de lecture locale
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { firebaseUser ->
            if (firebaseUser != null) {
                binding.tvProfileEmail.text = firebaseUser.email ?: getString(R.string.email_not_available) // Utilisation de string.xml
                // Essayer de charger depuis Firestore en priorité car c'est là que l'URL est mise à jour
                // par notre logique updateUserProfilePicture
                FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val photoUrl = document.getString("profilePictureUrl")
                            Glide.with(this)
                                .load(photoUrl) // Peut être null si pas de photo dans Firestore
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder) // Si photoUrl est null ou invalide
                                .circleCrop()
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .into(binding.ivProfilePicture)
                        } else {
                            // Document Firestore non trouvé, essayer Firebase Auth (fallback)
                            loadProfilePictureFromAuth(firebaseUser.photoUrl?.toString())
                        }
                    }
                    .addOnFailureListener {
                        // Échec de lecture Firestore, essayer Firebase Auth (fallback)
                        loadProfilePictureFromAuth(firebaseUser.photoUrl?.toString())
                    }
            } else {
                binding.tvProfileEmail.text = getString(R.string.not_connected)
                binding.etProfileUsername.setText(getString(R.string.na))
                binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
            }
        }

        authViewModel.userDisplayName.observe(viewLifecycleOwner) { displayName ->
            binding.etProfileUsername.setText(displayName ?: getString(R.string.username_not_defined))
        }

        authViewModel.profileUpdateResult.observe(viewLifecycleOwner) { result ->
            binding.buttonSaveProfile.isEnabled = result !is Resource.Loading
            when (result) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, "Pseudo mis à jour", Snackbar.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: "Erreur de mise à jour du pseudo", Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> {
                    // Le bouton est déjà désactivé
                }
                null -> { /* Ne rien faire, état initial ou reset */ }
            }
        }

        authViewModel.profilePictureUpdateResult.observe(viewLifecycleOwner) { result ->
            binding.buttonSelectPicture.isEnabled = result !is Resource.Loading // Activer/Désactiver le bouton
            when (result) {
                is Resource.Success -> {
                    Glide.with(this)
                        .load(result.data) // Nouvelle URL de la photo
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(binding.ivProfilePicture)
                    Snackbar.make(binding.root, "Photo de profil mise à jour", Snackbar.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: "Erreur de mise à jour de la photo", Snackbar.LENGTH_LONG).show()
                    // Optionnel: Recharger l'ancienne photo si possible ou laisser la version locale déjà affichée
                }
                is Resource.Loading -> {
                    // Le bouton est déjà désactivé
                }
                null -> { /* Ne rien faire, état initial ou reset */ }
            }
        }
    }

    private fun loadProfilePictureFromAuth(photoUrl: String?) {
        Glide.with(this)
            .load(photoUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivProfilePicture)
    }

    private fun setupClickListeners() {
        binding.buttonLogout.setOnClickListener {
            authViewModel.logoutUser()
            navigateToAuthActivity()
        }

        binding.buttonSelectPicture.setOnClickListener {
            // S'assurer que l'utilisateur est connecté avant de permettre la sélection
            if (authViewModel.currentUser.value != null) {
                pickImageLauncher.launch("image/*")
            } else {
                Snackbar.make(binding.root, "Veuillez vous connecter pour changer de photo.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveProfile.setOnClickListener {
            val newUsername = binding.etProfileUsername.text.toString().trim()
            binding.tilProfileUsername.error = null
            if (newUsername.isNotBlank()) {
                authViewModel.currentUser.value?.uid?.let { userId ->
                    authViewModel.updateUserProfile(userId, newUsername)
                } ?: Snackbar.make(binding.root, "Utilisateur non connecté", Snackbar.LENGTH_SHORT).show()
            } else {
                binding.tilProfileUsername.error = "Le pseudo ne peut pas être vide"
            }
        }

        binding.buttonViewMembers.setOnClickListener {
            // Utilisation de l'action Safe Args générée pour une navigation plus sûre
            val action = ProfileFragmentDirections.actionProfileFragmentToMembersFragment()
            findNavController().navigate(action)
        }

        // --- MODIFICATION ICI ---
        binding.buttonGeneralChat.setOnClickListener {
            // Utilisation de l'action Safe Args générée
            val action = ProfileFragmentDirections.actionProfileFragmentToChatFragment()
            findNavController().navigate(action)
        }
        // --- FIN DE LA MODIFICATION ---
    }

    private fun navigateToAuthActivity() {
        val intent = Intent(requireActivity(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finishAffinity() // finishAffinity() pour s'assurer que toutes les activités de la tâche sont fermées
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}