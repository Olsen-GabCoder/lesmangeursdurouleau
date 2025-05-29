package com.lesmangeursdurouleau.app.ui.readings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentReadingsBinding
import com.lesmangeursdurouleau.app.ui.readings.adapter.BookListAdapter
import com.lesmangeursdurouleau.app.ui.readings.ProposeBookActivity
import dagger.hilt.android.AndroidEntryPoint // IMPORT AJOUTÉ

@AndroidEntryPoint // ANNOTATION AJOUTÉE
class ReadingsFragment : Fragment() {

    private var _binding: FragmentReadingsBinding? = null
    private val binding get() = _binding!!

    private val readingsViewModel: ReadingsViewModel by viewModels()
    private lateinit var bookListAdapter: BookListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        bookListAdapter = BookListAdapter { selectedBook ->
            navigateToBookDetail(selectedBook)
        }

        binding.recyclerViewBooks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookListAdapter
        }
    }

    private fun navigateToBookDetail(book: Book) {
        val action = ReadingsFragmentDirections.actionReadingsFragmentToBookDetailFragment(
            bookId = book.id,
            bookTitle = book.title
        )
        findNavController().navigate(action)
    }

    private fun setupObservers() {
        readingsViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBarReadings.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.recyclerViewBooks.visibility = View.GONE
                binding.tvErrorReadings.visibility = View.GONE
            }
        }

        readingsViewModel.books.observe(viewLifecycleOwner) { bookList ->
            bookListAdapter.submitList(bookList)
            updateEmptyStateView(bookList.isNullOrEmpty() && readingsViewModel.isLoading.value == false, null)
        }

        readingsViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            updateEmptyStateView(readingsViewModel.books.value.isNullOrEmpty() && readingsViewModel.isLoading.value == false, errorMessage)
        }
    }

    private fun updateEmptyStateView(showErrorOrEmpty: Boolean, errorMessage: String?) {
        if (readingsViewModel.isLoading.value == true) return

        if (showErrorOrEmpty) {
            binding.recyclerViewBooks.visibility = View.GONE
            binding.tvErrorReadings.visibility = View.VISIBLE
            binding.tvErrorReadings.text = errorMessage ?: getString(R.string.no_readings_available)
        } else {
            binding.recyclerViewBooks.visibility = View.VISIBLE
            binding.tvErrorReadings.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.fabProposeBook.setOnClickListener {
            val intent = Intent(requireActivity(), ProposeBookActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewBooks.adapter = null // Bonne pratique pour éviter les fuites avec RecyclerView
        _binding = null
    }
}