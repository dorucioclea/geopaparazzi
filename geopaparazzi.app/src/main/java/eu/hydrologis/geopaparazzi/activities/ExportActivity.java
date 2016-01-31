/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.hydrologis.geopaparazzi.activities;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeSet;

import eu.geopaparazzi.library.core.ResourcesManager;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.database.Image;
import eu.geopaparazzi.library.kml.KmlRepresenter;
import eu.geopaparazzi.library.kml.KmzExport;
import eu.geopaparazzi.library.network.NetworkUtilities;
import eu.geopaparazzi.library.util.FileUtilities;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.StringAsyncTask;
import eu.geopaparazzi.library.util.TimeUtilities;
import eu.geopaparazzi.library.webproject.WebProjectManager;
import eu.hydrologis.geopaparazzi.GeopaparazziApplication;
import eu.hydrologis.geopaparazzi.R;
import eu.hydrologis.geopaparazzi.database.DaoBookmarks;
import eu.hydrologis.geopaparazzi.database.DaoGpsLog;
import eu.hydrologis.geopaparazzi.database.DaoImages;
import eu.hydrologis.geopaparazzi.database.DaoNotes;
import eu.hydrologis.geopaparazzi.database.objects.Bookmark;
import eu.hydrologis.geopaparazzi.database.objects.Line;
import eu.hydrologis.geopaparazzi.database.objects.LogMapItem;
import eu.hydrologis.geopaparazzi.database.objects.Note;
import eu.hydrologis.geopaparazzi.dialogs.GpxExportDialogFragment;
import eu.hydrologis.geopaparazzi.dialogs.KmzExportDialogFragment;
import eu.hydrologis.geopaparazzi.utilities.Constants;

/**
 * Activity for export tasks.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class ExportActivity extends AppCompatActivity implements
        NfcAdapter.CreateBeamUrisCallback {

    public static final String NODATA = "NODATA";
    private ProgressDialog cloudProgressDialog;
    private NfcAdapter mNfcAdapter;

    // List of URIs to provide to Android Beam
    private Uri[] mFileUris = new Uri[1];
    private PendingIntent pendingIntent;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_export);

        Toolbar toolbar = (Toolbar) findViewById(eu.hydrologis.geopaparazzi.R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);


        try {
            checkNfc();
        } catch (Exception e) {
            GPLog.error(this, e.getLocalizedMessage(), e);
        }

        Button kmzExportButton = (Button) findViewById(R.id.kmzExportButton);
        kmzExportButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                KmzExportDialogFragment kmzExportDialogFragment = KmzExportDialogFragment.newInstance(null);
                kmzExportDialogFragment.show(getSupportFragmentManager(), "kmz export");
            }
        });
        Button gpxExportButton = (Button) findViewById(R.id.gpxExportButton);
        gpxExportButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                GpxExportDialogFragment gpxExportDialogFragment = GpxExportDialogFragment.newInstance(null);
                gpxExportDialogFragment.show(getSupportFragmentManager(), "gpx export");
            }
        });
        Button bookmarksExportButton = (Button) findViewById(R.id.bookmarksExportButton);
        bookmarksExportButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                exportBookmarks();
            }
        });
        Button cloudExportButton = (Button) findViewById(R.id.cloudExportButton);
        cloudExportButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                final ExportActivity context = ExportActivity.this;
                if (!NetworkUtilities.isNetworkAvailable(context)) {
                    GPDialogs.infoDialog(context, context.getString(R.string.available_only_with_network), null);
                    return;
                }

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                final String user = preferences.getString(Constants.PREF_KEY_USER, "geopaparazziuser"); //$NON-NLS-1$
                final String pwd = preferences.getString(Constants.PREF_KEY_PWD, "geopaparazzipwd"); //$NON-NLS-1$
                final String serverUrl = preferences.getString(Constants.PREF_KEY_SERVER, ""); //$NON-NLS-1$
                if (serverUrl.length() == 0) {
                    GPDialogs.infoDialog(context, getString(R.string.error_set_cloud_settings), null);
                    return;
                }

                exportToCloud(context, serverUrl, user, pwd);

            }
        });

        Button imagesExportButton = (Button) findViewById(R.id.imagesExportButton);
        imagesExportButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                exportImages();
            }
        });
    }

    private void checkNfc() throws Exception {
        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null) {
            if (Build.VERSION.SDK_INT >
                    Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mNfcAdapter.setBeamPushUrisCallback(this, this);
                File databaseFile = ResourcesManager.getInstance(this).getDatabaseFile();
                mFileUris[0] = Uri.fromFile(databaseFile);
            } else {
                mNfcAdapter = null;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);

        GPDialogs.dismissProgressDialog(cloudProgressDialog);
    }


    private void exportToCloud(final ExportActivity context, final String serverUrl, final String user, final String pwd) {

        cloudProgressDialog = ProgressDialog.show(ExportActivity.this, getString(R.string.exporting_data),
                context.getString(R.string.exporting_data_to_the_cloud), true, true);
        new AsyncTask<String, Void, String>() {
            protected String doInBackground(String... params) {
                try {
                    String message = WebProjectManager.INSTANCE.uploadProject(context, serverUrl, user, pwd);
                    return message;
                } catch (Exception e) {
                    GPLog.error(this, e.getLocalizedMessage(), e);
                    return e.getLocalizedMessage();
                }
            }

            protected void onPostExecute(String response) { // on UI thread!
                GPDialogs.dismissProgressDialog(cloudProgressDialog);
                // String msg;
                // if (code == ReturnCodes.ERROR) {
                // msg = getString(R.string.error_uploadig_project_to_cloud);
                // } else {
                // msg = getString(R.string.project_succesfully_uploaded_to_cloud);
                // }

                GPDialogs.infoDialog(ExportActivity.this, response, null);
            }
        }.execute((String) null);

    }


    @SuppressWarnings("nls")
    private void exportBookmarks() {

        try {
            List<Bookmark> allBookmarks = DaoBookmarks.getAllBookmarks();
            TreeSet<String> bookmarksNames = new TreeSet<String>();
            for (Bookmark bookmark : allBookmarks) {
                String tmpName = bookmark.getName();
                bookmarksNames.add(tmpName.trim());
            }

            List<String> namesToNOTAdd = new ArrayList<String>();
            ResourcesManager resourcesManager = ResourcesManager.getInstance(this);
            File sdcardDir = resourcesManager.getSdcardDir();
            File bookmarksfile = new File(sdcardDir, "bookmarks.csv"); //$NON-NLS-1$
            StringBuilder sb = new StringBuilder();
            if (bookmarksfile.exists()) {
                List<String> bookmarksList = FileUtilities.readfileToList(bookmarksfile);
                for (String bookmarkLine : bookmarksList) {
                    String[] split = bookmarkLine.split(","); //$NON-NLS-1$
                    // bookmarks are of type: Agritur BeB In Valle, 45.46564, 11.58969, 12
                    if (split.length < 3) {
                        continue;
                    }
                    String name = split[0].trim();
                    if (bookmarksNames.contains(name)) {
                        namesToNOTAdd.add(name);
                    }
                }
                for (String string : bookmarksList) {
                    sb.append(string).append("\n");
                }
            }
            int exported = 0;
            for (Bookmark bookmark : allBookmarks) {
                String name = bookmark.getName().trim();
                if (!namesToNOTAdd.contains(name)) {
                    sb.append(name);
                    sb.append(",");
                    sb.append(bookmark.getLat());
                    sb.append(",");
                    sb.append(bookmark.getLon());
                    sb.append(",");
                    sb.append(bookmark.getZoom());
                    sb.append("\n");
                    exported++;
                }
            }

            FileUtilities.writefile(sb.toString(), bookmarksfile);
            if (bookmarksfile.exists()) {
                GPDialogs.infoDialog(this, "New bookmarks added to existing file: " + exported, null);
            } else {
                GPDialogs.infoDialog(this, "Successfully exported bookmarks: " + exported, null);
            }
        } catch (Exception e) {
            GPLog.error(this, null, e);
            GPDialogs.warningDialog(this, "An error occurred while exporting the bookmarks.", null);
        }

    }

    private void exportImages() {
        try {
            File sdcardDir = ResourcesManager.getInstance(GeopaparazziApplication.getInstance()).getSdcardDir();
            final File outFolder = new File(sdcardDir, "geopaparazzi_images_" + TimeUtilities.INSTANCE.TIMESTAMPFORMATTER_LOCAL.format(new Date()));
            if (!outFolder.mkdir()) {
                GPDialogs.warningDialog(this, getString(R.string.export_img_unable_to_create_folder) + outFolder, null);
                return;
            }
            final List<Image> imagesList = DaoImages.getImagesList(false, false);
            if (imagesList.size() == 0) {
                GPDialogs.infoDialog(this, getString(R.string.no_images_in_project), null);
                return;
            }


            final DaoImages imageHelper = new DaoImages();
            StringAsyncTask task = new StringAsyncTask(this) {
                protected String doBackgroundWork() {
                    try {
                        for (int i = 0; i < imagesList.size(); i++) {
                            Image image = imagesList.get(i);
                            try {
                                byte[] imageData = imageHelper.getImageData(image.getId());
                                File imageFile = new File(outFolder, image.getName());

                                FileOutputStream fos = new FileOutputStream(imageFile);
                                fos.write(imageData);
                                fos.close();
                            } catch (IOException e) {
                                GPLog.error(this, "For file: " + image.getName(), e);
                            } finally {
                                publishProgress(i);
                            }
                        }
                    } catch (Exception e) {
                        return "ERROR: " + e.getLocalizedMessage();
                    }
                    return "";
                }

                protected void doUiPostWork(String response) {
                    dispose();
                    if (response.length() != 0) {
                        GPDialogs.warningDialog(ExportActivity.this, response, null);
                    } else {
                        GPDialogs.infoDialog(ExportActivity.this, getString(R.string.export_img_ok_exported) + outFolder, null);
                    }
                }
            };
            task.startProgressDialog(getString(R.string.export_uc), getString(R.string.export_img_processing), false, imagesList.size());
            task.execute();


        } catch (Exception e) {
            GPLog.error(this, null, e);
            GPDialogs.errorDialog(this, e, null);
        }
    }

    @Override
    public Uri[] createBeamUris(NfcEvent nfcEvent) {
        GPLog.addLogEntry(this, "URI SENT: " + mFileUris[0]);
        return mFileUris;
    }

    public void onResume() {
        super.onResume();

        if (mNfcAdapter != null)
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);

        // Check to see that the Activity started due to an Android Beam
        String action = getIntent().getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            GPLog.addLogEntry(this, "Incoming NFC event.");
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    void processIntent(Intent intent) {
        Uri beamUri = intent.getData();
        String path = beamUri.getPath();
        GPLog.addLogEntry(this, "Incoming URI path: " + path);
        if (TextUtils.equals(beamUri.getScheme(), "file") &&
                path.endsWith("gpap")) {
            System.out.println(path);
            File pathFile = new File(path);
            boolean exists = pathFile.exists();
            System.out.println(exists);
        }
    }

}
