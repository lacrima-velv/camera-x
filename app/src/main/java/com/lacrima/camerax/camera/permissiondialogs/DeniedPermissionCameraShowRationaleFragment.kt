package com.lacrima.camerax.camera.permissiondialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacrima.camerax.R

class DeniedPermissionCameraShowRationaleFragment : DialogFragment() {

    private lateinit var listener: DeniedCameraPermissionClickListener
    /*
    Parent fragment must implement this interface to set click listener on the button to call
    permission request
     */
    interface DeniedCameraPermissionClickListener {
        fun onYesClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = parentFragment as DeniedCameraPermissionClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DeniedCameraPermissionClickListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.denied_camera_permission_rationale_title))
            .setMessage(getString(R.string.denied_camera_permission_rationale_body))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                listener.onYesClick()
            }
            .setNegativeButton(getString(R.string.no_thanks)) {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }

}