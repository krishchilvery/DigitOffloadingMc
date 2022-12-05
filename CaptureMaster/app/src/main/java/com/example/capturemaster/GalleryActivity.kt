package com.example.capturemaster

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.example.capturemaster.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var viewBinding: ActivityGalleryBinding
    private var digit = 0

    private fun getImages() {
        viewBinding.galleryView.removeAllViews()
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} == ?"
        val selectionArgs = arrayOf(
            "Pictures/${digit}/"
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val query = applicationContext.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val bmp = getBitmap(uri)
                val thumb = ThumbnailUtils.extractThumbnail(bmp, 300, 300)
                val imgView = ImageView(applicationContext)
                imgView.setImageBitmap(thumb)
                imgView.setOnClickListener {
                    viewBinding.previewImage.setImageBitmap(bmp)
                }
                viewBinding.galleryView.addView(imgView)
            }
        }
    }

    private fun getBitmap(uri: Uri): Bitmap? {
        return try {
            if(Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(
                    contentResolver,
                    uri
                )
            } else {
                val source = ImageDecoder.createSource(this.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        ArrayAdapter.createFromResource(
            this,
            R.array.digits,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            viewBinding.digitSelector.adapter = adapter
        }
        viewBinding.digitSelector.onItemSelectedListener = this
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        digit = pos
        getImages()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        digit = 0
    }
}