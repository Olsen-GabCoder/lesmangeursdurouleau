package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublicProfileViewModel by viewModels()
    private val args: PublicProfileFragmentArgs by navArgs() // Pour récupérer username pour le titre de l'AppBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mettre à jour le titre de l'AppBar avec le nom d'utilisateur passé en argument
        // (ou le nom récupéré du ViewModel une fois chargé, pour plus de précision)
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.username.takeIf { !it.isNullOrBlank() } ?: "Profil"


        setupObservers()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarPublicProfile.visibility = View.VISIBLE
                    binding.tvPublicProfileError.visibility = View.GONE
                    setProfileDataVisibility(View.GONE)
                }
                is Resource.Success -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    resource.data?.let { user ->
                        populateProfileData(user)
                        setProfileDataVisibility(View.VISIBLE)
                        // Mettre à jour le titre de l'AppBar avec le nom d'utilisateur réel si différent
                        if ((activity as? AppCompatActivity)?.supportActionBar?.title != user.username && user.username.isNotBlank()) {
                            (activity as? AppCompatActivity)?.supportActionBar?.title = user.username
                        }
                    } ?: run {
                        binding.tvPublicProfileError.text = getString(R.string.error_loading_user_data) // Tu devras ajouter cette string
                        binding.tvPublicProfileError.visibility = View.VISIBLE
                        setProfileDataVisibility(View.GONE)
                        Log.e("PublicProfileFragment", "User data is null on success for ID: ${args.userId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    binding.tvPublicProfileError.text = resource.message ?: getString(R.string.error_unknown) // Tu devras ajouter cette string
                    binding.tvPublicProfileError.visibility = View.VISIBLE
                    setProfileDataVisibility(View.GONE)
                    Log.e("PublicProfileFragment", "Error loading profile: ${resource.message}")
                }
            }
        }
    }

    private fun setProfileDataVisibility(visibility: Int) {
        binding.ivPublicProfilePicture.visibility = visibility
        binding.tvPublicProfileUsernameLabel.visibility = visibility
        binding.tvPublicProfileUsername.visibility = visibility
        binding.tvPublicProfileEmailLabel.visibility = visibility
        binding.tvPublicProfileEmail.visibility = visibility
        binding.tvPublicProfileJoinedLabel.visibility = visibility
        binding.tvPublicProfileJoinedDate.visibility = visibility
    }

    private fun populateProfileData(user: User) {
        Glide.with(this)
            .load(user.profilePictureUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder) // Peut-être une icône d'erreur différente si l'URL est invalide
            .transition(DrawableTransitionOptions.withCrossFade())
            .circleCrop() // Pour une photo de profil ronde
            .into(binding.ivPublicProfilePicture)

        binding.tvPublicProfileUsername.text = user.username.ifEmpty { getString(R.string.username_not_set) }
        binding.tvPublicProfileEmail.text = user.email.ifEmpty { getString(R.string.na) }

        user.createdAt?.let { timestamp ->
            try {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                // Firestore stocke souvent en UTC, donc spécifier le fuseau horaire peut être utile
                // si tu veux t'assurer que la conversion est correcte, mais pour une simple date,
                // Locale.getDefault() est souvent suffisant.
                // sdf.timeZone = TimeZone.getTimeZone("UTC") // Décommente si tu es sûr que c'est UTC
                binding.tvPublicProfileJoinedDate.text = sdf.format(Date(timestamp))
            } catch (e: Exception) {
                Log.e("PublicProfileFragment", "Erreur de formatage de la date createdAt: $timestamp", e)
                binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
            }
        } ?: run {
            binding.tvPublicProfileJoinedDate.text = getString(R.string.na)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}