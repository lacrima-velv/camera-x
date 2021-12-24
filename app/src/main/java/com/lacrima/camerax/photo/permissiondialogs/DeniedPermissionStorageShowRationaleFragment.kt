package com.lacrima.camerax.photo.permissiondialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeniedPermissionStorageShowRationaleFragment : DialogFragment() {

    private lateinit var listener: DeniedStoragePermissionClickListener
    /*
    Parent fragment must implement this interface to set click listener on the button to call
    permission request
     */
    interface DeniedStoragePermissionClickListener {
        fun onYesClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = parentFragment as DeniedStoragePermissionClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DeniedStoragePermissionClickListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission to write into external storage is required")
            .setMessage("The photo can't be saved until permission to write into external storage is granted. Would you like to grant the permission?")
            .setPositiveButton("Yes") { _, _ ->
                listener.onYesClick()
            }
            .setNegativeButton("No, thanks") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }

}