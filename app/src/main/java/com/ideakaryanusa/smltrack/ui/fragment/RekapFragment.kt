package com.ideakaryanusa.smltrack.ui.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Placeholder sementara - akan diisi lengkap setelah Home terbukti jalan.
 */
class RekapFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val frame = FrameLayout(requireContext())
        val tv = TextView(requireContext()).apply {
            text = "Rekap\n(segera hadir)"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        frame.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })
        return frame
    }
}
