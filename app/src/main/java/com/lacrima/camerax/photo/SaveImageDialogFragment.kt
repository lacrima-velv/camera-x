package com.lacrima.camerax.photo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.lacrima.camerax.MainViewModel
import com.lacrima.camerax.R

class SaveImageDialogFragment : DialogFragment() {

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

    private fun textWatcherForInputImageNameField(view: View) = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            view.isEnabled = charSequence != null && charSequence.isNotEmpty()
        }

        override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
            view.isEnabled = charSequence != null && charSequence.isNotEmpty()
        }

        override fun afterTextChanged(p0: Editable?) { }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_save_image_dialog, null)
        builder.setView(view)

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val saveButton = view.findViewById<Button>(R.id.save_button)
        val cancelButton = view.findViewById<Button>(R.id.cancel_button)
        val enterNameInput = view.findViewById<TextInputEditText>(R.id.enter_name_input)

        cancelButton.setOnClickListener {
            this.dismiss()
        }

        // Initially disable Save button if there's no image name entered
        saveButton.isEnabled = enterNameInput.text?.isNotEmpty() == true

        enterNameInput.addTextChangedListener(textWatcherForInputImageNameField(saveButton))

        saveButton.setOnClickListener {
            if (enterNameInput.text?.isNotEmpty() == true) {
                /*
                Also save its name to view model, so the user will be able to save the image with
                this name after permission denial and then changing his mind
                 */
                mainViewModel.setImageName(enterNameInput.text.toString())
                listenerSaveImage.onSaveClick(enterNameInput.text.toString())
                this.dismiss()
            }
        }

        return builder.create()
    }

}