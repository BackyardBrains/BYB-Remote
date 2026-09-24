package com.backyardbrains.bybremote;

import static com.backyardbrains.bybremote.utils.LogUtils.LOGD;
import static com.backyardbrains.bybremote.utils.LogUtils.LOGE;
import static com.backyardbrains.bybremote.utils.LogUtils.makeLogTag;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.backyardbrains.bybremote.utils.BluetoothUtils;

public class MainActivity extends AppCompatActivity implements RemoteManagerCallbacks {

    final static String TAG = makeLogTag(MainActivity.class);

    private static final long SCANNING_TIMEOUT = 4 * 1000; /* 4 seconds */

    private static final int SCREEN_MAIN = 0;
    private static final int SCREEN_SETTINGS = 1;

    boolean mScanning = false;
    boolean mTurning = false;
    boolean mOnSettingsScreen = false;

    /* guards so onResume() doesn't stack system dialogs while one is already showing */
    boolean mAskingPermission = false;
    boolean mAskingBluetooth = false;
    boolean mPermissionsDenied = false;

    String mDeviceAddress;

    Handler mHandler = new Handler(Looper.getMainLooper());
    RemoteManager mRemoteManager = null;
    ViewHolder viewHolder;
    Runnable mGATTUpdate;
    int mGATTFreq = 0;

    private GestureDetector gestureDetector;
    private OnBackPressedCallback mSettingsBackCallback;

    private final Runnable mConnectToNearest = new Runnable() {
        @Override public void run() {
            connectToNearestBtDevice();
        }
    };

    private final ActivityResultLauncher<String[]> mPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            mAskingPermission = false;
            if (BluetoothUtils.hasPermissions(this)) {
                ensureBluetoothOn();
            } else {
                LOGD(TAG, "Bluetooth permissions denied");
                mPermissionsDenied = true;
                showPermissionDialog();
            }
            invalidateOptionsMenu();
        });

    private final ActivityResultLauncher<Intent> mEnableBtLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            mAskingBluetooth = false;
            // check if user agreed to enable BT.
            if (result.getResultCode() != Activity.RESULT_OK) btDisabled();
        });

    @Override protected void onCreate(Bundle savedInstanceState) {
        // Toolbar is dark, so the status bar icons must be light.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        LOGD(TAG, "onCreate()");

        setContentView(R.layout.activity_main);

        mRemoteManager = new RemoteManager(this, this);

        // check if we have BT and BLE on board
        if (!BluetoothUtils.checkBleHardwareAvailable(this)) {
            bleMissing();
            return;
        }

        viewHolder = new ViewHolder();
        viewHolder.bind(this);

        // Android 15+ draws behind the system bars: pad the toolbar and content so nothing hides under them.
        ViewCompat.setOnApplyWindowInsetsListener(viewHolder.root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            viewHolder.appBar.setPadding(bars.left, bars.top, bars.right, 0);
            viewHolder.flipper.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // set toolbar as actionbar
        setSupportActionBar(viewHolder.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        viewHolder.boardImage.setVisibility(View.VISIBLE);
        viewHolder.boardConnectedImage.setVisibility(View.INVISIBLE);
        viewHolder.goLeftText.setVisibility(View.INVISIBLE);
        viewHolder.goRightText.setVisibility(View.INVISIBLE);

        viewHolder.Frequecy.setMax(150);
        viewHolder.Frequecy.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                mGATTFreq = seekBar.getProgress();
                if (mGATTFreq < 1) mGATTFreq = 1;

                //If the new freq it's greater than 1/2 the
                if ((float) mRemoteManager.getRemotePulseWidth() > (float) 500 / mGATTFreq) {
                    mGATTUpdate = new Runnable() {
                        @Override public void run() {
                            float newFreq = (float) (1000 / mGATTFreq);
                            int newPW = (int) newFreq / 2;
                            mRemoteManager.updatePulseWidth(newPW);
                            viewHolder.configText.setText(mRemoteManager.getConfigurationString());
                        }
                    };
                    mHandler.postDelayed(mGATTUpdate, 500);
                }

                viewHolder.PulseWidth.setMax(1000 / mGATTFreq);
                mRemoteManager.updateFrequency(mGATTFreq);
                viewHolder.configText.setText(mRemoteManager.getConfigurationString());
            }
        });
        viewHolder.Gain.setMax(100);
        viewHolder.Gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                float roundedGain = Math.round((float) seekBar.getProgress() / 5.0f) * 5.0f;
                mRemoteManager.updateGain((int) roundedGain);
                viewHolder.configText.setText(mRemoteManager.getConfigurationString());
            }
        });

        viewHolder.RandomMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mRemoteManager.isConnected()) {
                    mRemoteManager.updateRandomMode(isChecked);
                    viewHolder.PulseWidth.setEnabled(!viewHolder.RandomMode.isChecked());
                    viewHolder.Frequecy.setEnabled(!viewHolder.RandomMode.isChecked());
                    viewHolder.configText.setText(mRemoteManager.getConfigurationString());
                }
            }
        });

        viewHolder.PulseWidth.setMax(50);
        viewHolder.PulseWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                mRemoteManager.updatePulseWidth(seekBar.getProgress());
                viewHolder.configText.setText(mRemoteManager.getConfigurationString());
            }
        });

        viewHolder.Duration.setMax(1000);
        viewHolder.Duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                float roundedDuration = Math.round((float) seekBar.getProgress() / 10.0f) * 10.0f;
                //if ( roundedDuration < 10 ) roundedDuration = 10;

                mRemoteManager.updateDuration((int) roundedDuration);
                viewHolder.configText.setText(mRemoteManager.getConfigurationString());
            }
        });

        viewHolder.configText.setText("");

        final Button button = findViewById(R.id.btnSaveSettings);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LOGD(TAG, "Updating signal generator settings");
                mRemoteManager.updateFrequency(viewHolder.Frequecy.getProgress());

                // Perform action on click
                showMainScreen();
            }
        });

        // Back from the settings screen returns to the main screen instead of closing the app.
        mSettingsBackCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                showMainScreen();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mSettingsBackCallback);

        gestureDetector = new GestureDetector(this, new SwipeGestureDetector());
    }

    @Override protected void onResume() {
        super.onResume();
        LOGD(TAG, "onResume()");
        if (isFinishing()) return;

        // if RemoteManager cannot be initialized we should leave
        if (!mRemoteManager.initialize()) {
            finish();
            return;
        }

        // on every resume check permissions and that BT is enabled
        // (user could turn it off while app was in background etc.)
        checkPrerequisites();

        invalidateOptionsMenu();
    }

    @Override protected void onPause() {
        super.onPause();
        LOGD(TAG, "onPause()");
        if (viewHolder == null) return;

        if (mRemoteManager.isConnected()) {
            mRemoteManager.disconnect();
            mRemoteManager.close();
            // close() stops callbacks, so reset the "connected" look ourselves
            showDisconnected();
            invalidateOptionsMenu();
        } else if (mScanning) {
            stopLeScan();
        }
    }

    @Override protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mRemoteManager != null) mRemoteManager.close();
        super.onDestroy();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (mRemoteManager.isConnected()) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            menu.findItem(R.id.menu_settings).setVisible(true);
        } else {
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            menu.findItem(R.id.menu_settings).setVisible(false);

            if (mScanning) {
                menu.findItem(R.id.menu_stop).setVisible(true);
                menu.findItem(R.id.menu_scan).setVisible(false);
                menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_progress);
            } else {
                menu.findItem(R.id.menu_stop).setVisible(false);
                menu.findItem(R.id.menu_scan).setVisible(true);
                menu.findItem(R.id.menu_refresh).setActionView(null);
            }
        }

        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        final int id = item.getItemId();
        if (id == R.id.menu_scan) {
            // start LE scan
            startLeScan();
        } else if (id == R.id.menu_stop) {
            // stop LE scan
            stopLeScan();
        } else if (id == R.id.menu_disconnect) {
            disconnect();
        } else if (id == R.id.menu_settings) {
            showSettingsScreen();
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return (gestureDetector != null && gestureDetector.onTouchEvent(event)) || super.onTouchEvent(event);
    }

    @Override public void uiDeviceConnected(final BluetoothDevice device) {
        LOGD(TAG, "uiDeviceConnected()");
        runOnUiThread(new Runnable() {
            @Override public void run() {
                viewHolder.boardConnectedImage.setImageAlpha(150);
                viewHolder.boardConnectedImage.setVisibility(View.VISIBLE);
                invalidateOptionsMenu();
            }
        });
    }

    @Override public void uiDeviceDisconnected(final BluetoothDevice device) {
        LOGD(TAG, "uiDeviceDisconnected()");

        // let's reset strongest signal and last connected device address
        mStrongestSignal = Integer.MIN_VALUE;
        mDeviceAddress = null;

        runOnUiThread(new Runnable() {
            @Override public void run() {
                showDisconnected();
                invalidateOptionsMenu();
            }
        });
    }

    @Override public void uiServicesFound() {
        mRemoteManager.requestRemoteParameters();
    }

    @Override public void uiRemotePropertiesUpdated() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                viewHolder.configText.setText(mRemoteManager.getConfigurationString());
                viewHolder.boardConnectedImage.setImageAlpha(255);
                viewHolder.boardConnectedImage.setVisibility(View.VISIBLE);

                viewHolder.Frequecy.setProgress(mRemoteManager.getRemoteFrequency());
                viewHolder.Gain.setProgress(mRemoteManager.getRemoteGain());
                viewHolder.PulseWidth.setProgress(mRemoteManager.getRemotePulseWidth());
                viewHolder.Duration.setProgress(mRemoteManager.getRemoteDuration());
                viewHolder.RandomMode.setChecked(mRemoteManager.getRemoteRandomMode());

                viewHolder.PulseWidth.setEnabled(!viewHolder.RandomMode.isChecked());
                viewHolder.Frequecy.setEnabled(!viewHolder.RandomMode.isChecked());

                invalidateOptionsMenu();
            }
        });
    }

    private int mStrongestSignal = Integer.MIN_VALUE;

    @SuppressLint("MissingPermission") // only reached from a scan, which needs the permission
    @Override public void uiDeviceFound(BluetoothDevice device, int rssi) {
        if (rssi > mStrongestSignal) {
            LOGD(TAG, "uiDeviceFound() ... Found nearest signal generator: " + rssi);
            mStrongestSignal = rssi;
            mDeviceAddress = device.getAddress();
        }
    }

    void connectToNearestBtDevice() {
        // the user pressed Stop (or we left the screen) while we were waiting
        if (!mScanning) return;

        if (mDeviceAddress != null) {
            LOGD(TAG, "connectToNearestBtDevice() ... Found a signal generator!");

            viewHolder.boardConnectedImage.setImageAlpha(60);  //slowly builds up until connection
            viewHolder.boardConnectedImage.setVisibility(View.VISIBLE);

            LOGD(TAG, "connectToNearestBtDevice() ... mDeviceAddress = " + mDeviceAddress);

            mScanning = false;
            invalidateOptionsMenu();
            mRemoteManager.stopScanning();

            mRemoteManager.connect(mDeviceAddress);
        } else {
            LOGD(TAG, "connectToNearestBtDevice() ... Couldn't find a signal generator! Continue to scan.");
            mHandler.postDelayed(mConnectToNearest, SCANNING_TIMEOUT);
        }
    }

    @Override public void uiLeftTurnSentSuccessfully(final int stimulusDuration) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                viewHolder.goLeftText.setVisibility(View.VISIBLE);
                mTurning = true;
                addTurnCommandTimeout(stimulusDuration);
            }
        });
    }

    @Override public void uiRightTurnSentSuccessfully(final int stimulusDuration) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                viewHolder.goRightText.setVisibility(View.VISIBLE);
                mTurning = true;
                addTurnCommandTimeout(stimulusDuration);
            }
        });
    }

    void onLeftSwipe() {
        LOGD(TAG, "onLeftSwipe()");
        if (mRemoteManager.isConnected() && !mOnSettingsScreen) {
            if (!mTurning) mRemoteManager.turnLeft();
        }
    }

    void onRightSwipe() {
        LOGD(TAG, "onRightSwipe()");
        if (mRemoteManager.isConnected() && !mOnSettingsScreen) {
            if (!mTurning) mRemoteManager.turnRight();
        }
    }

    private void startLeScan() {
        // tapping Find is an explicit request, so ask again even if permission was refused before
        mPermissionsDenied = false;
        if (!checkPrerequisites()) return;
        if (BluetoothUtils.isLocationOffForScanning(this)) {
            showLocationDialog();
            return;
        }

        mStrongestSignal = Integer.MIN_VALUE;
        mDeviceAddress = null;
        mHandler.postDelayed(mConnectToNearest, SCANNING_TIMEOUT);
        mScanning = true;
        mRemoteManager.startScanning();
        invalidateOptionsMenu();
    }

    private void stopLeScan() {
        mHandler.removeCallbacks(mConnectToNearest);
        mScanning = false;
        mRemoteManager.stopScanning();
        invalidateOptionsMenu();
    }

    private void disconnect() {
        mRemoteManager.disconnect();
        showMainScreen();
    }

    private void showDisconnected() {
        viewHolder.configText.setText("");
        viewHolder.boardConnectedImage.setVisibility(View.INVISIBLE);
        showMainScreen();
    }

    private void showSettingsScreen() {
        if (mOnSettingsScreen) return;
        viewHolder.flipper.setDisplayedChild(SCREEN_SETTINGS);
        mOnSettingsScreen = true;
        mSettingsBackCallback.setEnabled(true);
    }

    private void showMainScreen() {
        if (!mOnSettingsScreen) return;
        viewHolder.flipper.setDisplayedChild(SCREEN_MAIN);
        mOnSettingsScreen = false;
        mSettingsBackCallback.setEnabled(false);
    }

    /* make sure that potential scanning will take no longer
 * than <SCANNING_TIMEOUT> seconds from now on */
    void addTurnCommandTimeout(int timeoutInMS) {
        Runnable timeout = new Runnable() {
            @Override public void run() {
                viewHolder.goRightText.setVisibility(View.INVISIBLE);
                viewHolder.goLeftText.setVisibility(View.INVISIBLE);
                mTurning = false;
            }
        };
        mHandler.postDelayed(timeout, timeoutInMS);
    }

    private void btDisabled() {
        Toast.makeText(this, getString(R.string.message_turn_on_bt), Toast.LENGTH_LONG).show();
        finish();
    }

    private void bleMissing() {
        Toast.makeText(this, getString(R.string.message_ble_hardware_required), Toast.LENGTH_LONG).show();
        finish();
    }

    //==============================================
    // PERMISSIONS, BLUETOOTH AND LOCATION
    //==============================================

    /**
     * Walks the user through whatever is missing: the Bluetooth permission first (Android 12+ needs it even to ask
     * for Bluetooth to be turned on), then Bluetooth itself.
     *
     * @return {@code true} when everything is ready to scan.
     */
    private boolean checkPrerequisites() {
        if (!BluetoothUtils.hasPermissions(this)) {
            if (!mPermissionsDenied && !mAskingPermission) {
                mAskingPermission = true;
                mPermissionLauncher.launch(BluetoothUtils.requiredPermissions());
            }
            return false;
        }
        return ensureBluetoothOn();
    }

    @SuppressLint("MissingPermission") // callers check BluetoothUtils.hasPermissions() first
    private boolean ensureBluetoothOn() {
        if (BluetoothUtils.isBtEnabled(this)) return true;
        if (!mAskingBluetooth) {
            // BT is not turned on - ask user to make it enabled
            mAskingBluetooth = true;
            mEnableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
        return false;
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.title_permission_needed)
            .setMessage(R.string.rationale_bluetooth)
            .setPositiveButton(R.string.action_setting, (dialog, which) -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showLocationDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.title_location_off)
            .setMessage(R.string.message_location_off)
            .setPositiveButton(R.string.action_setting,
                (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    /**
     *
     */
    static class ViewHolder {
        View root;
        View appBar;
        Toolbar toolbar;
        ViewFlipper flipper;

        TextView goLeftText;
        TextView goRightText;
        TextView configText;
        ImageView boardImage;
        ImageView boardConnectedImage;

        SeekBar Frequecy;
        SeekBar Duration;
        SeekBar PulseWidth;
        SeekBar Gain;
        SwitchCompat RandomMode;

        // Binds UI elements to local variables
        void bind(@NonNull Activity activity) {
            root = activity.findViewById(R.id.root);
            appBar = activity.findViewById(R.id.appBar);
            toolbar = activity.findViewById(R.id.toolbar);
            flipper = activity.findViewById(R.id.viewFlipper);
            boardImage = activity.findViewById(R.id.imageBoard);
            boardConnectedImage = activity.findViewById(R.id.imageBoardConnected);
            goLeftText = activity.findViewById(R.id.textGoLeft);
            goRightText = activity.findViewById(R.id.textGoRight);
            configText = activity.findViewById(R.id.textConfig);
            Duration = activity.findViewById(R.id.sbDuration);
            Gain = activity.findViewById(R.id.sbGain);
            Frequecy = activity.findViewById(R.id.sbFrequency);
            PulseWidth = activity.findViewById(R.id.sbPulseWidth);
            RandomMode = activity.findViewById(R.id.swRandomMode);
        }
    }

    // Private class for gestures
    private class SwipeGestureDetector extends SimpleOnGestureListener {
        // Swipe properties, you can change it to make the swipe
        // longer or shorter and speed
        private static final int SWIPE_MIN_DISTANCE = 60;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX,
            float velocityY) {
            if (e1 == null) return false;
            try {
                float diffAbs = Math.abs(e1.getY() - e2.getY());
                float diff = e1.getX() - e2.getX();

                if (diffAbs > SWIPE_MAX_OFF_PATH) return false;

                // Left swipe
                if (diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onLeftSwipe();

                    // Right swipe
                } else if (-diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onRightSwipe();
                }
            } catch (Exception e) {
                LOGE(TAG, "Error on gestures");
            }
            return false;
        }
    }
}
