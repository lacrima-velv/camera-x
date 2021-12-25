package com.lacrima.camerax.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.window.layout.WindowMetrics
import androidx.window.layout.WindowMetricsCalculator
import com.lacrima.camerax.*
import com.lacrima.camerax.MainViewModel.ScreenOrientation.*
import com.lacrima.camerax.R
import com.lacrima.camerax.camera.CameraFragment.ScreenOrientationPair.*
import com.lacrima.camerax.databinding.*
import com.lacrima.camerax.camera.permissiondialogs.DeniedPermissionCameraExplanation
import com.lacrima.camerax.camera.permissiondialogs.DeniedPermissionCameraShowRationaleFragment
import com.lacrima.camerax.camera.permissiondialogs.DeniedPermissionCameraShowRationaleFragment.DeniedCameraPermissionClickListener
import com.lacrima.camerax.utils.ANIMATION_FAST_MILLIS
import com.lacrima.camerax.utils.ANIMATION_SLOW_MILLIS
import com.lacrima.camerax.utils.Util.setUiWindowInsetsBottom
import com.lacrima.camerax.utils.Util.toPixels
import com.lacrima.camerax.utils.Util.afterMeasured
import com.lacrima.camerax.utils.Util.returnStatusBar
import com.lacrima.camerax.utils.Util.simulateClick
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment(),
    DeniedCameraPermissionClickListener {

    private lateinit var activityResultLauncherRequestPermission: ActivityResultLauncher<String>
    private var screenOrientationPair: ScreenOrientationPair = ScreenOrientation0to0
    private var imageCapture: ImageCapture? = null

    private lateinit var binding: FragmentCameraBinding
    private lateinit var noCameraPermissionBinding: NoCameraPermissionViewBinding
    private lateinit var progressViewBinding: ProgressViewBinding
    private lateinit var cameraUnexpectedErrorBinding: CameraUnexpectedErrorViewBinding

    private lateinit var mainViewModel: MainViewModel
    private lateinit var windowLayoutInfo: WindowMetrics
    private var displayId: Int = -1
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var camera: Camera? = null
    private var bitmap: Bitmap? = null
    private var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
    private var flippedHorizontally: Boolean = false
    private lateinit var cameraSharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var currentImageResourceFlashButton: Int = R.drawable.flash_auto_button_default
    // Used when detecting tap to focus
    private val mainLoopHandler = Handler(Looper.getMainLooper())

    private lateinit var broadcastManager: LocalBroadcastManager

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    binding.cameraCaptureButton.simulateClick()
                }
            }
        }
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    @SuppressLint("SourceLockedOrientationActivity")
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
                // Build UI controls
                updateCameraUi()
                setUpCamera()
            } else {
                /*
                Explain to the user that the feature is unavailable because the
                features requires a permission that the user has denied. At the
                same time, respect the user's decision.
                 */
                showExplanationOfPermissionRequiringNoOptions()
            }

        }
        // Lock screen orientation to portrait (only in this fragment)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Create Shared Preferences to store important values
        cameraSharedPreferences = requireActivity()
            .getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)

        // Set SharedPreferences.Editor
        editor = cameraSharedPreferences.edit()
    }

    /**
    Show dialog with explanation why the permission is necessary,
    but don't provide an option to request it again
     */
    private fun showExplanationOfPermissionRequiringNoOptions() {
        val dialog = DeniedPermissionCameraExplanation()
        dialog.show(childFragmentManager, "CameraExplanation")
    }

    /**
     * Show dialog with rationale and and a button to request the permission again
     */
    private fun showDeniedPermissionShowRationale() {
        val dialog = DeniedPermissionCameraShowRationaleFragment()
        dialog.show(childFragmentManager, "CameraRationale")
    }

    /**
     * Show placeholder when permission to access camera is not granted
     */
    private fun showNoAccess() {
        noCameraPermissionBinding.noCameraPermissionImage.isVisible = true
        noCameraPermissionBinding.noCameraPermissionText.isVisible = true

        cameraUnexpectedErrorBinding.cameraUnexpectedErrorText.isVisible = false
        cameraUnexpectedErrorBinding.cameraUnexpectedErrorImage.isVisible = false
        cameraUnexpectedErrorBinding.cameraErrorBackground.isVisible = false

        dontShowDefaultUi()
    }

    /**
     * Remove placeholder when permission to access camera is granted
     */
    private fun showAccess() {
        noCameraPermissionBinding.noCameraPermissionImage.isVisible = false
        noCameraPermissionBinding.noCameraPermissionText.isVisible = false

        binding.cameraCaptureButton.isVisible = true
        binding.cameraFlipButton.isVisible = true
        binding.cameraFlashOnOffButton.isVisible = true

        cameraUnexpectedErrorBinding.cameraUnexpectedErrorText.isVisible = false
        cameraUnexpectedErrorBinding.cameraUnexpectedErrorImage.isVisible = false
        cameraUnexpectedErrorBinding.cameraErrorBackground.isVisible = false
    }

    /**
    Override a method from interface used in dialog for permission rationale
    to request permission to storage by clicking Yes button
     */
    override fun onYesClick() {
        requestPermission()
    }

    /**
     * Show placeholder when unexpected error occurred with the camera
     */
    private fun showErrorPlaceholder(errorText: String) {
        cameraUnexpectedErrorBinding.cameraUnexpectedErrorText.isVisible = true
        cameraUnexpectedErrorBinding.cameraUnexpectedErrorImage.isVisible = true
        cameraUnexpectedErrorBinding.cameraErrorBackground.isVisible = true

        cameraUnexpectedErrorBinding.cameraUnexpectedErrorText.text = errorText

        noCameraPermissionBinding.noCameraPermissionImage.isVisible = false
        noCameraPermissionBinding.noCameraPermissionText.isVisible = false

        dontShowDefaultUi()
    }

    private fun dontShowDefaultUi() {
        binding.cameraCaptureButton.isVisible = false
        binding.cameraFlipButton.isVisible = false
        binding.cameraFlashOnOffButton.isVisible = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentCameraBinding.inflate(inflater, container, false)

        // We need to bind the root layout with our binder for external layout
        noCameraPermissionBinding = NoCameraPermissionViewBinding.bind(binding.root)

        progressViewBinding = ProgressViewBinding.bind(binding.root)

        cameraUnexpectedErrorBinding = CameraUnexpectedErrorViewBinding.bind(binding.root)

        // Set insets for buttons
        val marginBottomOfButtons = 24.toPixels

        setUiWindowInsetsBottom(binding.cameraFlashOnOffButton, marginBottomOfButtons)
        setUiWindowInsetsBottom(binding.cameraCaptureButton, marginBottomOfButtons)
        setUiWindowInsetsBottom(binding.cameraFlipButton, marginBottomOfButtons)

        if (cameraSharedPreferences.contains(APP_PREFERENCES_FLASH_MODE)) {
            flashMode = cameraSharedPreferences
                .getInt(APP_PREFERENCES_FLASH_MODE, ImageCapture.FLASH_MODE_AUTO)
        } // Else the default value is used

        // Use correct image resource for flash mode button
        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO ->
                binding.cameraFlashOnOffButton.setImageResource(R.drawable.flash_auto_button_default)
            ImageCapture.FLASH_MODE_ON ->
                binding.cameraFlashOnOffButton.setImageResource(R.drawable.flash_on_button_default)
            ImageCapture.FLASH_MODE_OFF ->
                binding.cameraFlashOnOffButton.setImageResource(R.drawable.flash_off_button_default)
        }

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            launch {
                mainViewModel.bitmap.collectLatest { originalBitmap ->
                    bitmap = originalBitmap
                }
            }
            launch {
                mainViewModel.photoState.collectLatest { photoState ->
                    when (photoState) {
                        MainViewModel.PhotoState.StateStarted -> {
                            // Show activity indicator
                            progressViewBinding.progressBar.isVisible = true
                        }
                        else -> {
                            // Don't show activity indicator
                            progressViewBinding.progressBar.isVisible = false
                        }
                    }
                }
            }
            launch {
                mainViewModel.screenOrientationState.collectLatest { screenOrientation ->

                    resetViewRotation(binding.cameraFlashOnOffButton)

                    if (screenOrientation.first == Portrait
                        && screenOrientation.second == Portrait
                    ) {
                        // The app was opened in portrait. Don't rotate buttons.
                        screenOrientationPair = ScreenOrientation0to0
                    } else if (screenOrientation.first == Landscape
                        && screenOrientation.second == Portrait
                    ){
                        screenOrientationPair = ScreenOrientation90to0

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_90_0,
                            R.drawable.animated_vector_flash_on_90_0,
                            R.drawable.animated_vector_flash_off_90_0,
                            R.drawable.animated_vector_flash_auto_90_0
                        )
                        Timber.d("Rotate buttons from 90 to 0")
                    } else if (screenOrientation.first == ReverseLandscape
                        && screenOrientation.second == Portrait
                    ){
                        screenOrientationPair = ScreenOrientation270to0

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_270_0,
                            R.drawable.animated_vector_flash_on_270_0,
                            R.drawable.animated_vector_flash_off_270_0,
                            R.drawable.animated_vector_flash_auto_270_0
                        )
                        Timber.d("Rotate buttons from 270 to 0")
                    } else if (screenOrientation.first == ReversePortrait
                        && screenOrientation.second == Landscape
                    ){
                        screenOrientationPair = ScreenOrientation180to90

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_180_90,
                            R.drawable.animated_vector_flash_on_180_90,
                            R.drawable.animated_vector_flash_off_180_90,
                            R.drawable.animated_vector_flash_auto_180_90
                        )
                        Timber.d("Rotate buttons from 180 to 90")
                    } else if (screenOrientation.first == Portrait
                        && screenOrientation.second == Landscape
                    ){
                        screenOrientationPair = ScreenOrientation0to90

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_0_90,
                            R.drawable.animated_vector_flash_on_0_90,
                            R.drawable.animated_vector_flash_off_0_90,
                            R.drawable.animated_vector_flash_auto_0_90
                        )
                        Timber.d("Rotate buttons from 0 to 90")
                    } else if (screenOrientation.first == Landscape
                        && screenOrientation.second == ReversePortrait
                    ){
                        Timber.d("Rotate buttons from 90 to 180")
                        screenOrientationPair = ScreenOrientation90to180

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_90_180,
                            R.drawable.animated_vector_flash_on_90_180,
                            R.drawable.animated_vector_flash_off_90_180,
                            R.drawable.animated_vector_flash_auto_90_180
                        )
                    } else if (screenOrientation.first == ReverseLandscape
                        && screenOrientation.second == ReversePortrait
                    ){
                        screenOrientationPair = ScreenOrientation270to180

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_270_180,
                            R.drawable.animated_vector_flash_on_270_180,
                            R.drawable.animated_vector_flash_off_270_180,
                            R.drawable.animated_vector_flash_auto_270_180
                        )
                        Timber.d("Rotate buttons from 270 to 180")
                    } else if (screenOrientation.first == Portrait
                        && screenOrientation.second == ReverseLandscape
                    ){
                        screenOrientationPair = ScreenOrientation0to270

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_0_270,
                            R.drawable.animated_vector_flash_on_0_270,
                            R.drawable.animated_vector_flash_off_0_270,
                            R.drawable.animated_vector_flash_auto_0_270
                        )
                        Timber.d("Rotate buttons from 0 to 270")
                    } else if (screenOrientation.first == ReversePortrait
                        && screenOrientation.second == ReverseLandscape
                    ){
                        screenOrientationPair = ScreenOrientation180to270

                        triggerButtonsAnimation(
                            R.drawable.animated_vector_switch_camera_180_270,
                            R.drawable.animated_vector_flash_on_180_270,
                            R.drawable.animated_vector_flash_off_180_270,
                            R.drawable.animated_vector_flash_auto_180_270
                        )
                        Timber.d("Rotate buttons from 180 to 270")
                    }
                }
            }
        }

        requireActivity().returnStatusBar()

        return binding.root
    }

    /**
     * Starts rotation animation of camera flip and flash mode buttons
     */
    private fun triggerButtonsAnimation(
        cameraFlipImage: Int,
        flashModeOnImage: Int,
        flashModeOffImage: Int,
        flashModeAutoImage:Int
    ) {
        startImageButtonRotationAnimation(binding.cameraFlipButton, cameraFlipImage)

        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO ->
                startImageButtonRotationAnimation(binding.cameraFlashOnOffButton, flashModeAutoImage)
            ImageCapture.FLASH_MODE_ON ->
                startImageButtonRotationAnimation(binding.cameraFlashOnOffButton, flashModeOnImage)
            ImageCapture.FLASH_MODE_OFF ->
                startImageButtonRotationAnimation(binding.cameraFlashOnOffButton, flashModeOffImage)
        }

    }

    /**
     * Starts rotation animation of a single button
     */
    private fun startImageButtonRotationAnimation(imageButton: ImageButton, animationResource: Int) {
        currentImageResourceFlashButton = animationResource

        imageButton.setImageResource(animationResource)

        val drawable = imageButton.drawable

        if (drawable is AnimatedVectorDrawable) {
            val animation: AnimatedVectorDrawable = drawable
            animation.start()
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        windowLayoutInfo = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(requireActivity())

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set the preferred implementation mode before starting the preview
        binding.viewFinder.implementationMode = PreviewView.ImplementationMode.PERFORMANCE

        // Wait for the views to be properly laid out
        binding.viewFinder.post {
            // Keep track of the display in which this view is attached
            displayId = binding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }

    }

    override fun onPause() {
        super.onPause()
        // Update SharedPreferences
        editor.apply {
            putInt(APP_PREFERENCES_LENS_FACING, lensFacing)
            putInt(APP_PREFERENCES_FLASH_MODE, flashMode)
            apply()
        }
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        Timber.d("updateCameraUi")
        when (checkSelfPermission(requireContext(), Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Use the API that requires the permission.
                showAccess()
                // Set up the listener for take photo button
                binding.cameraCaptureButton.setOnClickListener {
                    takePhoto()
                }

                // Setup for button used to switch cameras
                binding.cameraFlipButton.setOnClickListener {

                    lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                        //flippedHorizontally = false
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        //flippedHorizontally = true
                        CameraSelector.LENS_FACING_FRONT
                    }
                    // Re-bind use cases to update selected camera
                    bindCameraUseCases()
                }

                // Setup Flash mode
                binding.cameraFlashOnOffButton.setOnClickListener {
                    binding.cameraFlashOnOffButton.isEnabled = true
                    changeViewRotation(binding.cameraFlashOnOffButton)
                    Timber.d("screenOrientationPair is $screenOrientationPair")
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> {
                            when (screenOrientationPair) {
                                ScreenOrientation0to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.flash_off_button_default
                                        )
                                ScreenOrientation0to90 -> {
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_90_0
                                        )
                                }
                                ScreenOrientation0to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_0_270
                                        )
                                ScreenOrientation90to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_90_0
                                        )
                                ScreenOrientation90to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_90_180
                                        )
                                ScreenOrientation180to90 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_180_90
                                        )
                                ScreenOrientation180to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_180_270
                                        )
                                ScreenOrientation270to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_270_0
                                        )
                                ScreenOrientation270to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_off_270_180
                                        )
                            }
                            ImageCapture.FLASH_MODE_OFF
                        }
                        ImageCapture.FLASH_MODE_OFF -> {
                            when (screenOrientationPair) {
                                ScreenOrientation0to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.flash_on_button_default
                                        )
                                ScreenOrientation0to90 -> {
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_90_0
                                        )
                                }
                                ScreenOrientation0to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_0_270
                                        )
                                ScreenOrientation90to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_90_0
                                        )
                                ScreenOrientation90to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_90_180
                                        )
                                ScreenOrientation180to90 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_180_90
                                        )
                                ScreenOrientation180to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_180_270
                                        )
                                ScreenOrientation270to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_270_0
                                        )
                                ScreenOrientation270to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_on_270_180
                                        )
                            }
                            ImageCapture.FLASH_MODE_ON
                        }
                        else -> {
                            when (screenOrientationPair) {
                                ScreenOrientation0to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.flash_auto_button_default
                                        )
                                ScreenOrientation0to90 -> {
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_90_0
                                        )
                                }
                                ScreenOrientation0to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_0_270
                                        )
                                ScreenOrientation90to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_90_0
                                        )
                                ScreenOrientation90to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_90_180
                                        )
                                ScreenOrientation180to90 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_180_90
                                        )
                                ScreenOrientation180to270 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_180_270
                                        )
                                ScreenOrientation270to0 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_270_0
                                        )
                                ScreenOrientation270to180 ->
                                    binding.cameraFlashOnOffButton
                                        .setImageResource(
                                            R.drawable.animated_vector_flash_auto_270_180
                                        )
                            }

                            ImageCapture.FLASH_MODE_AUTO
                        }
                    }
                    bindCameraUseCases()
                }
            } PackageManager.PERMISSION_DENIED -> {
            Timber.d("Permission is denied")
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                showNoAccess()
                Timber.d("Permission rationale is requested")
                showDeniedPermissionShowRationale()
            } else {
                Timber.d("Permission rationale is not requested")
                showNoAccess()
                requestPermission()
            }
        }
        }
    }

    // Must change the view rotation, if it was tapped, when the orientation wasn't portrait
    private fun changeViewRotation(view: View) {
        when (mainViewModel.screenOrientationState.value.second) {
            Portrait -> view.rotation = 0.0f
            ReversePortrait -> view.rotation = 180.0f
            Landscape -> view.rotation = 90.0f
            ReverseLandscape -> view.rotation = 270.0f
        }
    }
    // The view's rotation must be reset before rotation animation
    private fun resetViewRotation(view: View) {
        view.rotation = 0.0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    @SuppressLint("RestrictedApi")
    private fun takePhoto() {
        // Check if the image should be flipped
        flippedHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT

        mainViewModel.makePhotoStateStarted()
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val executor = ContextCompat.getMainExecutor(requireActivity())

        /*
        Set up image capture listener, which is triggered after photo has been taken
        This method provides an in-memory buffer of the captured image
         */
        imageCapture.takePicture(executor, object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                Timber.d("Image is captured, but not saved")

                viewLifecycleOwner.lifecycleScope.launch {
                    mainViewModel.getBitmap(image, flippedHorizontally)
                    findNavController()
                        .navigate(CameraFragmentDirections
                            .actionCameraFragmentToPhotoFragment())
                    Timber.d("Ended photo")
                    mainViewModel.makePhotoStateEnded()

                }
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Timber.d("Photo capture failed: ${exception.message}")
                progressViewBinding.progressBar.isVisible = false
                Toast.makeText(
                    context,
                    getString(R.string.error_take_photo),
                    Toast.LENGTH_SHORT)
                    .show()
            }
        })

        // We can only change the foreground Drawable using API level 23+ API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Display flash animation to indicate that photo was captured
            binding.root.postDelayed({
                binding.root.foreground = ColorDrawable(Color.WHITE)
                binding.root.postDelayed(
                    { binding.root.foreground = null }, ANIMATION_FAST_MILLIS
                )
            }, ANIMATION_SLOW_MILLIS)
        }

    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    @SuppressLint("RestrictedApi")
    private fun setUpCamera() {
        when (checkSelfPermission(requireContext(), Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())

                // Disable Flash button if the flash unit is not available
                Timber.d("Is flash enabled: " +
                        "${imageCapture?.camera?.cameraInfo?.hasFlashUnit() != false}$")
                    binding.cameraFlashOnOffButton.isEnabled =
                        imageCapture?.camera?.cameraInfo?.hasFlashUnit() != false

                cameraProviderFuture.addListener({
                    // Used to bind the lifecycle of cameras to the lifecycle owner
                    cameraProvider = cameraProviderFuture.get()

                    lensFacing = if (cameraSharedPreferences.contains(APP_PREFERENCES_LENS_FACING)) {
                        cameraSharedPreferences
                            .getInt(APP_PREFERENCES_LENS_FACING, CameraSelector.LENS_FACING_BACK)
                    } else {
                        when {
                            hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                            hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                            else -> throw IllegalStateException("Back and front camera are unavailable")
                        }
                    }

                    // Enable or disable switching between cameras
                    updateCameraSwitchButton()

                    // Build and bind the camera use cases
                    bindCameraUseCases()

                }, ContextCompat.getMainExecutor(requireActivity()))
            }
            else -> Unit
        }

    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            binding.cameraFlipButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            binding.cameraFlipButton.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = windowLayoutInfo.bounds
        Timber.d("Screen metrics: ${metrics.width()} x ${metrics.height()}")

        val screenAspectRatio = aspectRatio(metrics.width(), metrics.height())
        Timber.d("Preview aspect ratio: $screenAspectRatio")

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        val previewBuilder = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)

        // ImageCapture
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(screenAspectRatio)
            .setFlashMode(flashMode)

        if (binding.viewFinder.display?.rotation != null) {
            val rotation = binding.viewFinder.display.rotation
            previewBuilder.setTargetRotation(rotation)
            imageCaptureBuilder.setTargetRotation(rotation)
        }

        val preview = previewBuilder.build()

        imageCapture = imageCaptureBuilder.build()

        // Unbind use cases before rebinding
        cameraProvider.unbindAll()

        try {
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture)
            setTapToFocus(camera!!)

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            observeCameraErrorStates(camera?.cameraInfo!!)
        } catch(exc: Exception) {
            Timber.d("Use case binding failed: $exc")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTapToFocus(camera: Camera) {
        binding.viewFinder.afterMeasured {
            binding.viewFinder.setOnTouchListener { _, motionEvent ->
                val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                    binding.viewFinder.width.toFloat(), binding.viewFinder.height.toFloat()
                )
                val autoFocusPoint = factory.createPoint(motionEvent.x, motionEvent.y)

                return@setOnTouchListener when (motionEvent.action) {
                    MotionEvent.ACTION_UP -> {
                        Timber.d("Action up")

                        setFocusPlaceAndVisibility(motionEvent.x, motionEvent.y)

                        // Cancel removeFocusResultAfterDelay(), when another point is tapped
                        mainLoopHandler.removeCallbacksAndMessages(null)
                        binding.focus.setImageResource(R.drawable.focus_started)
                        try {
                            val focusListenableFuture = camera.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    autoFocusPoint,
                                    FocusMeteringAction.FLAG_AF
                                ).apply {
                                    // Focus only when the user tap the preview
                                    disableAutoCancel()
                                }.build()
                            )
                            focusListenableFuture.addListener(
                                {
                                    var result: FocusMeteringResult? = null
                                    try {
                                        result = focusListenableFuture.get()
                                    } catch (exc: Exception) {
                                        Timber.d("${exc.message}")
                                    }
                                    //val result = focusListenableFuture.get()
                                    val isSuccessful = result?.isFocusSuccessful

                                    Timber.d("isSuccessful is $isSuccessful")

                                    if (isSuccessful == true) {
                                        binding.focus.setImageResource(R.drawable.focus_success)
                                        removeFocusResultAfterDelay()
                                    } else if (isSuccessful == false) {
                                        binding.focus.setImageResource(R.drawable.focus_fail)
                                        removeFocusResultAfterDelay()
                                    }
                                }, ContextCompat.getMainExecutor(requireActivity())
                            )

                        } catch (exc: CameraUnavailableException) {
                            Timber.d("${exc.message}")
                        }
                        true
                    }
                    MotionEvent.ACTION_DOWN -> {
                        Timber.d("Action down")
                        setFocusPlaceAndVisibility(motionEvent.x, motionEvent.y)
                        true
                    }
                    // Unhandled event
                    else -> false
                }
            }

        }
    }

    private fun removeFocusResultAfterDelay() {
        Timber.d("Focus is delayed")
        mainLoopHandler.postDelayed({
            binding.focus.setImageResource(R.drawable.focus_transparent)
        }, TAP_TO_FOCUS_DELAY)
    }

    private fun setFocusPlaceAndVisibility(x: Float, y: Float) {
        binding.focus.x = x
        binding.focus.y = y

        binding.focus.isVisible = !noCameraPermissionBinding.noCameraPermissionImage.isVisible
                && !cameraUnexpectedErrorBinding.cameraUnexpectedErrorImage.isVisible
                && lensFacing != CameraSelector.LENS_FACING_FRONT
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(requireActivity()) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = when (orientation) {
                    in 45 until 135 -> {
                        mainViewModel.updateScreenOrientation(ReverseLandscape)
                        Surface.ROTATION_270
                    }
                    in 135 until 225 -> {
                        mainViewModel.updateScreenOrientation(ReversePortrait)
                        Surface.ROTATION_180
                    }
                    in 225 until 315 -> {
                        mainViewModel.updateScreenOrientation(Landscape)
                        Surface.ROTATION_90
                    }
                    else -> {
                        mainViewModel.updateScreenOrientation(Portrait)
                        Surface.ROTATION_0
                    }
                }
                // Update the target rotation of imageCapture
                imageCapture?.targetRotation = rotation
            }
        }
    }

    override fun onStart() {
        super.onStart()
        /*
        If the permission is already granted, but placeholder for this case is still visible,
        probably the user has just granted the permission to access camera in system settings,
        which previously was rejected. In this case buttons will have no listeners and
        the default UI won't be visible.
         */
        if ( !binding.cameraCaptureButton.isVisible &&
            !cameraUnexpectedErrorBinding.cameraErrorBackground.isVisible &&
            checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            showAccess()
            Timber.d("Button flip hasOnClickListeners: " +
                    "${binding.cameraFlipButton.hasOnClickListeners()}")
            if (!binding.cameraFlipButton.hasOnClickListeners()) {
                updateCameraUi()
                setUpCamera()
            }
        }

        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }

    private fun observeCameraErrorStates(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.OPEN -> {
                        if (cameraUnexpectedErrorBinding.cameraUnexpectedErrorImage.isVisible
                            || noCameraPermissionBinding.noCameraPermissionImage.isVisible) {
                            // Remove any placeholders, if the camera is opened
                            showAccess()
                        }
                    }
                    else -> Unit
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        showErrorPlaceholder(getString(R.string.unexpected_error_text))
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        showErrorPlaceholder(getString(R.string.camera_in_use))
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        showErrorPlaceholder(getString(R.string.cameras_in_use))
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        showErrorPlaceholder("An unexpected error has occurred")
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        showErrorPlaceholder(getString(R.string.camera_disabled))
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        showErrorPlaceholder(getString(R.string.camera_fatal_error))
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        showErrorPlaceholder(getString(R.string.camera_do_not_disturb))
                    }
                }
            }
        }
    }

    private fun requestPermission() {
        /*
        After launch() is called, the system permissions dialog appears.
        When the user makes a choice, the system asynchronously invokes your
        implementation of ActivityResultCallback
        */
        Timber.d("Permission to camera is requested")
        activityResultLauncherRequestPermission.launch(Manifest.permission.CAMERA)
    }

    /**
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {
        private const val TAP_TO_FOCUS_DELAY = 1600L
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val APP_PREFERENCES_LENS_FACING = "LensFacing"
        private const val APP_PREFERENCES_FLASH_MODE = "FlashMode"
        private const val APP_PREFERENCES = "AppPreferences"
    }

    // Used to update flash button correctly
    sealed class ScreenOrientationPair {
        object ScreenOrientation0to0 : ScreenOrientationPair()
        object ScreenOrientation0to270 : ScreenOrientationPair()
        object ScreenOrientation0to90 : ScreenOrientationPair()
        object ScreenOrientation90to0 : ScreenOrientationPair()
        object ScreenOrientation90to180 : ScreenOrientationPair()
        object ScreenOrientation180to270 : ScreenOrientationPair()
        object ScreenOrientation180to90 : ScreenOrientationPair()
        object ScreenOrientation270to0 : ScreenOrientationPair()
        object ScreenOrientation270to180 : ScreenOrientationPair()
    }

}