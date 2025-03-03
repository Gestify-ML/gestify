package com.example.gestify.fragments

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import  com.example.gestify.databinding.ItemGestureRecognizerBinding
import com.google.mediapipe.tasks.components.containers.Category
import java.util.Locale
import kotlin.math.min

data class GestureMusicStatus(
    val gesture: Category?,
    val musicStatus: String
)

class GestureRecognizerResultsAdapter :
    RecyclerView.Adapter<GestureRecognizerResultsAdapter.ViewHolder>() {

    companion object {
        private const val NO_VALUE = "--"
    }

    private var adapterResults: MutableList<GestureMusicStatus> = mutableListOf()
    private var adapterSize: Int = 0

    // Update the adapter with both categories and music status
    @SuppressLint("NotifyDataSetChanged")
    fun updateGestureCategories(categories: List<Category>?) {
        if (categories != null) {
            val sortedCategories = categories.sortedByDescending { it.score() }
            val min = min(sortedCategories.size, adapterSize)

            // Ensure adapterResults has enough space to hold the gesture categories
            if (adapterResults.size < min) {
                // If there aren't enough entries, add more (or resize) the list
                adapterResults.addAll(List(min - adapterResults.size) { GestureMusicStatus(null, "") })
            }

            for (i in 0 until min) {
                // Update the gesture part only
                adapterResults[i] = GestureMusicStatus(sortedCategories[i], adapterResults[i].musicStatus)
            }

            // Sort results by gesture index
            adapterResults.sortBy { it.gesture?.index() }
            notifyDataSetChanged()
        }
    }



    // Update music status
    @SuppressLint("NotifyDataSetChanged")
    fun updateMusicStatus(musicStatus: String) {
        for (i in 0 until adapterResults.size) {
            // Update the music status for each entry
            adapterResults[i] = GestureMusicStatus(adapterResults[i].gesture, musicStatus)
        }
        notifyDataSetChanged()
    }

    fun updateAdapterSize(size: Int) {
        adapterSize = size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemGestureRecognizerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = adapterResults[position]
        holder.bind(result.gesture?.categoryName(), result.gesture?.score(), result.musicStatus)
    }

    override fun getItemCount(): Int = adapterResults.size

    inner class ViewHolder(private val binding: ItemGestureRecognizerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Bind both the gesture result and the music status
        fun bind(label: String?, score: Float?, musicStatus: String) {
            with(binding) {
                tvLabel.text = label ?: NO_VALUE
                tvScore.text = if (score != null) String.format(
                    Locale.US,
                    "%.2f",
                    score
                ) else NO_VALUE
                tvMusicStatus.text = musicStatus // Bind music status
            }
        }
    }
}
