package com.marz.snapprefs;


import android.content.Context;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class Saving {

    public static final String SNAPCHAT_PACKAGE_NAME = "com.snapchat.android";
    // Modes for saving Snapchats
    public static final int SAVE_AUTO = 0;
    public static final int SAVE_S2S = 1;
    public static final int DO_NOT_SAVE = 2;
    // Length of toasts
    public static final int TOAST_LENGTH_SHORT = 0;
    public static final int TOAST_LENGTH_LONG = 1;
    // Minimum timer duration disabled
    public static final int TIMER_MINIMUM_DISABLED = 0;
    private static final String PACKAGE_NAME = HookMethods.class.getPackage().getName();
    // Preferences and their default values
    public static int mModeSnapImage = SAVE_AUTO;
    public static int mModeSnapVideo = SAVE_AUTO;
    public static int mModeStoryImage = SAVE_AUTO;
    public static int mModeStoryVideo = SAVE_AUTO;
    public static int mTimerMinimum = TIMER_MINIMUM_DISABLED;
    public static boolean mTimerUnlimited = true;
    public static boolean mHideTimer = false;
    public static boolean mToastEnabled = true;
    public static int mToastLength = TOAST_LENGTH_LONG;
    public static String mSavePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Snapprefs";
    public static boolean mSaveSentSnaps = false;
    public static boolean mSortByCategory = true;
    public static boolean mSortByUsername = true;
    public static boolean mDebugging = true;
    public static boolean mOverlays = false;
    public static boolean viewingSnap;
    public static Object receivedSnap;
    public static Object oldreceivedSnap;
    public static boolean usedOldReceivedSnap = false;
    public static Resources mSCResources;
    public static FileInputStream mVideo;
    public static String mVideoUri;
    public static Bitmap mImage;
    public static ClassLoader snapCL;
    public static Bitmap image;
    public static FileInputStream video;
    static XSharedPreferences prefs;
    static int counter = 0;
    static SnapType lastSnapType;
    static String lastSender;
    static Date lastTimestamp;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault());
    private static XModuleResources mResources;
    private static GestureModel gestureModel;
    private static int screenHeight;
    ;

    static void initSaving(final XC_LoadPackage.LoadPackageParam lpparam, final XModuleResources modRes, final Context snapContext) {
        mResources = modRes;
        if (mSCResources == null) mSCResources = snapContext.getResources();
        refreshPreferences();

        try {
            /**
             * We hook this method to get the newly set VideoUri.
             */
            findAndHookMethod(VideoView.class, "setVideoURI", Uri.class, Map.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    //We have to store the file data before snapchat deletes it
                    try {
                        counter++;
                        if (mVideoUri != null) {
                            String lastVideoUri = param.args[0].toString();
                            if (mVideoUri.equals(lastVideoUri)) {
                                //Toast.makeText(snapContext,"The both URIs are the same: ", Toast.LENGTH_SHORT).show();
                                Logger.log("The URIs are the same.", true);
                                return;
                            } else {
                                mVideoUri = lastVideoUri;
                                mVideo = new FileInputStream(param.args[0].toString());
                                //Toast.makeText(snapContext,"The both URIs are NOT the same: ", Toast.LENGTH_SHORT).show();
                                Logger.log("The URIs are NOT the same.", true);
                                saveReceivedSnap(snapContext, receivedSnap, MediaType.VIDEO);
                            }

                        } else {
                            mVideoUri = param.args[0].toString();
                            mVideo = new FileInputStream(param.args[0].toString());
                            Logger.log(param.args[0].toString(), true);
                            Logger.log("mVideoUri is null", true);
                            saveReceivedSnap(snapContext, receivedSnap, MediaType.VIDEO);
                        }
                        //mVideoUri = param.args[0].toString();
                        //mVideo = new FileInputStream(param.args[0].toString());
                        Logger.log(param.args[0].toString(), true);
                        //Logger.log("mVideoUri is null", true);
                        //saveReceivedSnap(snapContext, receivedSnap, MediaType.VIDEO);
                        //Logger.log("It is a Video", true);
                        //Toast.makeText(snapContext,"Video, counter: "+counter, Toast.LENGTH_SHORT).show();
                        //mVideo = null;
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });

            /**
             * We hook this method to get the BitmapDrawable currently displayed.
             */
            findAndHookMethod(ImageView.class, "updateDrawable", Drawable.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (!mSCResources.getResourceName(((View) param.thisObject).getId()).equals(Common.basename + ":id/snap_image_view"))
                            return;

                        if (((BitmapDrawable) param.args[0]).getBitmap() == null) return;
                        Bitmap lastImage = ((BitmapDrawable) param.args[0]).getBitmap();
                        if (mImage != null) {
                            if (mImage.sameAs(lastImage)) {
                                //Toast.makeText(snapContext,"The both bitmap are the same: ", Toast.LENGTH_SHORT).show();
                                return;
                            } else {
                                mImage = lastImage;
                                //Toast.makeText(snapContext,"The both bitmap are NOT the same: ", Toast.LENGTH_SHORT).show();
                                saveReceivedSnap(snapContext, receivedSnap, MediaType.IMAGE);
                            }
                        } else {
                            mImage = lastImage;
                            //Toast.makeText(snapContext,"First was null, now we are saving, number: "+counter, Toast.LENGTH_SHORT).show();
                            saveReceivedSnap(snapContext, receivedSnap, MediaType.IMAGE);
                        }
                        //Logger.log("It is a Bitmap", true);
                        //Toast.makeText(snapContext,"Bitmap, counter: "+counter, Toast.LENGTH_SHORT).show();
                    } catch (NullPointerException | Resources.NotFoundException ignore) {
                        //Sometimes getResourceName is going to return null that's okay
                    }
                }
            });
            /**
             * We hook this method to get the BitmapDrawable currently displayed.
             */
            if (mOverlays == true) {
            findAndHookMethod(ImageView.class, "updateDrawable", Drawable.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (!mSCResources.getResourceName(((View) param.thisObject).getId()).equals(Common.basename + ":id/snap_video_image_overlay"))
                            return;
                        if (((BitmapDrawable) param.args[0]).getBitmap() == null) return;
                        Bitmap lastImage = ((BitmapDrawable) param.args[0]).getBitmap();
                        if (mImage != null) {
                            if (mImage.sameAs(lastImage)) {
                                //Toast.makeText(snapContext,"The both bitmap are the same: ", Toast.LENGTH_SHORT).show();
                                return;
                            } else {
                                mImage = lastImage;
                                //Toast.makeText(snapContext,"The both bitmap are NOT the same: ", Toast.LENGTH_SHORT).show();
                                saveReceivedSnap(snapContext, receivedSnap, MediaType.IMAGE_OVERLAY);
                            }
                        } else {
                            mImage = lastImage;
                            //Toast.makeText(snapContext,"First was null, now we are saving, number: "+counter, Toast.LENGTH_SHORT).show();
                            saveReceivedSnap(snapContext, receivedSnap, MediaType.IMAGE_OVERLAY);
                        }
                        //Logger.log("It is a Bitmap", true);
                        //Toast.makeText(snapContext,"Bitmap, counter: "+counter, Toast.LENGTH_SHORT).show();
                    } catch (NullPointerException | Resources.NotFoundException ignore) {
                        //Sometimes getResourceName is going to return null that's okay
                    }
                }
            });
            }

            /**
             * When the SnapView.a method gets called to show the actual snap, therefore it can be
             * used to determine if we are viewing the actual Snap or not.
             */

            findAndHookMethod(Obfuscator.save.SNAPVIEW_CLASS, lpparam.classLoader, Obfuscator.save.SNAPVIEW_SHOW, findClass(Obfuscator.save.SNAPVIEW_SHOW_FIRST, lpparam.classLoader), findClass(Obfuscator.save.SNAPVIEW_SHOW_SECOND, lpparam.classLoader), findClass(Obfuscator.save.SNAPVIEW_SHOW_THIRD, lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    viewingSnap = true;
                    Logger.log("Starting to view a snap, plus viewingSnap: " + viewingSnap, true);
                }
            });

            XC_MethodHook gestureMethodHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (gestureModel == null || gestureModel.isSaved()) return;
                    Logger.log("GestureHook: Not saved nor null", true);
                    MotionEvent motionEvent = (MotionEvent) param.args[0];
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE) {
                        Logger.log("GestureHook: action_move is done", true);
                        Logger.log("GestureHook: action_move is done", true);
                        if (!viewingSnap) return;
                        // Result true means the event is handled
                        param.setResult(true);

                        if (!gestureModel.isInitialized()) {
                            gestureModel.initialize(motionEvent.getRawX(), motionEvent.getRawY());
                        } else if (!gestureModel.isSaved()) {
                            float deltaX = (motionEvent.getRawX() - gestureModel.getStartX());
                            float deltaY = (motionEvent.getRawY() - gestureModel.getStartY());
                            // Pythagorean theorem to calculate the distance between to points
                            float currentDistance = (float) Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));

                            // Distance is bigger than previous, re-set reference point
                            if (currentDistance > gestureModel.getDistance()) {
                                gestureModel.setDistance(currentDistance);
                            } else { // On its way back
                                // Meaning it's at least 70% back to the start point and the gesture was longer then 20% of the screen
                                if ((currentDistance < (gestureModel.getDistance() * 0.3)) && (gestureModel.getDistance() > (gestureModel.getDisplayHeight() * 0.2))) {
                                    gestureModel.setSaved();
                                    saveReceivedSnap(snapContext, gestureModel.getReceivedSnap(), gestureModel.mediaType);
                                }
                            }
                        }
                    }
                }
            };

            final Class<?> snapImagebryo = findClass("akh", lpparam.classLoader);
            final Class<?> mediabryoClass = findClass("com.snapchat.android.model.Mediabryo", lpparam.classLoader);

            /**
             * Method which gets called to prepare an image for sending (before selecting contacts).
             * We check whether it's an image or a video and save it.
             */
            findAndHookMethod("com.snapchat.android.preview.SnapPreviewFragment", lpparam.classLoader, "n", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    refreshPreferences();
                    Logger.log("----------------------- SNAPPREFS/Sent Snap ------------------------", false);

                    if (!mSaveSentSnaps) {
                        Logger.log("Not saving sent snap");
                        return;
                    }
                    Logger.log("Saving sent snap");
                    try {
                        final Context context = (Context) callMethod(param.thisObject, "getActivity");
                        Logger.log("We have the Context", true);
                        Object mediabryo = getObjectField(param.thisObject, "a"); //ajk is AnnotatedMediabryo, in SnapPreviewFragment
                        Logger.log("We have the MediaBryo", true);
                        final String fileName = dateFormat.format(new Date());
                        Logger.log("We have the filename " + fileName, true);

                        // Check if instance of SnapImageBryo and thus an image or a video
                        if (snapImagebryo.isInstance(mediabryo)) {
                            Logger.log("The sent snap is an Image", true);
                            findAndHookMethod("ajk", lpparam.classLoader, "a", Bitmap.class, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    image = (Bitmap) param.args[0];
                                    Logger.log("Saving sent IMAGE SNAP", true);
                                    saveSnap(SnapType.SENT, MediaType.IMAGE, context, image, null, fileName, null);
                                }
                            });
                        } else {
                            Logger.log("The sent snap is a Video", true);
                            findAndHookMethod("com.snapchat.android.model.Mediabryo", lpparam.classLoader, "c", mediabryoClass, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Uri videoUri = (Uri) param.getResult();
                                    Logger.log("We have the URI " + videoUri.toString(), true);
                                    video = new FileInputStream(videoUri.toString());
                                    Logger.log("Saving sent VIDEO SNAP", true);
                                    saveSnap(SnapType.SENT, MediaType.VIDEO, context, null, video, fileName, null);
                                }
                            });
                        }
                    } catch (Throwable t) {
                        Logger.log("Saving sent snaps failed", true);
                        Logger.log(t.toString(), true);
                    }
                }
            });

            /**
             * We hook this method to get the ChatImage from the imageView of ImageResourceView,
             * then we get the properties and save the actual Image.
             */
            final Class<?> imageResourceViewClass = findClass(Obfuscator.save.IMAGERESOURCEVIEW_CLASS, lpparam.classLoader);
            hookAllConstructors(imageResourceViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final ImageView imageView = (ImageView) param.thisObject;
                    imageView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            Logger.log("----------------------- SNAPPREFS ------------------------", false);
                            Logger.log("Long press on chat image detected");

                            Bitmap chatImage = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                            Logger.log("We have the chat image", true);
                            Object imageResource = getObjectField(param.thisObject, Obfuscator.save.IMAGERESOURCEVIEW_VAR_IMAGERESOURCE);
                            Logger.log("We have the imageResource", true);
                            Object chatMedia = getObjectField(imageResource, Obfuscator.save.IMAGERESOURCE_VAR_CHATMEDIA); // in ImageResource
                            Logger.log("We have the chatMedia", true);
                            Long timestamp = (Long) callMethod(chatMedia, Obfuscator.save.CHAT_GETTIMESTAMP); // model.chat.Chat
                            Logger.log("We have the timestamp " + timestamp.toString(), true);
                            String sender = (String) callMethod(chatMedia, Obfuscator.save.STATEFULCHATFEEDITEM_GETSENDER); //in StatefulChatFeedItem
                            Logger.log("We have the sender " + sender, true);
                            String filename = sender + "_" + dateFormat.format(timestamp);
                            Logger.log("We have the file name " + filename, true);

                            saveSnap(SnapType.CHAT, MediaType.IMAGE, imageView.getContext(), chatImage, null, filename, sender);
                            return true;
                        }
                    });
                }
            });
            /**
             * We hook this method to set the CanonicalDisplayTime to our desired one if it is under
             * our limit and hide the counter if we need it.
             */

            findAndHookMethod(Obfuscator.save.RECEIVEDSNAP_CLASS, lpparam.classLoader, Obfuscator.save.RECEIVEDSNAP_DISPLAYTIME, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Double currentResult = (Double) param.getResult();
                    if (mTimerUnlimited == true) {
                        findAndHookMethod("com.snapchat.android.ui.SnapTimerView", lpparam.classLoader, "onDraw", Canvas.class, XC_MethodReplacement.DO_NOTHING);
                        param.setResult((double) 9999.9F);
                    } else {
                        if ((double) mTimerMinimum != TIMER_MINIMUM_DISABLED && currentResult < (double) mTimerMinimum) {
                            param.setResult((double) mTimerMinimum);
                        }
                    }
                }
            });
            if (mTimerUnlimited == true || mHideTimer == true) {
                findAndHookMethod("com.snapchat.android.ui.SnapTimerView", lpparam.classLoader, "onDraw", Canvas.class, XC_MethodReplacement.DO_NOTHING);
            }
           /* Class<?> snapView = findClass(Obfuscator.save.SNAPVIEW_CLASS, lpparam.classLoader);
            hookAllConstructors(snapView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Logger.log("We have hooked the constructor", true);
                    View snapTimerView = (View) getObjectField(param.thisObject, "j");
                    Logger.log("We have j as snapTimerView", true);
                    snapTimerView.setVisibility(View.INVISIBLE);
                    Logger.log("We have set j as Invisible", true);
                }
            }); */
            /**
             * We hook this method to handle our gestures made in the SC app itself.
             */
            findAndHookMethod(Obfuscator.save.LANDINGPAGEACTIVITY_CLASS, lpparam.classLoader, "dispatchTouchEvent", MotionEvent.class, gestureMethodHook);
            /**
             * We hook SnapView.c once again to get the receivedSnap argument, then store it along with the classLoader.
             */
            findAndHookMethod(Obfuscator.save.SNAPVIEW_CLASS, lpparam.classLoader, Obfuscator.save.SNAPVIEW_SHOW, findClass(Obfuscator.save.SNAPVIEW_SHOW_FIRST, lpparam.classLoader), findClass(Obfuscator.save.SNAPVIEW_SHOW_SECOND, lpparam.classLoader), findClass(Obfuscator.save.SNAPVIEW_SHOW_THIRD, lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Logger.log("Starting to view a snap");
                    receivedSnap = param.args[0];
                    oldreceivedSnap = receivedSnap;
                    //Call for savereceivedsnap
                    snapCL = lpparam.classLoader;
                }
            });
            /**
             * We hook SnapView.a to determine wether we have stopped viewing the Snap.
             */
            findAndHookMethod(Obfuscator.save.SNAPVIEW_CLASS, lpparam.classLoader, Obfuscator.save.SNAPVIEW_HIDE, findClass(Obfuscator.save.ENDREASON_CLASS, lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    //Logger.log("Stopped viewing the Snap", true);
                    viewingSnap = false;
                    mImage = null;
                    mVideo = null;
                    mVideoUri = null;
                }
            });
            /**
             * Sets the Snap as Screenshotted, so we constantly return false to it.
             */
            findAndHookMethod(Obfuscator.save.SNAP_CLASS, lpparam.classLoader, Obfuscator.save.SNAP_ISSCREENSHOTTED, XC_MethodReplacement.returnConstant(false));


        } catch (Exception e) {
            Logger.log("Error occured: Snapprefs doesn't currently support this version, wait for an update", e);

            findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Toast.makeText((Context) param.thisObject, "This version of snapchat is currently not supported by Snapprefs.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private static void saveReceivedSnap(Context context, Object receivedSnap, MediaType mediaType) {
        Logger.log("----------------------- SNAPPREFS ------------------------", false);
        String sender = null;
        SnapType snapType = null;
        Date timestamp;
        //Logger.log("saveReceivedSnap - 1 -" + mediaType.toString(), true);
        if (mediaType == MediaType.IMAGE || mediaType == MediaType.GESTUREDIMAGE) {
            if (receivedSnap == null) {
                receivedSnap = oldreceivedSnap;
                usedOldReceivedSnap = true;
                //Logger.log("saveReceivedSnap - 1 - IMAGE, NULL", true);
            } else {
                usedOldReceivedSnap = false;
                //Logger.log("saveReceivedSnap - 1 - IMAGE, NOT NULL", true);
            }
        } else if (mediaType == MediaType.VIDEO || mediaType == MediaType.GESTUREDVIDEO) {
            if (receivedSnap == null) {
                //receivedSnap = oldreceivedSnap;
                //Logger.log("saveReceivedSnap - 2 - VIDEO NULL", true);
                usedOldReceivedSnap = true;
                //return;
            }
            usedOldReceivedSnap = false;
            //Logger.log("saveReceivedSnap - 2 - VIDEO NOT NULL", true);
        } else if (mediaType == mediaType.IMAGE_OVERLAY) {
            if (receivedSnap == null) {
                receivedSnap = oldreceivedSnap;
                usedOldReceivedSnap = true;
                //Logger.log("saveReceivedSnap - 2 - OVERLAY, NULL", true);
            } else {
                usedOldReceivedSnap = false;
                //Logger.log("saveReceivedSnap - 2 - OVERLAY, NOT NULL", true);
            }
        }
        //Logger.log("saveReceivedSnap - 3 - " + mediaType.toString(), true);
        try {
            sender = (String) getObjectField(receivedSnap, "mSender");
            //Logger.log("saveReceivedSnap - 3 - mSender- " + mediaType.toString(), true);
        } catch (NullPointerException ignore) {
            //Logger.log("saveReceivedSnap - 3 - mSender NULL- " + mediaType.toString(), true);
        }
        //Logger.log("saveReceivedSnap - 4- " + mediaType.toString(), true);
        if (receivedSnap != null) {
            if (sender == null) { //This means its a story
                Class<?> storySnap = findClass(Obfuscator.save.STORYSNAP_CLASS, snapCL);
                try {
                    sender = (String) getObjectField(storySnap.cast(receivedSnap), "mUsername");
                } catch (Exception e) {
                    Logger.log(e.toString(), true);
                }
                setAdditionalInstanceField(receivedSnap, "snap_type", SnapType.STORY);
                lastSnapType = SnapType.STORY;
            } else {
                setAdditionalInstanceField(receivedSnap, "snap_type", SnapType.SNAP);
                lastSnapType = SnapType.SNAP;
            }
            snapType = (SnapType) removeAdditionalInstanceField(receivedSnap, "snap_type");
            timestamp = new Date((Long) callMethod(receivedSnap, Obfuscator.save.SNAP_GETTIMESTAMP)); //Gettimestamp-Snap
            lastSender = sender;
            lastTimestamp = timestamp;
            usedOldReceivedSnap = false;
        } else {
            sender = lastSender;
            timestamp = lastTimestamp;
            snapType = lastSnapType;
            usedOldReceivedSnap = true;
        }

        String filename = sender + "_" + dateFormat.format(timestamp);
        Logger.log("usedOldReceivedSnap = " + usedOldReceivedSnap, true);
        if (usedOldReceivedSnap) {
            filename = filename + "_1";
        }
        try {
            image = mImage;
            video = mVideo;
        } catch (NullPointerException ignore) {
        }
        switch (mediaType) {
            case VIDEO: {
                //setAdditionalInstanceField(receivedSnap, "snap_media_type", MediaType.VIDEO);
                Logger.log("Video " + snapType.name + " opened");
                int saveMode = (snapType == SnapType.SNAP ? mModeSnapVideo : mModeStoryVideo);
                if (saveMode == SAVE_S2S) {
                    saveMode = SAVE_AUTO;
                }
                if (saveMode == DO_NOT_SAVE) {
                    Logger.log("Mode: don't save");
                } else if (saveMode == SAVE_S2S) {
                    Logger.log("Mode: sweep to save");
                    gestureModel = new GestureModel(receivedSnap, screenHeight, MediaType.GESTUREDVIDEO);
                } else {
                    Logger.log("Mode: auto save");
                    gestureModel = null;
                    saveSnap(snapType, MediaType.VIDEO, context, null, video, filename, sender);
                }
                break;
            }
            case IMAGE: {
                //setAdditionalInstanceField(receivedSnap, "snap_media_type", MediaType.IMAGE);
                Logger.log("Image " + snapType.name + " opened");
                int saveMode = (snapType == SnapType.SNAP ? mModeSnapImage : mModeStoryImage);
                if (saveMode == SAVE_S2S) {
                    saveMode = SAVE_AUTO;
                }
                if (saveMode == DO_NOT_SAVE) {
                    Logger.log("Mode: don't save");
                } else if (saveMode == SAVE_S2S) {
                    Logger.log("Mode: sweep to save");
                    gestureModel = new GestureModel(receivedSnap, screenHeight, MediaType.GESTUREDIMAGE);
                } else {
                    Logger.log("Mode: auto save");
                    gestureModel = null;
                    saveSnap(snapType, MediaType.IMAGE, context, image, null, filename, sender);
                }
                break;
            }
            case IMAGE_OVERLAY: {
                int saveMode = (snapType == SnapType.SNAP ? mModeSnapVideo : mModeStoryVideo);
                if (saveMode == SAVE_S2S) {
                    saveMode = SAVE_AUTO;
                }
                if (saveMode == DO_NOT_SAVE) {
                } else if (saveMode == SAVE_S2S) {
                    gestureModel = new GestureModel(receivedSnap, screenHeight, MediaType.GESTUREDVIDEO);
                } else {
                    gestureModel = null;
                    saveSnap(snapType, MediaType.IMAGE_OVERLAY, context, image, null, filename, sender);
                }
                break;
            }
            case GESTUREDIMAGE: {
                Logger.log("GESTUREDIMAGE is coming", true);
                saveSnap(snapType, MediaType.IMAGE, context, image, null, filename, sender);
                break;
            }
            case GESTUREDVIDEO: {
                Logger.log("GESTUREDVIDEO is coming", true);
                saveSnap(snapType, MediaType.VIDEO, context, null, video, filename, sender);
                break;
            }
            default: {
                Logger.log("Unknown MediaType");
            }
        }
    }

    public static void saveSnap(SnapType snapType, MediaType mediaType, Context context, Bitmap image, FileInputStream video, String filename, String sender) {
        File directory;
        try {
            directory = createFileDir(snapType.subdir, sender);
        } catch (IOException e) {
            Logger.log(e);
            return;
        }

        File imageFile = new File(directory, filename + MediaType.IMAGE.fileExtension);
        File overlayFile = new File(directory, filename + "_overlay" + counter + MediaType.IMAGE_OVERLAY.fileExtension);
        File videoFile = new File(directory, filename + MediaType.VIDEO.fileExtension);

        if (mediaType == MediaType.IMAGE) {
            if (imageFile.exists()) {
                Logger.log("Image already exists");
                showToast(context, mResources.getString(R.string.image_exists));
                return;
            }

            if (saveImageJPG(image, imageFile)) {
                showToast(context, mResources.getString(R.string.image_saved));
                Logger.log("Image " + snapType.name + " has been saved");
                Logger.log("Path: " + imageFile.toString());

                runMediaScanner(context, imageFile.getAbsolutePath());
                Logger.log("Saving image", true);
            } else {
                showToast(context, mResources.getString(R.string.image_not_saved));
            }
        } else if (mediaType == MediaType.IMAGE_OVERLAY) {
                if (overlayFile.exists()) {
                    Logger.log("VideoOverlay already exists");
                    showToast(context, mResources.getString(R.string.video_exists));
                    return;
                }

            if (saveImagePNG(image, overlayFile)) {
                    //showToast(context, "This overlay ");
                    Logger.log("VideoOverlay " + snapType.name + " has been saved");
                    Logger.log("Path: " + overlayFile.toString());
                    runMediaScanner(context, overlayFile.getAbsolutePath());
                } else {
                    showToast(context, "An error occured while saving this overlay.");
                }
        } else if (mediaType == MediaType.VIDEO) {
            if (videoFile.exists()) {
                Logger.log("Video already exists");
                showToast(context, mResources.getString(R.string.video_exists));
                return;
            }

            if (saveVideo(video, videoFile)) {
                showToast(context, mResources.getString(R.string.video_saved));
                Logger.log("Video " + snapType.name + " has been saved");
                Logger.log("Path: " + videoFile.toString());
                runMediaScanner(context, videoFile.getAbsolutePath());
            } else {
                showToast(context, mResources.getString(R.string.video_not_saved));
            }
        }
        image = null;
        video = null;
        receivedSnap = null;
        viewingSnap = false;
    }

    public static File createFileDir(String category, String sender) throws IOException {
        File directory = new File(mSavePath);

        if (mSortByCategory || (mSortByUsername && sender == null)) {
            directory = new File(directory, category);
        }

        if (mSortByUsername && sender != null) {
            directory = new File(directory, sender);
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory " + directory);
        }

        return directory;
    }

    // function to saveimage
    public static boolean saveImageJPG(Bitmap image, File fileToSave) {
        try {
            FileOutputStream out = new FileOutputStream(fileToSave);
            image.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
            Logger.log("SAVEIMAGE-JPG DONE", true);
            return true;
        } catch (Exception e) {
            Logger.log("Exception while saving an image", e);
            return false;
        }
    }

    public static boolean saveImagePNG(Bitmap image, File fileToSave) {
        try {
            FileOutputStream out = new FileOutputStream(fileToSave);
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            Logger.log("SAVEIMAGE-PNG DONE", true);
            return true;
        } catch (Exception e) {
            Logger.log("Exception while saving an image", e);
            return false;
        }
    }

    // function to save video
    private static boolean saveVideo(FileInputStream video, File fileToSave) {
        try {
            FileInputStream in = video;
            //Logger.log(in.toString(), true);
            FileOutputStream out = new FileOutputStream(fileToSave);
            //Logger.log(out.toString(), true);

            byte[] buf = new byte[1024];
            int len;

            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            in.close();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            Logger.log("Exception while saving a video", e);
            return false;
        }
    }

    /*
     * Tells the media scanner to scan the newly added image or video so that it
     * shows up in the gallery without a reboot. And shows a Toast message where
     * the media was saved.
     * @param context Current context
     * @param filePath File to be scanned by the media scanner
     */
    public static void runMediaScanner(Context context, String... mediaPath) {
        try {
            Logger.log("MediaScanner started");
            MediaScannerConnection.scanFile(context, mediaPath, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Logger.log("MediaScanner scanned file: " + uri.toString());
                        }
                    });
        } catch (Exception e) {
            Logger.log("Error occurred while trying to run MediaScanner", e);
        }
    }

    public static void showToast(Context context, String toastMessage) {
        if (mToastEnabled) {
            if (mToastLength == TOAST_LENGTH_SHORT) {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
            }
        }
    }

    static void refreshPreferences() {

        prefs = new XSharedPreferences(new File(
                Environment.getDataDirectory(), "data/"
                + PACKAGE_NAME + "/shared_prefs/" + PACKAGE_NAME
                + "_preferences" + ".xml"));
        prefs.reload();

        mModeSnapImage = prefs.getInt("pref_key_snaps_images", mModeSnapImage);
        mModeSnapVideo = prefs.getInt("pref_key_snaps_videos", mModeSnapVideo);
        mModeStoryImage = prefs.getInt("pref_key_stories_images", mModeStoryImage);
        mModeStoryVideo = prefs.getInt("pref_key_stories_videos", mModeStoryVideo);
        mTimerMinimum = prefs.getInt("pref_key_timer_minimum", mTimerMinimum);
        mToastEnabled = prefs.getBoolean("pref_key_toasts_checkbox", mToastEnabled);
        mToastLength = prefs.getInt("pref_key_toasts_duration", mToastLength);
        mSavePath = prefs.getString("pref_key_save_location", mSavePath);
        mSaveSentSnaps = prefs.getBoolean("pref_key_save_sent_snaps", mSaveSentSnaps);
        mSortByCategory = prefs.getBoolean("pref_key_sort_files_mode", mSortByCategory);
        mSortByUsername = prefs.getBoolean("pref_key_sort_files_username", mSortByUsername);
        mDebugging = prefs.getBoolean("pref_key_debug_mode", mDebugging);
        mOverlays = prefs.getBoolean("pref_key_overlay", mOverlays);
        mTimerUnlimited = prefs.getBoolean("pref_key_timer_unlimited", mTimerUnlimited);
        mHideTimer = prefs.getBoolean("pref_key_timer_hide", mHideTimer);

    }

    public enum SnapType {
        SNAP("snap", "/ReceivedSnaps"),
        STORY("story", "/Stories"),
        SENT("sent", "/SentSnaps"),
        CHAT("chat", "/Chat");

        private final String name;
        private final String subdir;

        SnapType(String name, String subdir) {
            this.name = name;
            this.subdir = subdir;
        }
    }

    public enum MediaType {
        IMAGE(".jpg"),
        IMAGE_OVERLAY(".png"),
        VIDEO(".mp4"),
        GESTUREDIMAGE(".jpg"),
        GESTUREDVIDEO(".mp4");

        private final String fileExtension;

        MediaType(String fileExtension) {
            this.fileExtension = fileExtension;
        }
    }
}

