package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // ou viewModels si ce ViewModel n'est pas partagé
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentMembersBinding
import com.lesmangeursdurouleau.app.databinding.ItemMemberBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthViewModel // Assumant que tu veux accéder à l'utilisateur actuel
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

// Data class Member reste ici, c'est ok pour ce fichier
data class Member(
    val uid: String,
    val username: String?,
    val email: String?,
    val profilePictureUrl: String?
)

class MembersAdapter(
    private val members: List<Member>,
    private val onItemClick: (Member) -> Unit
) : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: Member) {
            binding.tvMemberUsername.text = member.username ?: itemView.context.getString(R.string.username_not_defined)
            binding.tvMemberEmail.text = member.email ?: itemView.context.getString(R.string.na) // R.string.na à définir
            Glide.with(itemView)
                .load(member.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivMemberPicture)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.bind(member)
        holder.itemView.setOnClickListener { onItemClick(member) }
    }

    override fun getItemCount(): Int = members.size
}


@AndroidEntryPoint // ANNOTATION AJOUTÉE
class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    // Si tu as besoin d'informations de l'utilisateur connecté (ex: pour ne pas l'afficher dans la liste)
    private val authViewModel: AuthViewModel by activityViewModels()
    // Si MembersFragment a son propre ViewModel pour charger les membres, tu l'injecterais ici:
    // private val membersViewModel: MembersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        fetchMembers()
    }

    private fun setupRecyclerView() {
        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.setHasFixedSize(true) // Si la taille des items ne change pas
    }

    private fun fetchMembers() {
        binding.progressBarMembers.visibility = View.VISIBLE
        binding.tvErrorMessage.visibility = View.GONE
        binding.rvMembers.visibility = View.GONE

        FirebaseFirestore.getInstance().collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.progressBarMembers.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    binding.tvErrorMessage.text = getString(R.string.no_members_found) // R.string.no_members_found à définir
                    binding.tvErrorMessage.visibility = View.VISIBLE
                    binding.rvMembers.visibility = View.GONE
                } else {
                    val members = querySnapshot.documents.mapNotNull { doc ->
                        Member(
                            uid = doc.getString("uid") ?: return@mapNotNull null,
                            username = doc.getString("username"),
                            email = doc.getString("email"),
                            profilePictureUrl = doc.getString("profilePictureUrl")
                        )
                    }
                    binding.rvMembers.adapter = MembersAdapter(members) { member ->
                        Snackbar.make(
                            binding.root,
                            getString(R.string.member_clicked, member.username ?: member.uid), // R.string.member_clicked à définir
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    binding.rvMembers.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBarMembers.visibility = View.GONE
                binding.tvErrorMessage.text = getString(R.string.error_loading_members, exception.localizedMessage ?: "Erreur inconnue") // R.string.error_loading_members à définir
                binding.tvErrorMessage.visibility = View.VISIBLE
                binding.rvMembers.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_loading_members, exception.localizedMessage ?: "Erreur inconnue"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null // Bonne pratique
        _binding = null
    }
}