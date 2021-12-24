package com.example.gallery.permissiondialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeniedPermissionCameraExplanation : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission to camera is not granted")
            .setMessage("Previously you denied permission to access camera, so the photos can't be made. You could grant this permission in Application Settings.")
            .setPositiveButton("OK") {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }
}