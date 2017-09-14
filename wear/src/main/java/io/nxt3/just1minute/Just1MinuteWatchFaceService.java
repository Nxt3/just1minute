package io.nxt3.just1minute;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static io.nxt3.just1minute.HelperFunctions.isTimeInRange;
import static io.nxt3.just1minute.HelperFunctions.scalePosition;
import static io.nxt3.just1minute.HelperFunctions.spToPx;

public class Just1MinuteWatchFaceService extends CanvasWatchFaceService {
    private final String TAG = "just1minute";

    //Supported complication types
    public static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                    ComplicationData.TYPE_LONG_TEXT,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_LARGE_IMAGE
            }
    };
    private static final int WALLPAPER_COMPLICATION_ID = 0;
    private static final int TOP_COMPLICATION_ID = 1;
    private static final int BOTTOM_COMPLICATION_ID = 2;
    public static final int[] COMPLICATION_IDS = {
            WALLPAPER_COMPLICATION_ID,
            TOP_COMPLICATION_ID,
            BOTTOM_COMPLICATION_ID
    };

    @Override
    public Engine onCreateEngine() {
        return new Just1MinuteWatchFaceEngine();
    }

    /**
     * The engine responsible for the Drawing of the watch face and receives events from the system
     */
    private class Just1MinuteWatchFaceEngine extends CanvasWatchFaceService.Engine {
        private final int MSG_UPDATE_TIME = 0;

        //Tick parameters
        //used as the starting point for drawing the ticks
        private final float AMBIENT_STROKE = 2f;

        //Update rate in milliseconds for interactive mode; once a minute by default
        private final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

        //Context used for HelperFunctions
        private Context mContext;

        //Used for managing the time
        private Calendar mCalendar;

        //Booleans for various device specific settings
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private boolean mRegisteredTimeZoneReceiver = false;

        //Coordinates for center (x, y)
        private int mCenterX;
        private int mCenterY;

        //Colors for each component
        private int mHourTickColor;
        private int mTickColor;
        private int mMinuteTextColor;
        private int mBackgroundColor;

        //Paint objects for each component
        private Paint mHourTickPaint;
        private Paint mTickPaint;
        private Paint mMinuteTextPaint;
        private Paint mBackgroundPaint;

        //Colors for each complication component
        private int mComplicationColor;
        private int mComplicationTitleColor;
        private int mComplicationBorderColor;

        //Paint objects for notification icons
        private Paint mNotificationTextPaint;
        private Paint mNotificationCirclePaint;

        //Colors for the notification indicator
        private int mNotificationTextColor;
        private int mNotificationCircleColor;

        //Notification counts
        private int mNotificationCount;
        private int mUnreadNotificationCount;

        //Complication stuff
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;
        private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;
        private final float COMPLICATION_RADIUS = 8f;

        //Fonts
        private Typeface mMinuteTextFont;
        private final Typeface mAmbientFont
                = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        private boolean mFontNeedsAdjustment = false;

        //Other settings
        private boolean mShowComplicationBorder;
        private boolean mHideHourTicks;

        //Notification indicators
        private boolean mShowNotificationIndicator;
        private boolean mNotificationIndicatorUnread;
        private boolean mNotificationIndicatorAll;

        //Night mode
        private boolean mNightModeEnabled;
        private long mNightModeStartTimeMillis;
        private long mNightModeEndTimeMillis;
        private boolean mManualNightModeEnabled;
        private boolean mForceNightMode;


        /**
         * Called when the watch face service is created for the first time
         * We will initialize our drawing components here
         */
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mContext = getApplicationContext();

            mCalendar = Calendar.getInstance();

            setWatchFaceStyle(new WatchFaceStyle.Builder(Just1MinuteWatchFaceService.this)
                    .setStatusBarGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL
                            | Gravity.TOP)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR
                            | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .setAcceptsTapEvents(true)
                    .build());

            loadMiscPrefs();

            if (!isInNightMode()) {
                loadColorPrefs();
            } else {
                loadNightModeColorPrefs();
            }

            initializeBackgroundAndTicks();
            initializeComplications();
            initializeNotificationCount();
            initializeWatchFace();
        }

        /**
         * Init watch face components (hour, minute, second hands)
         */
        private void initializeWatchFace() {
            Log.d(TAG, "Init watch face components");

            //Current hour tick
            mHourTickPaint = new Paint();
            mHourTickPaint.setColor(mHourTickColor);
            mHourTickPaint.setStyle(Paint.Style.FILL);
            mHourTickPaint.setStrokeJoin(Paint.Join.MITER);
            mHourTickPaint.setStrokeCap(Paint.Cap.SQUARE);
            mHourTickPaint.setStrokeWidth(AMBIENT_STROKE);
            mHourTickPaint.setAntiAlias(true);

            //Minute text
            mMinuteTextPaint = new TextPaint();
            mMinuteTextPaint.setColor(mMinuteTextColor);
            mMinuteTextPaint.setTypeface(mMinuteTextFont);
            mMinuteTextPaint.setAntiAlias(true);
        }

        /**
         * Init the backgrounds and circle
         */
        private void initializeBackgroundAndTicks() {
            Log.d(TAG, "Init background");

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);
            mBackgroundPaint.setAntiAlias(true);

            //Hour ticks
            mTickPaint = new Paint();
            mTickPaint.setColor(mTickColor);
            mTickPaint.setStyle(Paint.Style.FILL);
            mTickPaint.setStrokeJoin(Paint.Join.MITER);
            mTickPaint.setStrokeCap(Paint.Cap.SQUARE);
            mTickPaint.setStrokeWidth(AMBIENT_STROKE);
            mTickPaint.setAntiAlias(true);
        }

        /**
         * Init watch face complications components
         */
        private void initializeComplications() {
            Log.d(TAG, "Init complications");
            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            //create a complication for each complicationId
            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                createComplication(COMPLICATION_ID);
            }

            setActiveComplications(COMPLICATION_IDS);
        }

        /**
         * Creates a ComplicationDrawable for the complicationId
         *
         * @param complicationId to create a ComplicationDrawable for
         */
        private void createComplication(int complicationId) {
            final ComplicationDrawable complicationDrawable
                    = (ComplicationDrawable) getDrawable(R.drawable.complication_styles);

            updateComplicationStyles(complicationDrawable);

            mComplicationDrawableSparseArray.put(complicationId, complicationDrawable);
        }

        /**
         * Init notification counts
         */
        private void initializeNotificationCount() {
            mNotificationCirclePaint = new Paint();
            mNotificationCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mNotificationCirclePaint.setColor(mNotificationCircleColor);
            mNotificationCirclePaint.setAntiAlias(true);
            mNotificationCirclePaint.setStrokeWidth(2f);

            final Typeface notificationFont = Typeface.create("sans-serif", Typeface.BOLD);

            mNotificationTextPaint = new TextPaint();
            mNotificationTextPaint.setColor(mNotificationTextColor);
            mNotificationTextPaint.setTextAlign(Paint.Align.CENTER);
            mNotificationTextPaint.setAntiAlias(true);
            mNotificationTextPaint.setTypeface(notificationFont);
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);

            drawComplications(canvas, now);

            drawTicks(canvas);
            drawMinuteText(canvas);

            if (mShowNotificationIndicator) {
                drawNotificationCount(canvas);
            }
        }

        /**
         * Handles drawing the background
         *
         * @param canvas to draw to
         */
        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(mBackgroundColor);
            }
        }

        /**
         * Handles drawing the complications
         *
         * @param canvas            to draw to
         * @param currentTimeMillis current time
         */
        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                final ComplicationDrawable complicationDrawable
                        = mComplicationDrawableSparseArray.get(COMPLICATION_ID);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        /**
         * Handles drawing the tick marks AND the current hour tick
         *
         * @param canvas to draw to
         */
        private void drawTicks(Canvas canvas) {
            final int currentHour = mCalendar.get(Calendar.HOUR);

            //properties for the ticks
            final float topTickWidth = 4f;
            final float bottomTickWidth = 3f;
            final float tickLength = scalePosition(mCenterX, 7.3f);
            final float tickOffset = scalePosition(mCenterX, 32f);

            final Path tickMarkPolygon = createTickPath(mCenterX, topTickWidth, bottomTickWidth,
                    tickOffset, tickLength);

            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                //If the current hour is at the index, then draw the hour tick instead
                if (currentHour == tickIndex) {
                    if (mHideHourTicks) {
                        canvas.drawPath(tickMarkPolygon, mHourTickPaint);
                    } else {
                        //If the hour ticks are shown, increase the size of the current hour tick
                        final float sizeIncrease = 2f;
                        canvas.drawPath(createTickPath(mCenterX, topTickWidth + sizeIncrease,
                                bottomTickWidth + sizeIncrease, tickOffset, tickLength),
                                mHourTickPaint);
                    }
                } else if (!mHideHourTicks) {
                    canvas.drawPath(tickMarkPolygon, mTickPaint);
                }
                canvas.rotate(30, mCenterX, mCenterY); //rotate the canvas 30 degrees each time
            }
        }

        /**
         * Creates the tick Path
         *
         * @param x               starting position
         * @param topTickWidth    width of top of the polygon
         * @param bottomTickWidth width of the bottom of the polygon
         * @param tickOffset      offset from the edge to start drawing the tick
         * @param tickLength      length of the tick
         * @return Path for the tick
         */
        private Path createTickPath(float x, float topTickWidth, float bottomTickWidth,
                                    float tickOffset, float tickLength) {
            final Path tickMarkPolygon = new Path();
            tickMarkPolygon.moveTo(x - topTickWidth, tickOffset); //top left
            tickMarkPolygon.lineTo(x + topTickWidth, tickOffset); //top right
            tickMarkPolygon.lineTo(x + bottomTickWidth, tickOffset + tickLength); //bottom right
            tickMarkPolygon.lineTo(x - bottomTickWidth, tickOffset + tickLength); //bottom left
            tickMarkPolygon.close();

            return tickMarkPolygon;
        }

        /**
         * Handles drawing the minute text
         *
         * @param canvas to draw to
         */
        private void drawMinuteText(Canvas canvas) {
            mMinuteTextPaint.setTextAlign(Paint.Align.CENTER);

            final String minuteString = String.format(Locale.getDefault(),
                    "%02d", mCalendar.get(Calendar.MINUTE));

            float yPos;
            if (mFontNeedsAdjustment) {
                yPos = (mCenterY
                        - ((mMinuteTextPaint.descent() + mMinuteTextPaint.ascent()) / 2f)
                        //move the text down since handwritten fonts are skewed upwards for some reason
                        + scalePosition(mCenterX, 32f));
            } else {
                yPos = (mCenterY
                        - ((mMinuteTextPaint.descent() + mMinuteTextPaint.ascent()) / 2f)
                        //move the text ever-so-slightly upwards to make it look more centered
                        - scalePosition(mCenterX, 192f));
            }

            canvas.drawText(minuteString,
                    mCenterX,
                    yPos,
                    mMinuteTextPaint);
        }

        /**
         * Handles drawing the notification count
         *
         * @param canvas to draw to
         */
        private void drawNotificationCount(Canvas canvas) {
            int count = 0;

            if (mNotificationIndicatorUnread) {
                count = mUnreadNotificationCount;
            } else if (mNotificationIndicatorAll) {
                count = mNotificationCount;
            }

            if (count > 0) {
                //(x,y) coordinates for where to draw the notification indicator
                float xPos = mCenterX + scalePosition(mCenterX, 5f);
                float yPos = mCenterY - scalePosition(mCenterY, 8f);

                canvas.drawCircle(xPos, yPos, mCenterX * 0.06f, mNotificationCirclePaint);
                canvas.drawText(String.valueOf(mNotificationCount), xPos,
                        yPos - (mNotificationTextPaint.descent()
                                + mNotificationTextPaint.ascent()) / 2, mNotificationTextPaint);
            }
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());

                loadMiscPrefs();

                if (!isInNightMode()) {
                    loadColorPrefs();
                } else {
                    loadNightModeColorPrefs();
                }

                updateWatchStyles();

                for (int COMPLICATION_ID : COMPLICATION_IDS) {
                    final ComplicationDrawable complicationDrawable
                            = mComplicationDrawableSparseArray.get(COMPLICATION_ID);
                    updateComplicationStyles(complicationDrawable);
                }

                invalidate();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;

            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                final ComplicationDrawable complicationDrawable
                        = mComplicationDrawableSparseArray.get(COMPLICATION_ID);
                complicationDrawable.setInAmbientMode(mAmbient);
            }

            if (!mAmbient) {
                if (!isInNightMode()) {
                    loadColorPrefs();
                } else {
                    loadNightModeColorPrefs();
                }
            }

            updateWatchStyles();

            //Check and trigger whether or not timer should be running (only in active mode)
            updateTimer();
        }


        /**
         * Captures tap event (and tap type).
         * The {@link android.support.wearable.watchface.WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         *
         * @param tapType   type of tapping the user is performing
         * @param x         coordinate of the tap
         * @param y         coordinate of the tap
         * @param eventTime time the tap took place
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TAP:
                    int tappedComplicationId = getTappedComplicationId(x, y);
                    if (tappedComplicationId != -1) {
                        onComplicationTapped(tappedComplicationId);
                    }

                    if (mManualNightModeEnabled) {
                        final Rect centerBounds = createComplicationRect(mCenterX, mCenterY, 6f);

                        if (centerBounds.contains(x, y)) {
                            mForceNightMode = !mForceNightMode; //toggle the boolean

                            if (!isInNightMode()) {
                                loadColorPrefs();
                            } else {
                                loadNightModeColorPrefs();
                            }
                            updateWatchStyles();

                            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                                final ComplicationDrawable complicationDrawable
                                        = mComplicationDrawableSparseArray.get(COMPLICATION_ID);
                                updateComplicationStyles(complicationDrawable);
                            }

                            final SharedPreferences prefs
                                    = PreferenceManager.getDefaultSharedPreferences(mContext);
                            prefs.edit().putBoolean("force_night_mode", mForceNightMode).apply();
                            invalidate();
                        }
                    }
                    break;
            }
        }

        /**
         * Handles what to do once a complication is tapped
         *
         * @param id of the complication tapped
         */
        private void onComplicationTapped(int id) {
            final ComplicationData complicationData = mActiveComplicationDataSparseArray.get(id);

            if (complicationData != null) {
                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.d(TAG, "Something went wrong with tapping a complication");
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {
                    ComponentName componentName = new ComponentName(
                            mContext, WatchFaceService.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    mContext, componentName);

                    startActivity(permissionRequestIntent);
                }
            }
        }

        /**
         * Determines if a tap was inside a complication area
         *
         * @param x coordinate of tap
         * @param y coordinate of tap
         * @return the id of the complication that was tapped; if no complication was tapped,
         * return -1
         */
        private int getTappedComplicationId(int x, int y) {
            long currentTimeMillis = System.currentTimeMillis();

            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                final ComplicationData complicationData
                        = mActiveComplicationDataSparseArray.get(COMPLICATION_ID);

                if ((complicationData != null)
                        && (complicationData.isActive(currentTimeMillis))
                        && (complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED)
                        && (complicationData.getType() != ComplicationData.TYPE_EMPTY)) {

                    final ComplicationDrawable complicationDrawable
                            = mComplicationDrawableSparseArray.get(COMPLICATION_ID);
                    final Rect complicationBoundingRect = complicationDrawable.getBounds();

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return COMPLICATION_ID;
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        /**
         * Called when there is updated data for the complication
         *
         * @param complicationId   id of the complication to update data for
         * @param complicationData data to update the complication with
         */
        @Override
        public void onComplicationDataUpdate(int complicationId,
                                             ComplicationData complicationData) {
            //Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);

            //Updates correct ComplicationDrawable with updated data.
            final ComplicationDrawable complicationDrawable
                    = mComplicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        /**
         * Creates the rectangles objects for the complication to be placed in
         *
         * @param centerX       x coordinate of the center
         * @param centerY       y coordinate of the center
         * @param desiredRadius radius to use for dividing by the mCenterX coordinate
         * @return the complication rectangle
         */
        private Rect createComplicationRect(float centerX, float centerY, float desiredRadius) {
            final int radius = Math.round(mCenterX / desiredRadius);

            final int centerXInt = Math.round(centerX);
            final int centerYInt = Math.round(centerY);

            //creates the width to the Rect
            final int magicNumber = Math.round(scalePosition(mCenterX, 8f));

            return new Rect(centerXInt - radius - magicNumber,
                    centerYInt - radius,
                    centerXInt + radius + magicNumber,
                    centerYInt + radius);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mCenterX = width / 2;
            mCenterY = height / 2;

            //Handle measuring the minute text size
            mMinuteTextPaint.setTextSize(scalePosition(mCenterX, 3.6f));

            //Handle measuring the notification text
            mNotificationTextPaint.setTextSize(scalePosition(mCenterX, 20f));

            //Below is for measuring the complications
            final float offset = -16f; //offset for complications
            final Rect topBounds = createComplicationRect(mCenterX, mCenterY / 2 - offset,
                    COMPLICATION_RADIUS);
            final Rect bottomBounds = createComplicationRect(mCenterX, mCenterY * 1.5f + offset,
                    COMPLICATION_RADIUS);

            final ComplicationDrawable topComplicationDrawable =
                    mComplicationDrawableSparseArray.get(TOP_COMPLICATION_ID);
            topComplicationDrawable.setBounds(topBounds);

            final ComplicationDrawable bottomComplicationDrawable =
                    mComplicationDrawableSparseArray.get(BOTTOM_COMPLICATION_ID);
            bottomComplicationDrawable.setBounds(bottomBounds);

            final ComplicationDrawable wallpaperComplicationDrawable =
                    mComplicationDrawableSparseArray.get(WALLPAPER_COMPLICATION_ID);
            wallpaperComplicationDrawable.setBounds(new Rect(0, 0, width, height));
        }


        /**
         * Update the watch paint styles when changing between Ambient and Non-Ambient modes
         */
        private void updateWatchStyles() {
            if (mAmbient) {
                mBackgroundPaint.setColor(Color.BLACK);

                mHourTickPaint.setColor(Color.WHITE);

                mTickPaint.setColor(Color.GRAY);
                mTickPaint.setStyle(Paint.Style.STROKE);

                mMinuteTextPaint.setColor(Color.WHITE);
                mMinuteTextPaint.setTypeface(mAmbientFont);

                if (mShowNotificationIndicator) {
                    mNotificationTextPaint.setColor(Color.WHITE);
                    mNotificationCirclePaint.setColor(Color.TRANSPARENT);
                }

                if (mLowBitAmbient) {
                    mHourTickPaint.setAntiAlias(false);
                    mTickPaint.setAntiAlias(false);
                    mMinuteTextPaint.setAntiAlias(false);

                    if (mShowNotificationIndicator) {
                        mNotificationTextPaint.setAntiAlias(false);
                        mNotificationCirclePaint.setAntiAlias(false);
                    }
                }
            } else {
                mBackgroundPaint.setColor(mBackgroundColor);

                mHourTickPaint.setColor(mHourTickColor);
                mHourTickPaint.setStyle(Paint.Style.FILL);

                mTickPaint.setColor(mTickColor);
                mTickPaint.setStyle(Paint.Style.FILL);

                mMinuteTextPaint.setColor(mMinuteTextColor);
                mMinuteTextPaint.setTypeface(mMinuteTextFont);

                if (mShowNotificationIndicator) {
                    mNotificationTextPaint.setColor(mNotificationTextColor);
                    mNotificationCirclePaint.setColor(mNotificationCircleColor);
                }

                if (mLowBitAmbient) {
                    mHourTickPaint.setAntiAlias(true);
                    mTickPaint.setAntiAlias(true);
                    mMinuteTextPaint.setAntiAlias(true);

                    if (mShowNotificationIndicator) {
                        mNotificationTextPaint.setAntiAlias(true);
                        mNotificationCirclePaint.setAntiAlias(true);
                    }
                }
            }
        }

        /**
         * Update the complication styles
         *
         * @param complicationDrawable to set styles for
         */
        private void updateComplicationStyles(ComplicationDrawable complicationDrawable) {
            final Typeface complicationActiveFont = Typeface.create("sans-serif", Typeface.BOLD);

            //Sets the styles for the complications
            if (complicationDrawable != null) {
                complicationDrawable.setContext(mContext);
                complicationDrawable.setTextTypefaceActive(complicationActiveFont);
                complicationDrawable.setTitleTypefaceActive(complicationActiveFont);
                complicationDrawable.setTextColorActive(mComplicationColor);
                complicationDrawable.setTitleColorActive(mComplicationTitleColor);
                complicationDrawable.setIconColorActive(mComplicationColor);
                complicationDrawable.setHighlightColorActive(mComplicationColor);

                //Grayscale images when in Ambient Mode
                final ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                complicationDrawable.setImageColorFilterAmbient(new ColorMatrixColorFilter(matrix));

                //If the border is drawn...
                if (mShowComplicationBorder) {
                    complicationDrawable
                            .setBorderStyleActive(ComplicationDrawable.BORDER_STYLE_SOLID);
                    complicationDrawable
                            .setBorderStyleAmbient(ComplicationDrawable.BORDER_STYLE_SOLID);
                    complicationDrawable.setBorderColorActive(mComplicationBorderColor);

                    final float textSize = 13f;
                    complicationDrawable
                            .setTextSizeActive(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTitleSizeActive(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTextSizeAmbient(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTitleSizeAmbient(spToPx(mContext, textSize));

                    complicationDrawable
                            .setBorderRadiusActive(100);
                    complicationDrawable
                            .setBorderRadiusAmbient(100);
                } else { //if the border is NOT drawn...
                    complicationDrawable
                            .setBorderStyleActive(ComplicationDrawable.BORDER_STYLE_NONE);
                    complicationDrawable
                            .setBorderStyleAmbient(ComplicationDrawable.BORDER_STYLE_NONE);

                    final float textSize = 14f;
                    complicationDrawable
                            .setTextSizeActive(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTitleSizeActive(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTextSizeAmbient(spToPx(mContext, textSize));
                    complicationDrawable
                            .setTitleSizeAmbient(spToPx(mContext, textSize));

                    complicationDrawable.setBorderRadiusActive(0);
                    complicationDrawable.setBorderRadiusAmbient(0);
                }
            }
        }

        @Override
        public void onUnreadCountChanged(int count) {
            super.onUnreadCountChanged(count);
            mUnreadNotificationCount = count;
        }

        @Override
        public void onNotificationCountChanged(int count) {
            super.onNotificationCountChanged(count);
            mNotificationCount = count;
        }


        /**
         * Loads the normal color settings
         */
        private void loadColorPrefs() {
            final SharedPreferences prefs
                    = PreferenceManager.getDefaultSharedPreferences(mContext);

            //Default colors
            final int defaultHour = getColor(R.color.default_current_hour_tick);
            final int defaultTick = getColor(R.color.default_hour_tick);
            final int defaultMinutes = getColor(R.color.default_minute_text);
            final int defaultBackground = getColor(R.color.default_background);
            final int defaultComplications = getColor(R.color.default_complications);

            //Tick colors
            mHourTickColor = prefs.getInt("settings_hour_tick_color_value", defaultHour);
            mTickColor = prefs.getInt("settings_tick_color_value", defaultTick);

            //Minute text colors
            mMinuteTextColor = prefs.getInt("settings_minute_text_color_value", defaultMinutes);

            //Background colors
            mBackgroundColor = prefs.getInt("settings_background_color_value", defaultBackground);

            //Complication colors
            mComplicationColor = prefs.getInt("settings_complication_color_value", defaultComplications);
            mComplicationTitleColor = Color.argb(Math.round(169), Color.red(mComplicationColor),
                    Color.green(mComplicationColor), Color.blue(mComplicationColor));
            mComplicationBorderColor = Color.argb(Math.round(69), Color.red(mComplicationColor),
                    Color.green(mComplicationColor), Color.blue(mComplicationColor));

            //Notification indicator colors
            mNotificationTextColor = mBackgroundColor;
            mNotificationCircleColor = mHourTickColor;
        }

        /**
         * Loads the Night Mode color settings
         */
        private void loadNightModeColorPrefs() {
            final SharedPreferences prefs
                    = PreferenceManager.getDefaultSharedPreferences(mContext);

            //Default night mode colors
            final int defaultHour = getColor(R.color.default_current_hour_tick_night_mode);
            final int defaultTick = getColor(R.color.default_hour_tick_night_mode);
            final int defaultMinutes = getColor(R.color.default_minute_text_night_mode);
            final int defaultBackground = getColor(R.color.default_background_night_mode);
            final int defaultComplications = getColor(R.color.default_complications_night_mode);

            //Tick colors
            mHourTickColor = prefs.getInt("settings_hour_tick_night_mode_color_value", defaultHour);
            mTickColor = prefs.getInt("settings_tick_night_mode_color_value", defaultTick);

            //Minute text colors
            mMinuteTextColor = prefs.getInt("settings_minute_text_night_mode_color_value", defaultMinutes);

            //Background colors
            mBackgroundColor = prefs.getInt("settings_background_night_mode_color_value", defaultBackground);

            //Complication colors
            mComplicationColor = prefs.getInt("settings_complication_night_mode_color_value", defaultComplications);
            mComplicationTitleColor = Color.argb(Math.round(169), Color.red(mComplicationColor),
                    Color.green(mComplicationColor), Color.blue(mComplicationColor));
            mComplicationBorderColor = Color.argb(Math.round(69), Color.red(mComplicationColor),
                    Color.green(mComplicationColor), Color.blue(mComplicationColor));

            //Notification indicator colors
            mNotificationTextColor = mBackgroundColor;
            mNotificationCircleColor = mHourTickColor;
        }

        /**
         * Loads the non-color settings
         */
        private void loadMiscPrefs() {
            final SharedPreferences prefs
                    = PreferenceManager.getDefaultSharedPreferences(mContext);

            //Complication borders & showing/hiding the second hand
            mShowComplicationBorder = prefs.getBoolean("settings_complication_border", true);
            mHideHourTicks = prefs.getBoolean("settings_hide_hour_ticks", false);

            //Handles font selection
            final String minuteFont = prefs.getString("settings_minute_font", null);

            mFontNeedsAdjustment = false;

            if (minuteFont != null) {
                switch (minuteFont) {
                    case "0":
                        mMinuteTextFont
                                = Typeface.create("sans-serif-regular", Typeface.NORMAL);
                        break;
                    case "1":
                        mMinuteTextFont
                                = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                        break;
                    case "2":
                        mMinuteTextFont
                                = Typeface.createFromAsset(getAssets(), "RobotoMono-Medium.ttf");
                        break;
                    case "3":
                        mMinuteTextFont
                                = Typeface.createFromAsset(getAssets(), "Neucha.ttf");
                        mFontNeedsAdjustment = true;
                        break;
                }
            } else {
                mMinuteTextFont = Typeface.create("sans-serif-regular", Typeface.NORMAL);
            }

            //Notification indicator
            final String notificationIndicator
                    = prefs.getString("settings_notification_indicator", null);
            mNotificationIndicatorUnread
                    = notificationIndicator != null && notificationIndicator.equals("1");
            mNotificationIndicatorAll
                    = notificationIndicator != null && notificationIndicator.equals("2");
            mShowNotificationIndicator
                    = (mNotificationIndicatorAll || mNotificationIndicatorUnread);

            //Night mode
            mNightModeEnabled = prefs.getBoolean("settings_night_mode_enabled", false);

            if (mNightModeEnabled) {
                mNightModeStartTimeMillis = prefs.getLong("settings_night_mode_start_time",
                        Long.valueOf(getString(R.string.settings_night_mode_default_start_time)));
                mNightModeEndTimeMillis = prefs.getLong("settings_night_mode_end_time",
                        Long.valueOf(getString(R.string.settings_night_mode_default_end_time)));
            }

            mManualNightModeEnabled = prefs.getBoolean("settings_night_mode_manual_enabled", false);
            mForceNightMode = prefs.getBoolean("force_night_mode", false);
        }


        /**
         * Handles changing timezones
         */
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /**
         * Handler to update the time once a second when viewing the watch face
         */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();

                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);

                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            //Updates complications to properly render in Ambient Mode based on device
            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                final ComplicationDrawable complicationDrawable
                        = mComplicationDrawableSparseArray.get(COMPLICATION_ID);

                if (complicationDrawable != null) {
                    complicationDrawable.setLowBitAmbient(mLowBitAmbient);
                    complicationDrawable.setBurnInProtection(mBurnInProtection);
                }
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        /**
         * Register a receiver for handling timezone changes
         */
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            Just1MinuteWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        /**
         * Unregister a receiver for handling timezone changes
         */
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            Just1MinuteWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * @return whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run in active mode
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Determines if the watch face is in night mode or not
         *
         * @return whether or not night mode colors should be enabled
         */
        private boolean isInNightMode() {
            if (mNightModeEnabled) {
                return isTimeInRange(mCalendar.getTimeInMillis(),
                        mNightModeStartTimeMillis, mNightModeEndTimeMillis);
            } else {
                return mForceNightMode;
            }
        }
    }
}