// MainActivity.java
// Hosts the GeopaparazziActivityFragment on a phone and both the
// GeopaparazziActivityFragment and SettingsActivityFragment on a tablet
package eu.hydrologis.geopaparazzi;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;

import eu.geopaparazzi.library.gps.GpsServiceUtilities;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.hydrologis.geopaparazzi.core.IApplicationChangeListener;
import eu.hydrologis.geopaparazzi.fragments.GeopaparazziActivityFragment;
import eu.hydrologis.geopaparazzi.utilities.IChainedPermissionHelper;
import eu.hydrologis.geopaparazzi.utilities.PermissionWriteStorage;

/**
 * Main activity.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class GeopaparazziActivity extends AppCompatActivity implements IApplicationChangeListener {
    private IChainedPermissionHelper permissionHelper = new PermissionWriteStorage();
    private GeopaparazziActivityFragment geopaparazziActivityFragment;

    // configure the GeopaparazziActivity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_geopaparazzi);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // PERMISSIONS START
        if (permissionHelper.hasPermission(this) && permissionHelper.getNextWithoutPermission(this) == null) {
            init();
        } else {
            if (permissionHelper.hasPermission(this)) {
                permissionHelper = permissionHelper.getNextWithoutPermission(this);
            }
            permissionHelper.requestPermission(this);
        }
        // PERMISSIONS STOP

    }

    private void init() {

        // set default values in the app's SharedPreferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        geopaparazziActivityFragment = new GeopaparazziActivityFragment();
        FragmentTransaction transaction =
                getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, geopaparazziActivityFragment);
        transaction.commitAllowingStateLoss();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (permissionHelper.hasGainedPermission(requestCode, grantResults)) {
            IChainedPermissionHelper nextWithoutPermission = permissionHelper.getNextWithoutPermission(this);
            permissionHelper = nextWithoutPermission;
            if (permissionHelper == null) {
                init();
            } else {
                permissionHelper.requestPermission(this);
            }

        } else {
            GPDialogs.infoDialog(this, "Geopaparazzi can't be started because the following permission was not granted: " + permissionHelper.getDescription(), new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }

    // called after onCreate completes execution
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void finish() {
        try {
            GpsServiceUtilities.stopDatabaseLogging(this);
            GpsServiceUtilities.stopGpsService(this);
        } finally {
            super.finish();
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // force to exit through the exit button
        // System.out.println(keyCode + "/" + KeyEvent.KEYCODE_BACK);
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onApplicationNeedsRestart() {

        if (geopaparazziActivityFragment != null && geopaparazziActivityFragment.getGpsServiceBroadcastReceiver() != null) {
            GpsServiceUtilities.stopDatabaseLogging(this);
            GpsServiceUtilities.stopGpsService(this);
            GpsServiceUtilities.unregisterFromBroadcasts(this, geopaparazziActivityFragment.getGpsServiceBroadcastReceiver());
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 11) {
                    recreate();
                } else {
                    Intent intent = getIntent();
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    finish();
                    overridePendingTransition(0, 0);

                    startActivity(intent);
                    overridePendingTransition(0, 0);
                }
            }
        }, 10);
    }

}