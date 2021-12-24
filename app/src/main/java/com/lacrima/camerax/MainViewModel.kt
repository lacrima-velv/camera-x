package com.lacrima.camerax

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageProxy
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lacrima.camerax.utils.Util.flipHorizontally
import com.lacrima.camerax.utils.Util.rotate
import com.lacrima.camerax.camera.CameraFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application): AndroidViewModel(application) {
    private val _imageCaptured = MutableStateFlow<ImageProxy?>(null)
    val imageCaptured: StateFlow<ImageProxy?>
        get() = _imageCaptured

    fun setImageCaptured(imageProxy: ImageProxy) {
        _imageCaptured.value = imageProxy
    }

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?>
        get() = _bitmap

    private val _imageName = MutableStateFlow<String?>(null)
    val imageName: StateFlow<String?>
        get() = _imageName

    fun setImageName(name: String) {
        _imageName.value = name
    }

    private val _photoState = MutableStateFlow<PhotoState>(PhotoState.StateNotStarted)
    val photoState: StateFlow<PhotoState>
        get() = _photoState

    fun makePhotoStateStarted() {
        _photoState.value = PhotoState.StateStarted
    }

    fun makePhotoStateEnded() {
        _photoState.value = PhotoState.StateEnded
    }

    sealed class PhotoState {
        object StateNotStarted: PhotoState()
        object StateStarted: PhotoState()
        object StateEnded: PhotoState()
    }

    private val _screenOrientationState = MutableStateFlow<Pair<ScreenOrientation, ScreenOrientation>>(Pair(ScreenOrientation.Portrait, ScreenOrientation.Portrait))
    val screenOrientationState: StateFlow<Pair<ScreenOrientation, ScreenOrientation>>
        get() = _screenOrientationState

    fun updateScreenOrientation(newScreenOrientation: ScreenOrientation) {
        _screenOrientationState.value = Pair(_screenOrientationState.value.second, newScreenOrientation)
        Timber.d("_screenOrientationState.value.first is ${_screenOrientationState.value.first}" +
                "_screenOrientationState.value.second is ${_screenOrientationState.value.second}")
    }

    sealed class ScreenOrientation {
        object Portrait: ScreenOrientation()
        object Landscape: ScreenOrientation()
        object ReversePortrait: ScreenOrientation()
        object ReverseLandscape: ScreenOrientation()
    }

    init {
        viewModelScope.launch {
            imageCaptured.collectLatest { }
        }
    }

    suspend fun getBitmap(image: ImageProxy?, isFlippedHorizontally: Boolean = false) = withContext(Dispatchers.IO) {
        val imageProxy: ImageProxy? = image
        // Get the array of pixel planes for this Image
        val buffer = imageProxy?.planes?.get(0)?.buffer
        val bytes: ByteArray? = buffer?.capacity()?.let { ByteArray(it) }
        if (bytes != null) {
            buffer.get(bytes)
        }
        val bitmap = bytes?.size?.let { BitmapFactory.decodeByteArray(bytes, 0, it, null) }
            ?.rotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            ?.flipHorizontally(isFlippedHorizontally)

        Timber.d("isFlippedHorizontally: $isFlippedHorizontally")

        _bitmap.value = bitmap
        imageProxy?.close()
    }

    fun recycleBitmap() {
        _bitmap.value?.recycle()
    }

    suspend fun getBitmap() = withContext(Dispatchers.IO) {
        val imageProxy: ImageProxy? = imageCaptured.value
        Timber.d("imageProxy is $imageProxy")
        val buffer = imageProxy?.planes?.get(0)?.buffer
        val bytes: ByteArray? = buffer?.capacity()?.let { ByteArray(it) }
        if (bytes != null) {
            buffer.get(bytes)
        }
        val bitmap = bytes?.size?.let { BitmapFactory.decodeByteArray(bytes, 0, it, null) }
//            ?.rotate(
//            imageProxy?.imageInfo?.rotationDegrees?.toFloat() ?: 0f
//        )
        Timber.d("Bitmap is $bitmap")
        _bitmap.value = bitmap
        imageProxy?.close()
    }

    fun updateBitmapValue(bitmap: Bitmap?) {
        _bitmap.value = bitmap
    }

    fun saveImageToExternalStorage(
        bitmap: Bitmap?,
        imageName: String
    ) {
        // Add a specific media item.
        val resolver = getApplication<Application>().contentResolver
        // Find all images on the primary external storage device
        val imagesCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val imagesDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }
        }

        var imageContentUri: Uri? = null

        val format = Bitmap.CompressFormat.JPEG

        try {
            imageContentUri = resolver.insert(imagesCollection, imagesDetails) ?:
                    throw IOException("Failed to create new MediaStore record.")
            resolver.openOutputStream(imageContentUri)?.use {
                if (bitmap?.compress(format, 100, it) != true)
                    throw IOException("Failed to save bitmap.")
            } ?: throw IOException("Failed to open output stream.")
            Toast.makeText(getApplication<Application>().applicationContext, "Successfully saved an image", Toast.LENGTH_SHORT).show()
        } catch (exc: IOException) {
            imageContentUri?.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }
            throw exc
        }
    }

}