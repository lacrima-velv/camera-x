package com.example.gallery.permissiondialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
            .setTitle("Permission to camera is required")
            .setMessage("Photos can't be made until permission to camera is granted. Would you like to grant the permission?")
            .setPositiveButton("Yes") { _, _ ->
                listener.onYesClick()
            }
            .setNegativeButton("No, thanks") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }

}