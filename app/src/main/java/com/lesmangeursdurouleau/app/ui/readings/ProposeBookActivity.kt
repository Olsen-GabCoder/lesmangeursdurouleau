// CORRECTION DU PACKAGE : Il est maintenant directement dans ui/readings/
package com.lesmangeursdurouleau.app.ui.readings

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.ActivityProposeBookBinding
// L'import du ViewModel est correct si ProposeBookViewModel est aussi dans ui/readings/
import com.lesmangeursdurouleau.app.ui.readings.ProposeBookViewModel
import com.lesmangeursdurouleau.app.utils.Resource

class ProposeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProposeBookBinding
    private val viewModel: ProposeBookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProposeBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarProposeBook)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Proposer un Livre" // Optionnel: Définir le titre ici aussi

        setupClickListeners()
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.btnSubmitProposal.setOnClickListener {
            val title = binding.etProposeBookTitle.text.toString().trim()
            val author = binding.etProposeBookAuthor.text.toString().trim()
            val synopsis = binding.etProposeBookSynopsis.text.toString().trim()
            val coverUrl = binding.etProposeBookCoverUrl.text.toString().trim()

            // Réinitialiser les erreurs
            binding.tilProposeBookTitle.error = null
            binding.tilProposeBookAuthor.error = null

            var isValid = true
            if (title.isEmpty()) {
                binding.tilProposeBookTitle.error = getString(R.string.error_field_required)
                isValid = false
            }

            if (author.isEmpty()) {
                binding.tilProposeBookAuthor.error = getString(R.string.error_field_required)
                isValid = false
            }

            if (isValid) {
                viewModel.proposeBook(title, author, synopsis.ifEmpty { null }, coverUrl.ifEmpty { null })
            }
        }
    }

    private fun setupObservers() {
        viewModel.proposalResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarProposeBook.visibility = View.VISIBLE
                    binding.btnSubmitProposal.isEnabled = false
                    // Optionnel: Désactiver les champs de texte
                    binding.etProposeBookTitle.isEnabled = false
                    binding.etProposeBookAuthor.isEnabled = false
                    binding.etProposeBookSynopsis.isEnabled = false
                    binding.etProposeBookCoverUrl.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBarProposeBook.visibility = View.GONE
                    Toast.makeText(this, "Livre proposé avec succès !", Toast.LENGTH_LONG).show()
                    finish() // Ferme l'activité après succès
                }
                is Resource.Error -> {
                    binding.progressBarProposeBook.visibility = View.GONE
                    binding.btnSubmitProposal.isEnabled = true
                    // Optionnel: Réactiver les champs de texte
                    binding.etProposeBookTitle.isEnabled = true
                    binding.etProposeBookAuthor.isEnabled = true
                    binding.etProposeBookSynopsis.isEnabled = true
                    binding.etProposeBookCoverUrl.isEnabled = true
                    Toast.makeText(this, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                    Log.e("ProposeBookActivity", "Error proposing book: ${resource.message}")
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}