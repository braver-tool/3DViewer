package com.brave.modelviewer

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.brave.modelviewer.databinding.ActivityMainBinding
import com.brave.modelviewer.gvr.ModelGvrActivity
import com.brave.modelviewer.multiobjects.MultiObjectsModelSurfaceView
import com.brave.modelviewer.obj.ObjModel
import com.brave.modelviewer.ply.PlyModel
import com.brave.modelviewer.stl.StlModel
import com.brave.modelviewer.util.Util.closeSilently
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/*
* Copyright 2017 Dmitry Brant. All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sampleModels: List<String>
    private var sampleModelIndex = 0
    private var modelView: ModelSurfaceView? = null
    private var multiModelView: MultiObjectsModelSurfaceView? = null
    private val disposables = CompositeDisposable()

    //Multi-Objects
    private lateinit var sampleObjModels: List<String>
    private var sampleObjModelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressBar.visibility = View.GONE
        binding.actionButton.setOnClickListener { startVrActivity() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.containerView)) { _: View?, insets: WindowInsetsCompat ->
            (binding.actionButton.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = insets.systemWindowInsetTop
                bottomMargin = insets.systemWindowInsetBottom
                leftMargin = insets.systemWindowInsetLeft
                rightMargin = insets.systemWindowInsetRight
            }
            (binding.progressBar.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = insets.systemWindowInsetTop
                bottomMargin = insets.systemWindowInsetBottom
                leftMargin = insets.systemWindowInsetLeft
                rightMargin = insets.systemWindowInsetRight
            }
            insets.consumeSystemWindowInsets()
        }

        sampleModels = assets.list("")!!.filter { it.endsWith(".stl") }

        sampleObjModels = assets.list("")!!.filter { it.endsWith(".obj") }

        if (intent.data != null && savedInstanceState == null) {
            beginLoadModel(intent.data!!)
        }
    }

    override fun onStart() {
        super.onStart()
        createNewModelView(ModelViewerApplication.currentModel)
        if (ModelViewerApplication.currentModel != null) {
            title = ModelViewerApplication.currentModel!!.title
        }
    }

    override fun onPause() {
        super.onPause()
        modelView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        modelView?.onResume()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_open_model -> {
                checkReadPermissionThenOpen()
                true
            }
            R.id.menu_load_sample -> {
                loadSampleModel()
                true
            }
            R.id.menu_load_multiple -> {
                loadMultiObjectsModel()
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            READ_PERMISSION_REQUEST -> if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginOpenModel()
            } else {
                Toast.makeText(this, R.string.read_permission_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == RESULT_OK && resultData!!.data != null) {
            val uri = resultData.data
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            beginLoadModel(uri!!)
        }
    }

    private fun checkReadPermissionThenOpen() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    READ_PERMISSION_REQUEST)
        } else {
            beginOpenModel()
        }
    }

    private fun beginOpenModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST)
    }

    private fun createNewModelView(model: Model?) {
        if (modelView != null) {
            binding.containerView.removeView(modelView)
        }
        ModelViewerApplication.currentModel = model
        modelView = ModelSurfaceView(this, model)
        binding.containerView.addView(modelView, 0)
    }

    private fun createNewMultiModelView(model: List<Model>?) {
        if (modelView != null) {
            binding.containerView.removeView(modelView)
        }
        ModelViewerApplication.currentModelArray = model
        multiModelView = MultiObjectsModelSurfaceView(this, model)
        binding.containerView.addView(multiModelView, 0)
    }

    private fun beginLoadModel(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        disposables.add(Observable.fromCallable {
            var model: Model? = null
            var stream: InputStream? = null
            try {
                val cr = applicationContext.contentResolver
                val fileName = getFileName(cr, uri)
                stream = if ("http" == uri.scheme || "https" == uri.scheme) {
                    val client = OkHttpClient()
                    val request: Request = Request.Builder().url(uri.toString()).build()
                    val response = client.newCall(request).execute()

                    // TODO: figure out how to NOT need to read the whole file at once.
                    ByteArrayInputStream(response.body!!.bytes())
                } else {
                    cr.openInputStream(uri)
                }
                if (stream != null) {
                    if (!fileName.isNullOrEmpty()) {
                        model = when {
                            fileName.toLowerCase(Locale.ROOT).endsWith(".stl") -> {
                                StlModel(stream)
                            }
                            fileName.toLowerCase(Locale.ROOT).endsWith(".obj") -> {
                                ObjModel(stream)
                            }
                            fileName.toLowerCase(Locale.ROOT).endsWith(".ply") -> {
                                PlyModel(stream)
                            }
                            else -> {
                                // assume it's STL.
                                StlModel(stream)
                            }
                        }
                        model.title = fileName
                    } else {
                        // assume it's STL.
                        // TODO: autodetect file type by reading contents?
                        model = StlModel(stream)
                    }
                }
                model!!
            } finally {
                closeSilently(stream)
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate {
                    binding.progressBar.visibility = View.GONE
                }
                .subscribe({
                    setCurrentModel(it)
                }, {
                    it.printStackTrace()
                    Toast.makeText(applicationContext, getString(R.string.open_model_error, it.message), Toast.LENGTH_SHORT).show()
                }))
    }

    private fun getFileName(cr: ContentResolver, uri: Uri): String? {
        if ("content" == uri.scheme) {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            ContentResolverCompat.query(cr, uri, projection, null, null, null, null)?.use { metaCursor ->
                if (metaCursor.moveToFirst()) {
                    return metaCursor.getString(0)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun setCurrentModel(model: Model) {
        createNewModelView(model)
        Toast.makeText(applicationContext, R.string.open_model_success, Toast.LENGTH_SHORT).show()
        title = model.title
        binding.progressBar.visibility = View.GONE
    }

    private fun setCurrentMultiModel(model: List<Model>) {
        val model_title = "Multi Model"
        createNewMultiModelView(model)
        Toast.makeText(applicationContext, R.string.open_model_success, Toast.LENGTH_SHORT).show()
        title = model_title
        binding.progressBar.visibility = View.GONE
    }

    private fun startVrActivity() {
        if (ModelViewerApplication.currentModel == null) {
            Toast.makeText(this, R.string.view_vr_not_loaded, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(this, ModelGvrActivity::class.java))
        }
    }

    private fun loadSampleModel() {
        try {
            val stream = assets.open(sampleModels[sampleModelIndex++ % sampleModels.size])
            setCurrentModel(StlModel(stream))
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun loadMultiObjectsModel() {
        try {
            var model: Model?
            val modelArray: MutableList<Model> = ArrayList<Model>()
            for (i in sampleObjModels.indices) {
                val stream = assets.open(sampleObjModels.get(i))
                model = ObjModel(stream)
                modelArray.add(model)
                stream.close()
            }
            setCurrentMultiModel(modelArray)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    companion object {
        private const val READ_PERMISSION_REQUEST = 100
        private const val OPEN_DOCUMENT_REQUEST = 101
    }
}
