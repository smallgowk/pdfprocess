package com.example.pdfinsert

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfRenderFragment : Fragment(), View.OnClickListener {
    private val STATE_CURRENT_PAGE_INDEX = "current_page_index"

    /**
     * The filename of the PDF.
     */
    private val FILENAME = "sample.pdf"

    /**
     * File descriptor of the PDF.
     */
    private var mFileDescriptor: ParcelFileDescriptor? = null

    /**
     * [PdfRenderer] to render the PDF.
     */
    private var mPdfRenderer: PdfRenderer? = null

    /**
     * Page that is currently shown on the screen.
     */
    private var mCurrentPage: PdfRenderer.Page? = null

    /**
     * [ImageView] that shows a PDF page as a [Bitmap]
     */
    private var mImageView: ImageView? = null
    private var imageSigned: ImageView? = null

    /**
     * [Button] to move to the previous page.
     */
    private var mButtonPrevious: Button? = null

    /**
     * [Button] to move to the next page.
     */
    private var mButtonNext: Button? = null
    private var mButtonSave: Button? = null

    private var embeddedBitmap: Bitmap? = null

    /**
     * PDF page index
     */
    private var mPageIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pdf_renderer_basic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Retain view references.
        mImageView = view.findViewById<View>(R.id.image) as ImageView
        imageSigned = view.findViewById<View>(R.id.imageSigned) as ImageView
        mButtonPrevious = view.findViewById<View>(R.id.previous) as Button
        mButtonNext = view.findViewById<View>(R.id.next) as Button
        mButtonSave = view.findViewById<View>(R.id.save) as Button
        // Bind events.
        mButtonPrevious?.setOnClickListener(this)
        mButtonNext?.setOnClickListener(this)
        mButtonSave?.setOnClickListener(this)
        mPageIndex = 0
        // If there is a savedInstanceState (screen orientations, etc.), we restore the page index.
        if (null != savedInstanceState) {
            mPageIndex = savedInstanceState.getInt(STATE_CURRENT_PAGE_INDEX, 0)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            openRenderer(activity)
            embeddedBitmap = BitmapFactory.decodeStream(context?.assets?.open("ap1025_ic_person.png"))
            imageSigned?.setImageBitmap(embeddedBitmap)
            showPage(mPageIndex)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(activity, "Error! " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        try {
            closeRenderer()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mCurrentPage?.let {
            outState.putInt(STATE_CURRENT_PAGE_INDEX, it.getIndex())
        }
    }

    /**
     * Sets up a [PdfRenderer] and related resources.
     */
    @Throws(IOException::class)
    private fun openRenderer(context: Context?) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context!!.cacheDir, FILENAME)
        if (!file.exists()) {
            // Since PdfRenderer cannot handle the compressed asset file directly, we copy it into
            // the cache directory.
            val asset = context.assets.open(FILENAME)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // This is the PdfRenderer we use to render the PDF.
        mFileDescriptor?.let {
            mPdfRenderer = PdfRenderer(it)
        }
    }

    /**
     * Closes the [PdfRenderer] and related resources.
     *
     * @throws IOException When the PDF file cannot be closed.
     */
    @Throws(IOException::class)
    private fun closeRenderer() {
        mCurrentPage?.close()
        mPdfRenderer!!.close()
        mFileDescriptor!!.close()
    }

    /**
     * Shows the specified page of PDF to the screen.
     *
     * @param index The page index.
     */
    private fun showPage(index: Int) {
        if (mPdfRenderer!!.pageCount <= index) {
            return
        }
        // Make sure to close the current page before opening another one.
        if (null != mCurrentPage) {
            mCurrentPage?.close()
        }
        // Use `openPage` to open a specific page in PDF.
        mCurrentPage = mPdfRenderer!!.openPage(index)
        // Important: the destination bitmap must be ARGB (not RGB).
        val bitmap = Bitmap.createBitmap(
            mCurrentPage!!.getWidth(), mCurrentPage!!.getHeight(),
            Bitmap.Config.ARGB_8888
        )
        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get
        // the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        mCurrentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        // We are ready to show the Bitmap to user.
        mImageView!!.setImageBitmap(bitmap)
        updateUi()
    }

    private fun processPdf(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PDF_" + timeStamp + "_"
        val storageDir = getAlbumDir()
        try {
            val pdfPath = File.createTempFile(
                imageFileName,  /* prefix */
                ".pdf",  /* suffix */
                storageDir /* directory */
            )
            val pdfDocument = PdfDocument()
            val pageCount = mPdfRenderer?.pageCount ?: 0
            if (pageCount > 0) {
                for (i in 0 until pageCount) {
                    if (i == mPageIndex) {
                        buildPageInfo(i, embeddedBitmap, pdfDocument)
                    } else {
                        buildPageInfo(i, null, pdfDocument)
                    }
                }
            }
            convertToPDF(pdfPath, pdfDocument)
            return pdfPath

        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    private fun getAlbumDir(): File? {
        val dir = File(context?.getExternalFilesDir("DCIM")?.path + "/pdfinsert")
        if (!dir.mkdirs()) {
            if (!dir.exists()) {
                Log.d("CameraSample", "failed to create directory");
                return null;
            }
        }
        Log.e("Duyuno", "Duyuno dir: ${dir.path}")
        return dir
    }

    private fun convertToPDF(pdfPath: File, pdfDocument: PdfDocument) {
        try {
            pdfDocument.writeTo(FileOutputStream(pdfPath))
            Toast.makeText(context, "Image is successfully converted to PDF", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        pdfDocument.close()
    }

    private fun buildPageInfo(pageNumber: Int, signBitmap: Bitmap?, pdfDocument: PdfDocument) {
        if (null != mCurrentPage) {
            mCurrentPage?.close()
        }
        mCurrentPage = mPdfRenderer!!.openPage(pageNumber)
        val bitmap = Bitmap.createBitmap(
            mCurrentPage!!.getWidth(), mCurrentPage!!.getHeight(),
            Bitmap.Config.ARGB_8888
        )
        mCurrentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        val mergeBitmap = overlay(bitmap, signBitmap)

        val width: Int = mergeBitmap.getWidth()
        val height: Int = mergeBitmap.getHeight()

        val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageNumber + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        paint.setColor(Color.parseColor("#ffffff"))
        canvas.drawPaint(paint)
        // val newBitmap = Bitmap.createScaledBitmap(mergeBitmap, width, height, true)
        paint.setColor(Color.BLUE)
        canvas.drawBitmap(mergeBitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)
    }

    private fun overlay(bmp1: Bitmap, bmp2: Bitmap?): Bitmap {
        if (bmp2 == null) return bmp1
        val width = bmp1.width
        val height = bmp1.height
        val bmOverlay = Bitmap.createBitmap(width, height, bmp1.config)
        val canvas = Canvas(bmOverlay)
        canvas.drawBitmap(bmp1, Matrix(), null)
        canvas.drawBitmap(bmp2, (width - bmp2.width) * 1f, (height - bmp2.height) * 1f, null)
        bmp1.recycle()
        bmp2.recycle()
        return bmOverlay
    }

    /**
     * Updates the state of 2 control buttons in response to the current page index.
     */
    private fun updateUi() {
        val index = mCurrentPage!!.index
        val pageCount = mPdfRenderer!!.pageCount
        mButtonPrevious!!.isEnabled = 0 != index
        mButtonNext!!.isEnabled = index + 1 < pageCount
        activity!!.title = getString(R.string.app_name_with_index, index + 1, pageCount)
    }

    /**
     * Gets the number of pages in the PDF. This method is marked as public for testing.
     *
     * @return The number of pages.
     */
    fun getPageCount(): Int {
        return mPdfRenderer!!.pageCount
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.previous -> {

                // Move to the previous page
                showPage(mCurrentPage!!.index - 1)
            }
            R.id.next -> {

                // Move to the next page
                showPage(mCurrentPage!!.index + 1)
            }
            R.id.save -> processPdf()
        }
    }
}