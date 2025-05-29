package com.lesmangeursdurouleau.app.ui.members

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentProfileBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthActivity
import com.lesmangeursdurouleau.app.ui.auth.AuthViewModel
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
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
                    .into(binding.ivProfilePicture)

                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val imageData = inputStream?.readBytes()
                inputStream?.close()
                imageData?.let { data ->
                    authViewModel.currentUser.value?.uid?.let { userId ->
                        binding.buttonSelectPicture.isEnabled = false
                        authViewModel.updateProfilePicture(userId, data)
                    } ?: Snackbar.make(binding.root, "Utilisateur non connecté", Snackbar.LENGTH_SHORT).show()
                } ?: Snackbar.make(binding.root, "Erreur lors de la lecture de l'image", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
                binding.buttonSelectPicture.isEnabled = true
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
                binding.tvProfileEmail.text = firebaseUser.email ?: "Email non disponible"
                FirebaseFirestore.getInstance().collection("users").document(firebaseUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        val photoUrl = document.getString("profilePictureUrl")
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(binding.ivProfilePicture)
                    }
                    .addOnFailureListener {
                        // En cas d'échec de chargement depuis Firestore, essayer depuis Firebase Auth (moins fiable pour URL mise à jour)
                        Glide.with(this)
                            .load(firebaseUser.photoUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(binding.ivProfilePicture)
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
            when (result) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, "Pseudo mis à jour", Snackbar.LENGTH_SHORT).show()
                    binding.buttonSaveProfile.isEnabled = true
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: "Erreur de mise à jour du pseudo", Snackbar.LENGTH_LONG).show()
                    binding.buttonSaveProfile.isEnabled = true
                }
                is Resource.Loading -> {
                    binding.buttonSaveProfile.isEnabled = false
                }
                null -> {
                    binding.buttonSaveProfile.isEnabled = true
                }
            }
        }

        authViewModel.profilePictureUpdateResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Resource.Success -> {
                    Glide.with(this) // Recharger l'image à partir de la nouvelle URL fournie par le ViewModel
                        .load(result.data)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(binding.ivProfilePicture)
                    Snackbar.make(binding.root, "Photo de profil mise à jour", Snackbar.LENGTH_SHORT).show()
                    binding.buttonSelectPicture.isEnabled = true
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message ?: "Erreur de mise à jour de la photo", Snackbar.LENGTH_LONG).show()
                    // Ne pas forcément remettre le placeholder, l'ancienne image est peut-être encore bonne
                    binding.buttonSelectPicture.isEnabled = true
                }
                is Resource.Loading -> {
                    binding.buttonSelectPicture.isEnabled = false
                }
                null -> {
                    binding.buttonSelectPicture.isEnabled = true
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonLogout.setOnClickListener {
            authViewModel.logoutUser()
            navigateToAuthActivity()
        }

        binding.buttonSelectPicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.buttonSaveProfile.setOnClickListener {
            val newUsername = binding.etProfileUsername.text.toString().trim()
            binding.tilProfileUsername.error = null // Réinitialiser l'erreur
            if (newUsername.isNotBlank()) {
                authViewModel.currentUser.value?.uid?.let { userId ->
                    authViewModel.updateUserProfile(userId, newUsername)
                } ?: Snackbar.make(binding.root, "Utilisateur non connecté", Snackbar.LENGTH_SHORT).show()
            } else {
                binding.tilProfileUsername.error = "Le pseudo ne peut pas être vide"
            }
        }

        binding.buttonViewMembers.setOnClickListener {
            // Assure-toi que l'action est bien définie dans ton nav_graph.xml
            // et que l'ID de la destination ProfileFragment est correct pour générer les Directions
            // Exemple: ProfileFragmentDirections.actionProfileFragmentToMembersFragment()
            // Ou si l'ID est navigation_members_profile:
            // NavigationMembersProfileDirections.actionProfileFragmentToMembersFragment()
            // Si tu as bien utilisé <action android:id="@+id/action_profileFragment_to_membersFragment" ... />
            // Cela devrait être correct:
            findNavController().navigate(R.id.action_profileFragment_to_membersFragment)
        }
    }

    private fun navigateToAuthActivity() {
        val intent = Intent(requireActivity(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finishAffinity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}