package com.lacrima.camerax.photo

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lacrima.camerax.MainViewModel
import com.lacrima.camerax.databinding.FragmentSaveImageDialogBinding
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

class SaveImageDialogFragment : DialogFragment() {

    private lateinit var binding: FragmentSaveImageDialogBinding
    private lateinit var listenerSaveImage: DialogSaveImageClickListener
    private lateinit var mainViewModel: MainViewModel

    /*
    Parent fragment must implement this interface to set click listener on the button to save
    the image to external storage
     */
    interface DialogSaveImageClickListener {
        fun onSaveClick(imageName:String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listenerSaveImage = parentFragment as DialogSaveImageClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DialogClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSaveImageDialogBinding.inflate(inflater, container, false)

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        binding.cancelButton.setOnClickListener {
            this.dismiss()
        }

        // Initially disable Save button if there's no image name entered
        binding.saveButton.isEnabled = binding.enterNameInput.text?.isNotEmpty() == true

        binding.enterNameInput
            .addTextChangedListener(textWatcherForInputImageNameField(binding.saveButton))

        binding.saveButton.setOnClickListener {
            if (binding.enterNameInput.text?.isNotEmpty() == true) {
                /*
                Also save its name to view model, so the user will be able to save the image with
                this name after permission denial and then changing his mind
                 */
                mainViewModel.setImageName(binding.enterNameInput.text.toString())
                listenerSaveImage.onSaveClick(binding.enterNameInput.text.toString())
                this.dismiss()
            }
        }

        return binding.root
    }

    private fun textWatcherForInputImageNameField(view: View) = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            Timber.d("beforeTextChanged() is called. charSequence is $charSequence")
            view.isEnabled = charSequence != null && charSequence.isNotEmpty()
        }

        override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            Timber.d("onTextChanged() is called. charSequence is $charSequence")
            view.isEnabled = charSequence != null && charSequence.isNotEmpty()
        }

        override fun afterTextChanged(p0: Editable?) { }
    }
}