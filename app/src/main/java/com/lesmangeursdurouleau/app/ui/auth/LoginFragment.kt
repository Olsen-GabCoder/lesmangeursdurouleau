package com.lesmangeursdurouleau.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
// import androidx.fragment.app.viewModels // Inutilisé si activityViewModels est utilisé
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentLoginBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthResultWrapper
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (authViewModel.justRegistered.value == true) {
            authViewModel.consumeJustRegisteredEvent()
        }

        setupObservers()

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmailLogin.text.toString().trim()
            val password = binding.etPasswordLogin.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            } else {
                authViewModel.loginUser(email, password)
            }
        }

        binding.tvGoToRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupObservers() {
        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResultWrapper.Loading -> {
                    binding.progressBarLogin.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                    binding.etEmailLogin.isEnabled = false
                    binding.etPasswordLogin.isEnabled = false
                    binding.tvGoToRegister.isClickable = false
                }
                is AuthResultWrapper.Success -> {
                    binding.progressBarLogin.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.login_successful, result.user.email ?: "Utilisateur"), Toast.LENGTH_LONG).show()

                    val intent = Intent(requireActivity(), MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                is AuthResultWrapper.Error -> {
                    binding.progressBarLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.etEmailLogin.isEnabled = true
                    binding.etPasswordLogin.isEnabled = true
                    binding.tvGoToRegister.isClickable = true
                    Toast.makeText(requireContext(), getString(R.string.login_failed, result.exception.message ?: "Erreur inconnue"), Toast.LENGTH_LONG).show()
                }

                null -> TODO()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}