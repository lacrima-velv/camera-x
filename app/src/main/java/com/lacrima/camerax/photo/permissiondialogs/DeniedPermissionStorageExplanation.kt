package com.lacrima.camerax.photo.permissiondialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeniedPermissionStorageExplanation : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission to external stprage is not granted")
            .setMessage("Previously you denied permission to write into external storage, so the photo can't be save. You could grant this permission in Application Settings.")
            .setPositiveButton("OK") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }
}