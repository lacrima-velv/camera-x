package com.lacrima.camerax.photo

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.davemorrissey.labs.subscaleview.ImageSource
import com.lacrima.camerax.MainViewModel
import com.lacrima.camerax.databinding.FragmentPhotoBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import com.lacrima.camerax.photo.SaveImageDialogFragment.DialogSaveImageClickListener
import com.lacrima.camerax.utils.Util.setUiWindowInsetsBottom
import com.lacrima.camerax.utils.Util.toPixels
import com.lacrima.camerax.databinding.CannotDisplayImageViewBinding
import com.lacrima.camerax.photo.permissiondialogs.DeniedPermissionStorageExplanation
import com.lacrima.camerax.photo.permissiondialogs.DeniedPermissionStorageShowRationaleFragment
import com.lacrima.camerax.utils.Util.removeStatusBar
import kotlinx.coroutines.flow.collectLatest

class PhotoFragment : Fragment(), DialogSaveImageClickListener {

    private lateinit var activityResultLauncherRequestPermission: ActivityResultLauncher<String>
    private lateinit var mainViewModel: MainViewModel
    private lateinit var binding: FragmentPhotoBinding
    private lateinit var cannotDisplayImageBinding: CannotDisplayImageViewBinding
    private var bitmap: Bitmap? = null
    private var imageNameFromViewModel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*
        Register the permissions callback, which handles the user's response to the
        system permissions dialog. Save the return value, an instance of
        ActivityResultLauncher.
         */
        activityResultLauncherRequestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission is granted. Continue the workflow
                imageNameFromViewModel?.let { saveImage(it) }
            } else {
                /*
                Explain to the user that the feature is unavailable because the
                features requires a permission that the user has denied. At the
                same time, respect the user's decision.
                 */
                showExplanationOfPermissionRequiringNoOptions()
            }

        }

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /**
    Show dialog with explanation why the permission is necessary,
    but don't provide an option to request it again
     */
    private fun showExplanationOfPermissionRequiringNoOptions() {
        val dialog = DeniedPermissionStorageExplanation()
        dialog.show(childFragmentManager, "WriteExternalStorageExplanation")
    }

    /**
     * Show dialog with rationale and and a button to request the permission again
     */
    private fun showDeniedPermissionShowRationale() {
        val dialog = DeniedPermissionStorageShowRationaleFragment()
        dialog.show(childFragmentManager, "WriteExternalStorageRationale")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentPhotoBinding.inflate(inflater, container, false)

        cannotDisplayImageBinding = CannotDisplayImageViewBinding.bind(binding.root)

        val marginBottomOfButtons = 24.toPixels

        setUiWindowInsetsBottom(binding.saveAs, marginBottomOfButtons)
        setUiWindowInsetsBottom(binding.cancel, marginBottomOfButtons)

        requireActivity().removeStatusBar()

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            launch {
                mainViewModel.bitmap.collectLatest { originalBitmap ->
                    bitmap = originalBitmap
                    displayImage(bitmap)
                }
            }
            launch {
                mainViewModel.imageName.collectLatest { name ->
                    imageNameFromViewModel = name
                }
            }
        }


        binding.saveAs.setOnClickListener {
            openSaveImageDialog()
        }

        binding.cancel.setOnClickListener {
            findNavController()
                .navigate(PhotoFragmentDirections.actionPhotoFragmentToCameraFragment())
        }

        showDefaultControls()

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun displayImage(bitmap: Bitmap?) {
        bitmap?.let { ImageSource.bitmap(it) }?.
        let { binding.photo.setImage(it) } ?: showPlaceholder()
    }

    private fun showPlaceholder() {
        cannotDisplayImageBinding.cannotDisplayImage.isVisible = true
        cannotDisplayImageBinding.cannotDisplayImageText.isVisible = true

        binding.photo.isVisible = false
        binding.saveAs.isVisible = false
        binding.cancel.isVisible = false
    }

    private fun showDefaultControls() {
        Timber.d("showDefaultControls() is called")
        cannotDisplayImageBinding.cannotDisplayImage.isVisible = false
        cannotDisplayImageBinding.cannotDisplayImageText.isVisible = false

        binding.photo.isVisible = true
        binding.saveAs.isVisible = true
        binding.cancel.isVisible = true
    }

    private fun openSaveImageDialog() {
        SaveImageDialogFragment().show(childFragmentManager, "SaveImageDialogFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.photo.recycle()
        Timber.d("onDestroyView() is called")
    }

    private fun requestPermission() {
        /*
        After launch() is called, the system permissions dialog appears.
        When the user makes a choice, the system asynchronously invokes your
        implementation of ActivityResultCallback
        */
        Timber.d("Permission to write into external storage is requested")
        activityResultLauncherRequestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun saveImage(imageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> {
                    // Use the API that requires the permission.
                    mainViewModel.saveImageToExternalStorage(bitmap, imageName)
                }
                PackageManager.PERMISSION_DENIED -> {
                    Timber.d("Permission is denied")
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Timber.d("Permission rationale is requested")
                        showDeniedPermissionShowRationale()
                    } else {
                        Timber.d("Permission rationale is not requested")
                        requestPermission()
                    }
                }
            }
        } else {
            mainViewModel.saveImageToExternalStorage(bitmap, imageName)
        }
    }

    override fun onSaveClick(imageName: String) {
        saveImage(imageName)
    }
}