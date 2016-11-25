/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import Utility.Utility;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static String TAG = MyWatchFace.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public WatchFaceEngine onCreateEngine() {
        return new WatchFaceEngine();
    }
///This is necessary because of the limitations of WatchFaceService discussed above. If your own watch face only needs to be updated every minute
    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.WatchFaceEngine> mWeakReference;

        public EngineHandler(MyWatchFace.WatchFaceEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.WatchFaceEngine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    //The Engine object associated with your service is what drives your watch face.
// It handles timers, displaying your user interface, moving in and out of ambient mode,
// and getting information about the physical watch display.
    private class WatchFaceEngine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
       // Defining Necessary Values and Variables
        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_INFO_PATH = "/weather-info";

        private static final String KEY_UUID = "uuid";
        private static final String KEY_HIGH = "high";
        private static final String KEY_LOW = "low";
        private static final String KEY_WEATHER_ID = "weatherId";


        Paint mTextTimePaint;
        Paint mTextTimeSecondsPaint;
        Paint mTextDatePaint;
        Paint mTextDateAmbientPaint;
        Paint mTextTempHighPaint;
        Paint mTextTempLowPaint;
        Paint mTextTempLowAmbientPaint;

        Bitmap mWeatherIcon;
        String mWeatherHigh;
        String mWeatherLow;

        float mTimeYOffset =100;
        float mDateYOffset;
        float mDividerYOffset;
        float mWeatherYOffset;
        //define is a broadcast receiver that handles the situation where a user may be traveling and change time zones.
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                mCalendar.setTimeInMillis(now);
            }
        };
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
//Now that your variables and objects are declared, it's time to start initializing the watch face.
// Engine has an onCreate method that should be used for creating objects and other tasks that can take a significant amount of time
// and battery.
// You will also want to set a few flags for the WatchFaceStyle here to control how the system interacts with the user when your watch face is active.
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
//initBackground
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mTextTimePaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            mTextTimeSecondsPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            mTextDatePaint = createTextPaint(resources.getColor(R.color.primary_light), NORMAL_TYPEFACE);
            mTextDateAmbientPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            mTextTempHighPaint = createTextPaint(Color.WHITE, BOLD_TYPEFACE);
            mTextTempLowPaint = createTextPaint(resources.getColor(R.color.primary_light), NORMAL_TYPEFACE);
            mTextTempLowAmbientPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            /*mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));*/

            mCalendar = Calendar.getInstance();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

       // is called when the user hides or shows the watch face
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                mGoogleApiClient.disconnect();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }
        //This is used to determine if the device your watch face is running on is rounded or squared. This lets you change your watch face to match up with the hardware.
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
// Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            mDateYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_y_offset_round : R.dimen.digital_date_y_offset);
            mDividerYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_divider_y_offset_round : R.dimen.digital_divider_y_offset);
            mWeatherYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_weather_y_offset_round : R.dimen.digital_weather_y_offset);

            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mTextTimePaint.setTextSize(timeTextSize);
            mTextTimeSecondsPaint.setTextSize((float) (tempTextSize * 0.80));
            mTextDatePaint.setTextSize(dateTextSize);
            mTextDateAmbientPaint.setTextSize(dateTextSize);
            mTextTempHighPaint.setTextSize(tempTextSize);
            mTextTempLowAmbientPaint.setTextSize(tempTextSize);
            mTextTempLowPaint.setTextSize(tempTextSize);
        }
//you check if those attributes apply to the device running your watch face and save them in a member variable defined at the top of your Engine.
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }
//When your device is in ambient mode, the Handler timer will be disabled.
// Your watch face can still update with the current time every minute through using the built-in onTimeTick method to invalidate the Canvas
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }
//is called when the device moves in or out of ambient mode.
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            boolean is24Hour = DateFormat.is24HourFormat(MyWatchFace.this);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            int am_pm = mCalendar.get(Calendar.AM_PM);

            String timeText;
            if (is24Hour) {
                int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
                timeText = String.format("%02d:%02d", hour, minute);
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                timeText = String.format("%d:%02d", hour, minute);
            }

            String secondsText = String.format("%02d", second);
            String amPmText = Utility.getAmPmString(getResources(), am_pm);
            float timeTextLen = mTextTimePaint.measureText(timeText);
            float xOffsetTime = timeTextLen / 2;
            if (mAmbient) {
                if (!is24Hour) {
                    xOffsetTime = xOffsetTime + (mTextTimeSecondsPaint.measureText(amPmText) / 2);
                }
            } else {
                xOffsetTime = xOffsetTime + (mTextTimeSecondsPaint.measureText(secondsText) / 2);
            }
            float xOffsetTimeFromCenter = bounds.centerX() - xOffsetTime;
            canvas.drawText(timeText, xOffsetTimeFromCenter, mTimeYOffset, mTextTimePaint);
            if (mAmbient) {
                if (!is24Hour) {
                    canvas.drawText(amPmText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset, mTextTimeSecondsPaint);
                }
            } else {
                canvas.drawText(secondsText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset, mTextTimeSecondsPaint);
            }

            // Decide which paint to user for the next bits dependent on ambient mode.
            Paint datePaint = mAmbient ? mTextDateAmbientPaint : mTextDatePaint;

            Resources resources = getResources();

            // Draw the date
            String dayOfWeekString = Utility.getDayOfWeekString(resources, mCalendar.get(Calendar.DAY_OF_WEEK));
            String monthOfYearString = Utility.getMonthOfYearString(resources, mCalendar.get(Calendar.MONTH));

            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
            int year = mCalendar.get(Calendar.YEAR);

            String dateText = String.format("%s, %s %d %d", dayOfWeekString, monthOfYearString, dayOfMonth, year);
            float xOffsetDate = datePaint.measureText(dateText) / 2;
            canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateYOffset, datePaint);

            // Draw high and low temp if we have it
            if (mWeatherHigh != null && mWeatherLow != null && mWeatherIcon != null) {
                // Draw a line to separate date and time from weather elements
                canvas.drawLine(bounds.centerX() - 20, mDividerYOffset, bounds.centerX() + 20, mDividerYOffset, datePaint);

                float highTextLen = mTextTempHighPaint.measureText(mWeatherHigh);

                if (mAmbient) {
                    float lowTextLen = mTextTempLowAmbientPaint.measureText(mWeatherLow);
                    float xOffset = bounds.centerX() - ((highTextLen + lowTextLen + 20) / 2);
                    canvas.drawText(mWeatherHigh, xOffset, mWeatherYOffset, mTextTempHighPaint);
                    canvas.drawText(mWeatherLow, xOffset + highTextLen + 20, mWeatherYOffset, mTextTempLowAmbientPaint);
                } else {
                    float xOffset = bounds.centerX() - (highTextLen / 2);
                    canvas.drawText(mWeatherHigh, xOffset, mWeatherYOffset, mTextTempHighPaint);
                    canvas.drawText(mWeatherLow, bounds.centerX() + (highTextLen / 2) + 20, mWeatherYOffset, mTextTempLowPaint);
                    float iconXOffset = bounds.centerX() - ((highTextLen / 2) + mWeatherIcon.getWidth() + 30);
                    canvas.drawBitmap(mWeatherIcon, iconXOffset, mWeatherYOffset - mWeatherIcon.getHeight(), null);
                }
            }

            /*String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);*/
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, WatchFaceEngine.this);
            requestWeatherInfo();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG, path);
                    if (path.equals(WEATHER_INFO_PATH)) {
                        if (dataMap.containsKey(KEY_HIGH)) {
                            mWeatherHigh = dataMap.getString(KEY_HIGH);
                            Log.d(TAG, "High = " + mWeatherHigh);
                        } else {
                            Log.d(TAG, "What? No high?");
                        }

                        if (dataMap.containsKey(KEY_LOW)) {
                            mWeatherLow = dataMap.getString(KEY_LOW);
                            Log.d(TAG, "Low = " + mWeatherLow);
                        } else {
                            Log.d(TAG, "What? No low?");
                        }

                        if (dataMap.containsKey(KEY_WEATHER_ID)) {
                            int weatherId = dataMap.getInt(KEY_WEATHER_ID);
                            Drawable b = getResources().getDrawable(Utility.getIconResourceForWeatherCondition(weatherId));
                            Bitmap icon = ((BitmapDrawable) b).getBitmap();
                            float scaledWidth = (mTextTempHighPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
                            mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTextTempHighPaint.getTextSize(), true);

                        } else {
                            Log.d(TAG, "What? no weatherId?");
                        }

                        invalidate();
                    }
                }
            }
        }

        public void requestWeatherInfo() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
            putDataMapRequest.getDataMap().putString(KEY_UUID, UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Failed asking phone for weather data");
                            } else {
                                Log.d(TAG, "Successfully asked for weather data");
                            }
                        }
                    });

        }
    }
}
