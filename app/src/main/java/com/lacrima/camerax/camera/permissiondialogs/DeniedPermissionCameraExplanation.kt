package com.lacrima.camerax.camera.permissiondialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacrima.camerax.R

class DeniedPermissionCameraExplanation : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.denied_camera_permission_explanation_title))
            .setMessage(getString(R.string.denied_camera_permission_explanation_body))
            .setPositiveButton(getString(R.string.ok)) {
                    dialog, _ -> dialog.dismiss()
            }
            .show()
    }
}