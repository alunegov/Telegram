package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip2.VoIPEncKeyView;
import org.telegram.ui.Components.voip2.VoIPNotificationView;
import org.telegram.ui.Components.voip2.VoIPToggleButton;
import org.telegram.ui.Components.voip2.VoIPWavesContainer;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class VoIPFragment2 implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    private static String TAG = "VF2";

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final static int COLOR_DARK = ColorUtils.setAlphaComponent(Color.BLACK, 50);
    private final static int COLOR_LIGHT = ColorUtils.setAlphaComponent(Color.WHITE, 50);

    private static VoIPFragment2 instance;

    private Activity activity;
    private int currentAccount;

    //TLRPC.User currentUser;
    TLRPC.User callingUser;

    private ViewGroup fragmentView;

    MotionBackgroundDrawable background;

    private VoIPFloatingLayout callingUserMiniFloatingLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private boolean currentUserCameraIsFullscreen;

    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;
    private TextureViewRenderer callingUserMiniTextureRenderer;

    ImageView backIcon;

    VoIPEncKeyView encKeyView;

    LinearLayout statusLayout;
    TextView callingUserTitle;
    VoIPStatusTextView statusTextView;
    VoIPNotificationView weakSignalNotification;

    VoIPNotificationsLayout notificationsLayout;

    FrameLayout inButtonsLayout;
    VoIPToggleButton acceptButtonView;
    VoIPToggleButton declineButtonView;

    VoIPButtonsLayout buttonsLayout;
    VoIPToggleButton[] bottomButtons = new VoIPToggleButton[4];
    Emoji.EmojiDrawable[] emojiDrawables = new Emoji.EmojiDrawable[4];

    View topShadow;
    View bottomShadow;

    private PrivateVideoPreviewDialog previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;
    private boolean emojiLoaded;
    private boolean callingUserIsVideo;
    private boolean currentUserIsVideo;
    private boolean lockOnScreen;
    private boolean screenWasWakeup;
    private boolean isVideoCall;

    private boolean uiVisible = true;
    private boolean canHideUI;
    boolean cameraForceExpanded;
    long lastContentTapTime;
    private Animator cameraShowingAnimator;
    private boolean isWeakSignal;
    private BackupImageView callingUserPhotoViewMini;
    private boolean deviceIsLocked;
    private boolean fragmentLockOnScreen;
    private boolean canSwitchToPip;
    private boolean switchingToPip;
    boolean enterFromPiP;
    private boolean isFinished;

    boolean hideUiRunnableWaiting;
    Runnable hideUIRunnable = () -> {
        hideUiRunnableWaiting = false;
        if (canHideUI && uiVisible && !encKeyView.getIsExpanded()) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }
    };

    VoIPWavesContainer callingUserPhotoWavesContainer;
    VoIPWavesContainer acceptButtonWavesContainer;

    private boolean acceptAnimShowed;

    public static VoIPFragment2 getInstance() {
        return instance;
    }

    public static void show(Activity activity, int account) {
        show(activity, false, account);
    }

    public static void show(Activity activity, boolean overlay, int account) {
        if (instance != null && instance.fragmentView.getParent() == null) {
            if (instance != null) {
                instance.callingUserTextureView.renderer.release();
                instance.currentUserTextureView.renderer.release();
                instance.callingUserMiniTextureRenderer.release();
                instance.destroy();
            }
            instance = null;
        }
        if (instance != null || activity.isFinishing()) {
            return;
        }
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().getUser() == null) {
            return;
        }

        VoIPFragment2 fragment = new VoIPFragment2(activity, account);
        instance = fragment;

        View fragmentView = fragment.createView(activity);

        fragment.deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }
        fragment.screenWasWakeup = !screenOn;

        fragment.fragmentLockOnScreen = fragment.deviceIsLocked;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            fragmentView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    fragment.setInsets(windowInsets);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            });
        }

        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSPARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(fragmentView, windowLayoutParams);
    }

    public static void clearInstance() {
        if (instance == null) {
            return;
        }

        if (VoIPService.getSharedInstance() != null) {
            int h = instance.fragmentView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            if (instance.canSwitchToPip) {
                VoIPPiPView.show(instance.activity, instance.currentAccount, instance.fragmentView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                    VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                    VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
                }
            }
        }

        instance.callingUserTextureView.renderer.release();
        instance.currentUserTextureView.renderer.release();
        instance.callingUserMiniTextureRenderer.release();
        instance.destroy();
        instance = null;
    }

    public static void onPause() {
        //Log.i(TAG, "onPause");
        if (instance != null) {
            instance.onPauseInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onPause();
        }
    }

    public static void onResume() {
        //Log.i(TAG, "onResume");
        if (instance != null) {
            instance.onResumeInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onResume();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        //Log.i(TAG, "onRequestPermissionsResult");
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    public VoIPFragment2(Activity activity, int account) {
        this.activity = activity;
        currentAccount = account;
        callingUser = VoIPService.getSharedInstance().getUser();
        //isOutgoing = VoIPService.getSharedInstance().isOutgoing();
        previousState = -1;
        currentState = VoIPService.getSharedInstance().getCallState();

        VoIPService.getSharedInstance().registerStateListener(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        //VoIPService.audioLevelsCallback = null;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
    }

    private ViewGroup createView(Context context) {
        FrameLayout fragmentLayout = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (isFinished || switchingToPip) {
                    return false;
                }
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && !lockOnScreen) {
                    onBackPressed();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (currentState == VoIPService.STATE_WAITING_INCOMING) {
                        final VoIPService service = VoIPService.getSharedInstance();
                        if (service != null) {
                            service.stopRinging();
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (encKeyView.getIsExpanded()) {
                                    //expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return check;
            }

            boolean check;
            long pressedTime;
        };
        fragmentView = fragmentLayout;

        fragmentLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        //orientationBefore = activity.getRequestedOrientation();
        //activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        fragmentLayout.setClipToPadding(false);
        fragmentLayout.setClipChildren(false);
        fragmentLayout.setFitsSystemWindows(true);

        background = new MotionBackgroundDrawable(0x20A4D7, 0x3F8BEA, 0x8148EC, 0xB456D8, 0, false);
        background.setIndeterminateAnimation(true);
        background.setIndeterminateSpeedScale(1f);
        background.updateAnimation(false);
        fragmentLayout.setBackground(background);

        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.renderer.setRotateTextureWithScreen(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        //callingUserTextureView.attachBackgroundRenderer();
        fragmentLayout.addView(callingUserTextureView);

        currentUserCameraIsFullscreen = true;

        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
                currentUserCameraIsFullscreen = true;
                cameraForceExpanded = true;
                previousState = currentState;
                updateViewState();
            }
        });
        fragmentLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserTextureView.renderer.setMirror(true);
        currentUserCameraFloatingLayout.addView(currentUserTextureView);

        View backgroundView = new View(context);
        backgroundView.setBackgroundColor(0xff1b1f23);

        callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
        callingUserMiniFloatingLayout.alwaysFloating = true;
        callingUserMiniFloatingLayout.setFloatingMode(true, false);
        callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
            if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                currentUserCameraIsFullscreen = false;
                cameraForceExpanded = false;
                previousState = currentState;
                updateViewState();
            }
        });
        callingUserMiniFloatingLayout.setVisibility(View.GONE);
        fragmentLayout.addView(callingUserMiniFloatingLayout);

        callingUserMiniTextureRenderer = new TextureViewRenderer(context);
        callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
        callingUserMiniTextureRenderer.setIsCamera(false);
        callingUserMiniTextureRenderer.setFpsReduction(30);
        callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)), Color.TRANSPARENT}));
        fragmentLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));
        //topShadow.setAlpha(0f);

        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.5f))}));
        fragmentLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM));
        //bottomShadow.setAlpha(0f);

        backIcon = new ImageView(context);
        backIcon.setImageResource(R.drawable.msg_call_minimize);
        fragmentLayout.addView(backIcon, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 24, 20, 0, 0));

        encKeyView = new VoIPEncKeyView(context);
        encKeyView.setVisibility(View.GONE);
        encKeyView.rationaleTextTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser)));
        encKeyView.setCallback(new VoIPEncKeyView.Callback() {
            @Override
            public void onShowRationaleClick() {
                MessagesController.getGlobalMainSettings().edit().putBoolean("emojisTooltipWasShowed", true).apply();

                AndroidUtilities.runOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;

                encKeyView.setExpanded(true, true);

                if (!(currentUserIsVideo || callingUserIsVideo)) {
                    callingUserPhotoWavesContainer.playAnimation(false);
                    callingUserPhotoWavesContainer.setPivotY(callingUserPhotoWavesContainer.getMeasuredHeight());
                    callingUserPhotoWavesContainer.animate().alpha(0).scaleX(0).scaleY(0).start();
                }

                if (currentUserIsVideo || callingUserIsVideo) {
                    statusLayout.animate().alpha(0).start();
                } else {
                    statusLayout.animate().translationY(AndroidUtilities.dp(20f)).start();
                }
            }

            @Override
            public void onHideRationaleClick() {
                encKeyView.setExpanded(false, true);

                if (!(currentUserIsVideo || callingUserIsVideo)) {
                    callingUserPhotoWavesContainer.playAnimation(true);
                    callingUserPhotoWavesContainer.setPivotY(callingUserPhotoWavesContainer.getMeasuredHeight());
                    callingUserPhotoWavesContainer.animate().alpha(1).scaleX(1).scaleY(1).start();
                }

                if (currentUserIsVideo || callingUserIsVideo) {
                    statusLayout.animate().alpha(1).start();
                } else {
                    statusLayout.animate().translationY(0f).start();
                }

                VoIPService service = VoIPService.getSharedInstance();
                if (canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
                    AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
                    hideUiRunnableWaiting = true;
                }
            }
        });
        fragmentLayout.addView(encKeyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 20, 40, 0));

        callingUserPhotoWavesContainer = new VoIPWavesContainer(context);
        //callingUserPhotoWavesContainer.setBackgroundColor(Color.RED);
        callingUserPhotoWavesContainer.setWavesRadius(74, 80, 84, 90);
        fragmentLayout.addView(callingUserPhotoWavesContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 100, 0, 0));
        callingUserPhotoWavesContainer.playAnimation(true);

        callingUserPhotoViewMini = new BackupImageView(context);
        //callingUserPhotoViewMini.setBackgroundColor(Color.BLUE);
        callingUserPhotoViewMini.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(140), 0xFF000000), callingUser);
        callingUserPhotoViewMini.setRoundRadius(AndroidUtilities.dp(140) / 2);
        callingUserPhotoWavesContainer.addView(callingUserPhotoViewMini, LayoutHelper.createFrame(140, 140, Gravity.CENTER_HORIZONTAL, 40, 40, 40, 40));

        statusLayout = new LinearLayout(context);
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentLayout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 300, 0, 0));

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        CharSequence name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
        name = Emoji.replaceEmoji(name, callingUserTitle.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
        callingUserTitle.setText(name);
        callingUserTitle.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        statusTextView = new VoIPStatusTextView(context);
        //ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 24));

        weakSignalNotification = new VoIPNotificationView(context);
        weakSignalNotification.setText("Weak network signal");
        weakSignalNotification.setAlpha(0);
        weakSignalNotification.setScaleX(0);
        weakSignalNotification.setScaleY(0);
        statusLayout.addView(weakSignalNotification, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        inButtonsLayout = new FrameLayout(context);
        //inButtonsLayout.setBackgroundColor(Color.RED);
        fragmentLayout.addView(inButtonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 60));

        acceptButtonWavesContainer = new VoIPWavesContainer(context);
        //acceptButtonWavesContainer.setBackgroundColor(Color.BLUE);
        acceptButtonWavesContainer.setWavesRadius(36, 40, 44, 48);
        acceptButtonWavesContainer.setWavesDeltaCY(20f);
        inButtonsLayout.addView(acceptButtonWavesContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 10, 0, 0, 0));
        acceptButtonWavesContainer.playAnimation(true);

        acceptButtonView = new VoIPToggleButton(context, 64);
        //acceptButtonView.setBackgroundColor(Color.GREEN);
        acceptButtonView.setData(R.raw.call_accept, true, Color.WHITE, Color.argb(255, 3, 187, 51), "Accept", false, VoIPToggleButton.IconAnimation.ANIMATE);
        acceptButtonView.playAnimation(true);
        acceptButtonView.setOnClickListener(v -> {
            if (currentState == VoIPService.STATE_BUSY) {
                Intent intent = new Intent(activity, VoIPService.class);
                intent.putExtra("user_id", callingUser.id);
                intent.putExtra("is_outgoing", true);
                intent.putExtra("start_incall_activity", false);
                intent.putExtra("video_call", isVideoCall);
                intent.putExtra("can_video_call", isVideoCall);
                intent.putExtra("account", currentAccount);
                try {
                    activity.startService(intent);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().acceptIncomingCall();
                        if (currentUserIsVideo) {
                            VoIPService.getSharedInstance().requestVideoCall(false);
                        }

                        //background.setColors(0xA9CC66, 0x5AB147, 0x07BA63, 0x07A9AC, 0, false);

                        acceptButtonWavesContainer.playAnimation(false);
                        acceptButtonView.playAnimation(false);
                    }
                }
            }
        });
        acceptButtonWavesContainer.addView(acceptButtonView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 40, 40, 40));

        declineButtonView = new VoIPToggleButton(context, 64);
        //declineButtonView.setBackgroundColor(Color.MAGENTA);
        declineButtonView.setData(R.raw.call_decline, false, Color.WHITE, Color.argb(255, 242, 24, 39), "Decline", false, VoIPToggleButton.IconAnimation.ANIMATE);
        declineButtonView.setOnClickListener(v -> {
            if (currentState == VoIPService.STATE_BUSY) {
                finish();
            } else {
                VoIPService.getSharedInstance().declineIncomingCall();
            }
        });
        inButtonsLayout.addView(declineButtonView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 40, 50, 0));

        buttonsLayout = new VoIPButtonsLayout(context);
        buttonsLayout.setChildSize(60);
        fragmentLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 24));

        for (int i = 0; i < 4; i++) {
            bottomButtons[i] = new VoIPToggleButton(context, 52);
            //bottomButtons[i].setBackgroundColor(Color.MAGENTA);
            buttonsLayout.addView(bottomButtons[i]/*, LayoutHelper.createLinear(60, 60, Gravity.CENTER_HORIZONTAL)*/);
        }

        notificationsLayout = new VoIPNotificationsLayout(context);
        notificationsLayout.setNotificationBackgroundColor(COLOR_DARK);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        fragmentLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        /*new CountDownTimer(20000, 388) {
            boolean gg = true;
            int ii = 0;

            @Override
            public void onTick(long millisUntilFinished) {
                if (millisUntilFinished < 10000 && gg) {
                    gg = false;
                    //background.setColors(0x08B0A3, 0x17AAE4, 0x3B7AF1, 0x4576E9, 0, false);
                }
                //background.updateAnimation(true);
                //Log.i(TAG, String.format("onTick value = %d", ii));
                callingUserPhotoWavesContainer.setAmpl(2 * ii);
                acceptButtonWavesContainer.setAmpl(ii);
                ii += 90;
                if (ii > 1000) {
                    ii = 0;
                }
            }

            @Override
            public void onFinish() {
                //Log.i(TAG, String.format("onFinish value = %d", 0));
                //acceptButtonWavesContainer.setAmpl(0);
            }
        }.start();*/

        backIcon.setOnClickListener(view -> {
            if (!lockOnScreen) {
                onBackPressed();
            }
        });
        if (fragmentLockOnScreen) {
            backIcon.setVisibility(View.GONE);
        }

        updateViewState();

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (!isVideoCall) {
                isVideoCall = service.privateCall != null && service.privateCall.video;
            }
            initRenderers();
            //VoIPService.audioLevelsCallback = (uids, levels, voice) -> Log.i(TAG, "audioLevelsCallback");
        }

        return fragmentLayout;
    }

    private void finish() {
        clearInstance();

        try {
            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(fragmentView);
        } catch (Exception ignore) {}
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsets(WindowInsets windowInsets) {
        lastInsets = windowInsets;
        ((FrameLayout.LayoutParams) backIcon.getLayoutParams()).topMargin = AndroidUtilities.dp(20) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) encKeyView.getLayoutParams()).topMargin = AndroidUtilities.dp(20) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserPhotoWavesContainer.getLayoutParams()).topMargin = AndroidUtilities.dp(100) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(300) + lastInsets.getSystemWindowInsetTop();
        //((FrameLayout.LayoutParams) topShadow.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();

        //((FrameLayout.LayoutParams) callingUserTextureView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        //((FrameLayout.LayoutParams) currentUserCameraFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) inButtonsLayout.getLayoutParams()).bottomMargin = AndroidUtilities.dp(60) + lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) buttonsLayout.getLayoutParams()).bottomMargin = AndroidUtilities.dp(24) + lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        //((FrameLayout.LayoutParams) bottomShadow.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();

        currentUserCameraFloatingLayout.setInsets(lastInsets);
        callingUserMiniFloatingLayout.setInsets(lastInsets);
        fragmentView.requestLayout();
        if (previewDialog != null) {
            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
        }
    }

    private void onBackPressed() {
        if (isFinished || switchingToPip) {
            return;
        }
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
            cameraForceExpanded = false;
            currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
            currentUserCameraIsFullscreen = false;
            previousState = currentState;
            updateViewState();
            return;
        }
        if (canSwitchToPip && !lockOnScreen) {
            /*if (AndroidUtilities.checkInlinePermissions(activity)) {
                switchToPip();
            } else {
                requestInlinePermissions();
            }*/
        } else {
            finish();
        }
    }

    private void updateViewState() {
        if (fragmentView == null) {
            return;
        }
        if (isFinished || switchingToPip) {
            return;
        }

        lockOnScreen = false;
        boolean animated = previousState != -1;
        boolean showAcceptDeclineView = false;
        boolean showTimer = false;
        boolean showReconnecting = false;
        boolean showCallingAvatarMini = false;
        VoIPService service = VoIPService.getSharedInstance();

        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = true;
                //statusLayoutOffset = AndroidUtilities.dp(24);
                //acceptDeclineView.setRetryMod(false);
                if (service != null && service.privateCall.video) {
                    showCallingAvatarMini = currentUserIsVideo && callingUser.photo != null;
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), true, animated);
                    //acceptDeclineView.setTranslationY(-AndroidUtilities.dp(60));
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), true, animated);
                    //acceptDeclineView.setTranslationY(0);
                }
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
                break;
            case VoIPService.STATE_REQUESTING:
                statusTextView.setText(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true, animated);
                break;
            case VoIPService.STATE_HANGING_UP:
                break;
            case VoIPService.STATE_BUSY:
                showAcceptDeclineView = true;
                statusTextView.setText(LocaleController.getString("VoipBusy", R.string.VoipBusy), false, animated);
                //acceptDeclineView.setRetryMod(true);
                currentUserIsVideo = false;
                callingUserIsVideo = false;
                break;
            case VoIPService.STATE_ESTABLISHED:
            case VoIPService.STATE_RECONNECTING:
                background.setColors(0xA9CC66, 0x5AB147, 0x07BA63, 0x07A9AC, 0, false);
                updateKeyView(animated);
                showTimer = true;
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = true;
                }
                break;
            case VoIPService.STATE_ENDED:
                currentUserTextureView.saveCameraLastBitmap();
                AndroidUtilities.runOnUIThread(() -> finish(), 200);
                break;
            case VoIPService.STATE_FAILED:
                statusTextView.setText(LocaleController.getString("VoipFailed", R.string.VoipFailed), false, animated);
                final VoIPService voipService = VoIPService.getSharedInstance();
                final String lastError = voipService != null ? voipService.getLastError() : Instance.ERROR_UNKNOWN;
                if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
                    if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
                        if (isVideoCall) {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name);
                            boolean[] callAgain = new boolean[1];
                            AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                                    .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                                    .setMessage(AndroidUtilities.replaceTags(message))
                                    .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> finish())
                                    .setPositiveButton(LocaleController.getString("VoipPeerVideoOutdatedMakeVoice", R.string.VoipPeerVideoOutdatedMakeVoice), (dialogInterface, i) -> {
                                        callAgain[0] = true;
                                        currentState = VoIPService.STATE_BUSY;
                                        Intent intent = new Intent(activity, VoIPService.class);
                                        intent.putExtra("user_id", callingUser.id);
                                        intent.putExtra("is_outgoing", true);
                                        intent.putExtra("start_incall_activity", false);
                                        intent.putExtra("video_call", false);
                                        intent.putExtra("can_video_call", false);
                                        intent.putExtra("account", currentAccount);
                                        try {
                                            activity.startService(intent);
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    })
                                    .show();
                            dlg.setCanceledOnTouchOutside(true);
                            dlg.setOnDismissListener(dialog -> {
                                if (!callAgain[0]) {
                                    finish();
                                }
                            });
                        } else {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name);
                            showErrorDialog(AndroidUtilities.replaceTags(message));
                        }
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
                        showErrorDialog("Error initializing audio hardware");
                    } else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
                        finish();
                    } else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
                        showErrorDialog(LocaleController.getString("VoipErrorUnknown", R.string.VoipErrorUnknown));
                    } else {
                        AndroidUtilities.runOnUIThread(() -> finish(), 1000);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> finish(), 1000);
                }
                break;
        }
        if (previewDialog != null) {
            return;
        }

        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        if (animated) {
            currentUserCameraFloatingLayout.saveRelativePosition();
            callingUserMiniFloatingLayout.saveRelativePosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                //callingUserPhotoView.setAlpha(1f);
            }
            if (animated) {
                callingUserTextureView.animate().alpha(1f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(1f);
            }
            /*if (!callingUserTextureView.renderer.isFirstFrameRendered() && !enterFromPiP) {
                callingUserIsVideo = false;
            }*/
        }

        if (currentUserIsVideo || callingUserIsVideo) {
            //fillNavigationBar(true, animated);
        } else {
            //fillNavigationBar(false, animated);
            //callingUserPhotoView.setVisibility(View.VISIBLE);
            if (animated) {
                callingUserTextureView.animate().alpha(0f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(0f);
            }
        }

        if (!currentUserIsVideo || !callingUserIsVideo) {
            cameraForceExpanded = false;
        }

        boolean showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded;

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            if (showAcceptDeclineView) {
                inButtonsLayout.setAlpha(1);
                buttonsLayout.setAlpha(0);
                acceptAnimShowed = false;
            } else {
                if (uiVisible) {
                    inButtonsLayout.setAlpha(0);
                    buttonsLayout.setAlpha(1);
                    if (!acceptAnimShowed) {
                        acceptAnimShowed = true;

                        /*inButtonsLayout.animate().alpha(0f).setDuration(50).start();

                        buttonsLayout.setAlpha(0f);
                        buttonsLayout.animate().alpha(1f).start();

                        int[] acceptCoords = new int[2];
                        acceptButtonView.getLocationOnScreen(acceptCoords);
                        int[] declineCoords = new int[2];
                        declineButtonView.getLocationOnScreen(declineCoords);

                        int[] coords = new int[2];
                        bottomButtons[0].getLocationOnScreen(coords);
                        int dx = acceptCoords[0] - coords[0] + AndroidUtilities.dp(8f);
                        int dy = acceptCoords[1] - coords[1] + AndroidUtilities.dp(8f);
                        bottomButtons[0].setTranslationX(dx);
                        bottomButtons[0].setTranslationY(dy);
                        bottomButtons[0].animate().translationX(0).translationY(0).start();

                        bottomButtons[1].getLocationOnScreen(coords);
                        dx = acceptCoords[0] - coords[0] + AndroidUtilities.dp(8f);
                        bottomButtons[1].setTranslationX(dx);
                        bottomButtons[1].setTranslationY(dy);
                        bottomButtons[1].animate().translationX(0).translationY(0).start();

                        bottomButtons[2].getLocationOnScreen(coords);
                        dx = acceptCoords[0] - coords[0] + AndroidUtilities.dp(8f);
                        bottomButtons[2].setTranslationX(dx);
                        bottomButtons[2].setTranslationY(dy);
                        bottomButtons[2].animate().translationX(0).translationY(0).start();

                        bottomButtons[3].getLocationOnScreen(coords);
                        dx = declineCoords[0] - coords[0] + AndroidUtilities.dp(8f);
                        bottomButtons[3].setTranslationX(dx);
                        bottomButtons[3].setTranslationY(dy);
                        bottomButtons[3].animate().translationX(0).translationY(0).start();*/
                    }
                }
            }
        }

        fragmentLockOnScreen = lockOnScreen || deviceIsLocked;
        canHideUI = (currentState == VoIPService.STATE_ESTABLISHED) && (currentUserIsVideo || callingUserIsVideo);
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }

        if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
            hideUiRunnableWaiting = true;
        } else if (service != null && service.isMicMute()) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
        }

        if (currentUserIsVideo || callingUserIsVideo) {
            background.setIndeterminateAnimation(false);

            if (uiVisible) {
                topShadow.setAlpha(1f);
                bottomShadow.setAlpha(1f);
            }

            callingUserPhotoWavesContainer.setAlpha(0f);

            if (encKeyView.getIsExpanded()) {
                statusLayout.setAlpha(0f);
            }
            statusLayout.setTranslationY(AndroidUtilities.dp(-220f));
        } else {
            background.setIndeterminateAnimation(true);

            topShadow.setAlpha(0f);
            bottomShadow.setAlpha(0f);

            if (encKeyView.getIsExpanded()) {
                callingUserPhotoWavesContainer.setScaleX(0f);
                callingUserPhotoWavesContainer.setScaleY(0f);
            } else {
                callingUserPhotoWavesContainer.setAlpha(1f);
                callingUserPhotoWavesContainer.setScaleX(1f);
                callingUserPhotoWavesContainer.setScaleY(1f);
            }

            if (encKeyView.getIsExpanded()) {
                statusLayout.setAlpha(1f);
                statusLayout.setTranslationY(AndroidUtilities.dp(20f));
            } else {
                statusLayout.setTranslationY(0f);
            }
        }

        if (animated) {
            if (lockOnScreen || !uiVisible) {
                if (backIcon.getVisibility() != View.VISIBLE) {
                    backIcon.setVisibility(View.VISIBLE);
                    backIcon.setAlpha(0f);
                }
                backIcon.animate().alpha(0f).start();
            } else {
                backIcon.animate().alpha(1f).start();
            }
            notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(100) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (!lockOnScreen) {
                backIcon.setVisibility(View.VISIBLE);
            }
            backIcon.setAlpha(lockOnScreen ? 0 : 1f);
            notificationsLayout.setTranslationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(100) : 0));
        }

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            updateButtons(animated);
        }

        if (showTimer) {
            statusTextView.showTimer(animated);
        }
        statusTextView.showReconnect(showReconnecting, animated);

        canSwitchToPip = false;//(currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY) && (currentUserIsVideo || callingUserIsVideo);

        if (service != null) {
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());

            service.setSinks(currentUserIsVideo && !service.isScreencast() ? currentUserTextureView.renderer : null, showCallingUserVideoMini ? callingUserMiniTextureRenderer : callingUserTextureView.renderer);

            if (animated) {
                notificationsLayout.beforeLayoutChanges();
            }
            if (service.isMicMute()) {
                notificationsLayout.addNotification(0, "Your microphone is turned off", "muted_local", animated);
            } else {
                notificationsLayout.removeNotification("muted_local");
            }
            if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
            } else {
                notificationsLayout.removeNotification("muted");
            }
            if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(R.drawable.calls_camera_mini, LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video", animated);
                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                notificationsLayout.removeNotification("video");
            }
            /*if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.showForView(bottomButtons[1], true);
            } else if (notificationsLayout.getChildCount() != 0) {
                tapToVideoTooltip.hide();
            }*/
            if (animated) {
                notificationsLayout.animateLayoutChanges();
            }
        }

        int floatingViewsOffset = notificationsLayout.getChildsHight() + AndroidUtilities.dp(10f);

        currentUserCameraFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        callingUserMiniFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        callingUserMiniFloatingLayout.setUiVisible(uiVisible);

        if (currentUserIsVideo) {
            if (!callingUserIsVideo || cameraForceExpanded) {
                showFloatingLayout(STATE_FULLSCREEN, animated);
            } else {
                showFloatingLayout(STATE_FLOATING, animated);
            }
        } else {
            showFloatingLayout(STATE_GONE, animated);
        }

        if (showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() == null) {
            callingUserMiniFloatingLayout.setIsActive(true);
            if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
                callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
                callingUserMiniFloatingLayout.setAlpha(0f);
                callingUserMiniFloatingLayout.setScaleX(0.5f);
                callingUserMiniFloatingLayout.setScaleY(0.5f);
            }
            callingUserMiniFloatingLayout.animate().setListener(null).cancel();
            callingUserMiniFloatingLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150).start();
            callingUserMiniFloatingLayout.setTag(1);
        } else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() != null) {
            callingUserMiniFloatingLayout.setIsActive(false);
            callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (callingUserMiniFloatingLayout.getTag() == null) {
                        callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    }
                }
            }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserMiniFloatingLayout.setTag(null);
        }

        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();
    }

    private void initRenderers() {
        currentUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {}
        });

        callingUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {}
        }, EglBase.CONFIG_PLAIN, new GlRectDrawer());

        callingUserMiniTextureRenderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
    }

    private void showUi(boolean show) {
        if (!show && uiVisible) {
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            encKeyView.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(0).translationY(AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            if (currentUserIsVideo || callingUserIsVideo) {
                topShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                bottomShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }

            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;

            buttonsLayout.setEnabled(false);
        } else if (show && !uiVisible) {
            //tapToVideoTooltip.hide();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            encKeyView.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            if (currentUserIsVideo || callingUserIsVideo) {
                topShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                bottomShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }

            buttonsLayout.setEnabled(true);
        }

        uiVisible = show;
        requestFullscreen(!show);
        notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public void requestFullscreen(boolean request) {
        if (request) {
            fragmentView.setSystemUiVisibility(fragmentView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            int flags = fragmentView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            fragmentView.setSystemUiVisibility(flags);
        }
    }

    private void showFloatingLayout(int state, boolean animated) {
        //Log.i(TAG, String.format("showFloatingLayout state=%d", state));
        if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) {
            currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        }
        if (!animated && cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
        }
        if (state == STATE_GONE) {
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != STATE_GONE) {
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
                    );
                    if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_FLOATING) {
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
                        );
                    }
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentUserCameraFloatingLayout.setTranslationX(0);
                            currentUserCameraFloatingLayout.setTranslationY(0);
                            currentUserCameraFloatingLayout.setScaleY(1f);
                            currentUserCameraFloatingLayout.setScaleX(1f);
                            currentUserCameraFloatingLayout.setVisibility(View.GONE);
                        }
                    });
                    cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    cameraShowingAnimator.setStartDelay(50);
                    cameraShowingAnimator.start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
        } else {
            boolean switchToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                switchToFloatAnimated = false;
            }
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                    if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
                        currentUserCameraFloatingLayout.setAlpha(0f);
                        currentUserCameraFloatingLayout.setScaleX(0.7f);
                        currentUserCameraFloatingLayout.setScaleY(0.7f);
                        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
                    }
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
                    );
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.setDuration(150).start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
            }
            if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
                currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
                currentUserCameraIsFullscreen = true;
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated);
            currentUserCameraIsFullscreen = state != STATE_FLOATING;
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.voipServiceCreated) {
            //Log.i(TAG, "voipServiceCreated");
            if (currentState == VoIPService.STATE_BUSY && VoIPService.getSharedInstance() != null) {
                callingUserTextureView.renderer.release();
                currentUserTextureView.renderer.release();
                callingUserMiniTextureRenderer.release();
                initRenderers();
                VoIPService.getSharedInstance().registerStateListener(this);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            //Log.i(TAG, "emojiLoaded");
            updateKeyView(true);
        } else if (id == NotificationCenter.closeInCallActivity) {
            //Log.i(TAG, "closeInCallActivity");
            finish();
        } else if (id == NotificationCenter.webRtcSpeakerAmplitudeEvent) {
            //Log.i(TAG, String.format("webRtcSpeakerAmplitudeEvent ampl=%f", (float) args[0]));
            if (fragmentView != null && !(currentUserIsVideo || callingUserIsVideo) && !encKeyView.getIsExpanded()) {
                callingUserPhotoWavesContainer.setAmpl((float) args[0]);
            }
        }
    }

    @Override
    public void onStateChanged(int state) {
        //Log.i(TAG, String.format("onStateChanged state=%d", state));
        if (currentState != state) {
            previousState = currentState;
            currentState = state;
            if (fragmentView != null) {
                updateViewState();
            }
        }
    }

    @Override
    public void onSignalBarsCountChanged(int count) {
        //Log.i(TAG, String.format("onSignalBarsCountChanged count=%d", count));
        if (fragmentView != null) {
            //count = new Random().nextInt(4) + 1;

            if (statusTextView != null) {
                statusTextView.setSignalBarCount(count);
            }

            if (count <= 1) {
                if (!isWeakSignal) {
                    isWeakSignal = true;

                    weakSignalNotification.animate().cancel();
                    weakSignalNotification.animate().alpha(1).scaleX(1).scaleY(1).setInterpolator(new OvershootInterpolator()).start();

                    background.setColors(0xDB904C, 0xDE7238, 0xE7618F, 0xE86958, 0, false);
                }
            } else {
                if (isWeakSignal) {
                    isWeakSignal = false;

                    weakSignalNotification.animate().cancel();
                    weakSignalNotification.animate().alpha(0).scaleX(0).scaleY(0).start();

                    background.setColors(0xA9CC66, 0x5AB147, 0x07BA63, 0x07A9AC, 0, false);
                }
            }
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        //Log.i(TAG, "onAudioSettingsChanged");
        updateButtons(true);
    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        //Log.i(TAG, String.format("onMediaStateUpdated audioState=%d, videoState=%d", audioState, videoState));
        previousState = currentState;
        if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        //Log.i(TAG, String.format("onCameraSwitch isFrontFace=%b", isFrontFace));
        previousState = currentState;
        updateViewState();
    }

    @Override
    public void onCameraFirstFrameAvailable() {
        //Log.i(TAG, "onCameraFirstFrameAvailable");
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {
        //Log.i(TAG, String.format("onVideoAvailableChange isAvailable=%b", isAvailable));
        previousState = currentState;
        if (isAvailable && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onScreenOnChange(boolean screenOn) {
        //Log.i(TAG, String.format("onScreenOnChange screenOn=%b", screenOn));
    }

    public void onScreenCastStart() {
        //Log.i(TAG, "onScreenCastStart");
        if (previewDialog == null) {
            return;
        }
        previewDialog.dismiss(true, true);
    }

    private void updateKeyView(boolean animated) {
        if (emojiLoaded) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        byte[] auth_key = null;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(service.getEncryptionKey());
            buf.write(service.getGA());
            auth_key = buf.toByteArray();
        } catch (Exception checkedExceptionsAreBad) {
            FileLog.e(checkedExceptionsAreBad, false);
        }
        if (auth_key == null) {
            return;
        }
        byte[] sha256 = Utilities.computeSHA256(auth_key, 0, auth_key.length);
        String[] emoji = EncryptionKeyEmojifier.emojifyForCall(sha256);
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(emoji[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                drawable.preload();
            }
            emojiDrawables[i] = drawable;
        }
        checkEmojiLoaded(animated);
    }

    private void checkEmojiLoaded(boolean animated) {
        if (Arrays.stream(emojiDrawables).anyMatch(it -> it == null || !it.isLoaded())) {
            return;
        }

        emojiLoaded = true;

        encKeyView.setEmojis(emojiDrawables, MessagesController.getGlobalMainSettings().getBoolean("emojisTooltipWasShowed", false), animated);
        encKeyView.setVisibility(View.VISIBLE);
    }

    private void updateButtons(boolean animated) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }

        /*if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionSet transitionSet = new TransitionSet();
            Visibility visibility = new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(100), 0);
                    if (view instanceof VoIPToggleButton) {
                        view.setTranslationY(AndroidUtilities.dp(100));
                        //animator.setStartDelay(((VoIPToggleButton) view).animationDelay);
                    }
                    return animator;
                }

                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                }
            };
            transitionSet
                    .addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
                    .addTransition(new ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT));
            transitionSet.excludeChildren(VoIPToggleButton.class, true);
            TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
        }*/

        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                    setFrontalCameraAction(bottomButtons[0], service, animated);
                } else {
                    setSpeakerPhoneAction(bottomButtons[0], service, animated);
                }

                setVideoAction(bottomButtons[1], service, animated);

                setMicrophoneAction(bottomButtons[2], service, animated);
            }/* else {
                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);
            }*/
            //bottomButtons[3].setVisibility(View.GONE);
        } else {
            if (instance == null) {
                return;
            }

            if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                setFrontalCameraAction(bottomButtons[0], service, animated);
            } else {
                setSpeakerPhoneAction(bottomButtons[0], service, animated);
            }

            setVideoAction(bottomButtons[1], service, animated);

            setMicrophoneAction(bottomButtons[2], service, animated);

            bottomButtons[3].setData(R.raw.call_decline, false, Color.WHITE, Color.argb(255, 242, 24, 39), LocaleController.getString("VoipEndCall", R.string.VoipEndCall), animated, VoIPToggleButton.IconAnimation.ANIMATE);
            bottomButtons[3].setOnClickListener(view -> {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().hangUp();
                }
            });
        }
    }

    private void setSpeakerPhoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isBluetoothOn()) {
            bottomButton.setData(R.raw.speaker_to_bt, false, Color.WHITE, COLOR_LIGHT, LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), animated, VoIPToggleButton.IconAnimation.ANIMATE);
            //bottomButton.setChecked(false, animated);
        } else if (service.isSpeakerphoneOn()) {
            bottomButton.setData(R.raw.bt_to_speaker, false, COLOR_DARK, Color.WHITE, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated, VoIPToggleButton.IconAnimation.ANIMATE);
            //bottomButton.setChecked(true, animated);
        } else {
            bottomButton.setData(R.raw.bt_to_speaker, false, Color.WHITE, COLOR_LIGHT, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated, VoIPToggleButton.IconAnimation.ANIMATE);
            //bottomButton.setChecked(false, animated);
        }
        //bottomButton.setCheckableForAccessibility(true);
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });
    }

    private void setVideoAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                bottomButton.setData(/*service.isScreencast() ? R.drawable.calls_sharescreen : */R.raw.video_start, false, Color.WHITE, COLOR_LIGHT, LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), animated, VoIPToggleButton.IconAnimation.POINT);
            } else {
                bottomButton.setData(R.raw.video_stop, false, COLOR_DARK, Color.WHITE, LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), animated, VoIPToggleButton.IconAnimation.POINT);
            }
            //bottomButton.setCrossOffset(-AndroidUtilities.dpf2(3.5f));
            bottomButton.setOnClickListener(view -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
                } else {
                    if (Build.VERSION.SDK_INT < 21 && service.privateCall != null && !service.privateCall.video && !callingUserIsVideo && !service.sharedUIParams.cameraAlertWasShowed) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(LocaleController.getString("VoipSwitchToVideoCall", R.string.VoipSwitchToVideoCall));
                        builder.setPositiveButton(LocaleController.getString("VoipSwitch", R.string.VoipSwitch), (dialogInterface, i) -> {
                            service.sharedUIParams.cameraAlertWasShowed = true;
                            toggleCameraInput();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.create().show();
                    } else {
                        toggleCameraInput();
                    }
                }
            });
            bottomButton.setEnabled(true);
        } else {
            bottomButton.setData(R.raw.video_start, false, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), COLOR_LIGHT, "Video", animated, VoIPToggleButton.IconAnimation.POINT);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        }
    }

    private void setMicrophoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isMicMute()) {
            bottomButton.setData(R.raw.call_mute, false, COLOR_DARK, Color.WHITE, LocaleController.getString("VoipUnmute", R.string.VoipUnmute), animated, VoIPToggleButton.IconAnimation.POINT);
        } else {
            bottomButton.setData(R.raw.call_unmute, false, Color.WHITE, COLOR_LIGHT, LocaleController.getString("VoipMute", R.string.VoipMute), animated, VoIPToggleButton.IconAnimation.POINT);
        }
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
                final boolean micMute = !serviceInstance.isMicMute();
                /*if (accessibilityManager.isTouchExplorationEnabled()) {
                    final String text;
                    if (micMute) {
                        text = LocaleController.getString("AccDescrVoipMicOff", R.string.AccDescrVoipMicOff);
                    } else {
                        text = LocaleController.getString("AccDescrVoipMicOn", R.string.AccDescrVoipMicOn);
                    }
                    view.announceForAccessibility(text);
                }*/
                serviceInstance.setMicMute(micMute, false, true);
                previousState = currentState;
                updateViewState();
            }
        });
    }

    private void setFrontalCameraAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setData(R.raw.camera_flip_, false, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), COLOR_LIGHT, LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, VoIPToggleButton.IconAnimation.SCALE);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        } else {
            bottomButton.setEnabled(true);
            if (!service.isFrontFaceCamera()) {
                bottomButton.setData(R.raw.camera_flip_, false, COLOR_DARK, Color.WHITE, LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, VoIPToggleButton.IconAnimation.SCALE);
            } else {
                bottomButton.setData(R.raw.camera_flip_, false, Color.WHITE, COLOR_LIGHT, LocaleController.getString("VoipFlip", R.string.VoipFlip), animated, VoIPToggleButton.IconAnimation.SCALE);
            }

            bottomButton.setOnClickListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    /*if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }*/
                    serviceInstance.switchCamera();
                }
            });
        }
    }

    private void toggleCameraInput() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            /*if (accessibilityManager.isTouchExplorationEnabled()) {
                final String text;
                if (!currentUserIsVideo) {
                    text = LocaleController.getString("AccDescrVoipCamOn", R.string.AccDescrVoipCamOn);
                } else {
                    text = LocaleController.getString("AccDescrVoipCamOff", R.string.AccDescrVoipCamOff);
                }
                fragmentView.announceForAccessibility(text);
            }*/
            if (!currentUserIsVideo) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if (previewDialog == null) {
                        service.createCaptureDevice(false);
                        if (!service.isFrontFaceCamera()) {
                            service.switchCamera();
                        }
                        fragmentLockOnScreen = true;
                        previewDialog = new PrivateVideoPreviewDialog(fragmentView.getContext(), false, true) {
                            @Override
                            public void onDismiss(boolean screencast, boolean apply) {
                                previewDialog = null;
                                VoIPService service = VoIPService.getSharedInstance();
                                fragmentLockOnScreen = false;
                                if (apply) {
                                    currentUserIsVideo = true;
                                    if (service != null && !screencast) {
                                        service.requestVideoCall(false);
                                        service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                previousState = currentState;
                                updateViewState();
                            }
                        };
                        if (lastInsets != null) {
                            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
                        }
                        fragmentView.addView(previewDialog);
                    }
                    return;
                } else {
                    currentUserIsVideo = true;
                    if (!service.isSpeakerphoneOn()) {
                        VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
                    }
                    service.requestVideoCall(false);
                    service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                }
            } else {
                currentUserTextureView.saveCameraLastBitmap();
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 21) {
                    service.clearCamera();
                }
            }
            previousState = currentState;
            updateViewState();
        }
    }

    public void onPauseInternal() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }

        boolean hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity);

        if (canSwitchToPip && hasPermissionsToPip) {
            int h = instance.fragmentView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.fragmentView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }

        if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
    }

    public void onResumeInternal() {
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.finish();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
            updateViewState();
        } else {
            finish();
        }

        deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
    }

    private void showErrorDialog(CharSequence message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(dialog -> finish());
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (VoIPService.getSharedInstance() == null) {
                finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPService.getSharedInstance().acceptIncomingCall();
            } else {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(activity, () -> finish(), requestCode);
                    return;
                }
            }
        }
        if (requestCode == 102) {
            if (VoIPService.getSharedInstance() == null) {
                finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCameraInput();
            }
        }
    }

    @SuppressLint("InlinedApi")
    private void requestInlinePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlertsCreator.createDrawOverlayPermissionDialog(activity, (dialogInterface, i) -> {
                if (fragmentView != null) {
                    finish();
                }
            }).show();
        }
    }
}
