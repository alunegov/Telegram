package org.telegram.ui.Components.voip2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.Objects;

public class VoIPToggleButton extends FrameLayout {

    RLottieImageView[] iconImageView = new RLottieImageView[2];

    FrameLayout captionLayout;
    TextView[] captionTextView = new TextView[2];

    int currentIconRes;
    int currentIconColor;
    int currentBackgroundColor;
    String currentCaption;

    private final int iconSize;

    public VoIPToggleButton(@NonNull Context context) {
        this(context, 52);
    }

    public VoIPToggleButton(@NonNull Context context, int iconSize) {
        super(context);

        this.iconSize = iconSize;

        for (int i = 0; i < 2; i++) {
            iconImageView[i] = new RLottieImageView(context);
            //iconImageView[i].setBackgroundColor(Color.GREEN);
            iconImageView[i].setScaleType(ImageView.ScaleType.CENTER);
            this.addView(iconImageView[i], LayoutHelper.createFrame(iconSize, iconSize, Gravity.CENTER_HORIZONTAL));
        }
        iconImageView[1].setVisibility(View.GONE);

        captionLayout = new FrameLayout(context);
        //captionLayout.setBackgroundColor(Color.BLUE);
        this.addView(captionLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        for (int i = 0; i < 2; i++) {
            captionTextView[i] = new TextView(context);
            //captionTextView[i].setBackgroundColor(Color.RED);
            captionTextView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            captionTextView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            captionTextView[i].setTextColor(Color.WHITE);
            captionTextView[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            captionLayout.addView(captionTextView[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, iconSize + 4, 0, 0));
        }
        captionTextView[1].setVisibility(View.GONE);
    }

    public void setData(int iconRes, boolean autoRepeat, int iconColor, int backgroundColor, String caption, boolean animated, IconAnimation iconAnim) {
        if (currentIconRes == iconRes && currentIconColor == iconColor && currentBackgroundColor == backgroundColor && Objects.equals(currentCaption, caption)) {
            return;
        }

        boolean iconChanged = (currentIconRes != iconRes) || (iconAnim == IconAnimation.POINT) || (iconAnim == IconAnimation.SCALE) || (iconAnim == IconAnimation.ANIMATE);
        boolean captionChanged = !Objects.equals(currentCaption, caption);

        currentIconRes = iconRes;
        currentIconColor = iconColor;
        currentBackgroundColor = backgroundColor;
        currentCaption = caption;

        if (animated) {
            if (iconChanged) {
                iconImageView[1].setAutoRepeat(autoRepeat);
                iconImageView[1].setAnimation(iconRes, iconSize, iconSize);
                iconImageView[1].setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(iconSize), backgroundColor));
                iconImageView[1].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }

            switch (iconAnim) {
                case POINT: {
                    iconImageView[1].setScaleX(0);
                    iconImageView[1].setScaleY(0);
                    iconImageView[1].setVisibility(View.VISIBLE);

                    ValueAnimator va = ValueAnimator.ofFloat(0, 1f);
                    va.addUpdateListener(animation -> {
                        float factor = (float) animation.getAnimatedValue();

                        if (factor <= 0.3f) {
                            iconImageView[0].setScaleX(1f - factor);
                            iconImageView[0].setScaleY(1f - factor);
                        }
                        iconImageView[1].setScaleX(factor);
                        iconImageView[1].setScaleY(factor);

                        if (factor > 0.3f && !iconImageView[1].isPlaying()) {
                            iconImageView[1].setProgress(0);
                            iconImageView[1].playAnimation();
                        }
                    });
                    va.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            iconImageView[0].setAutoRepeat(autoRepeat);
                            iconImageView[0].setAnimation(iconImageView[1].getAnimatedDrawable());
                            iconImageView[0].setBackground(iconImageView[1].getBackground());
                            iconImageView[0].setColorFilter(iconImageView[1].getColorFilter());
                            iconImageView[0].setScaleX(1);
                            iconImageView[0].setScaleY(1);
                            iconImageView[1].setVisibility(View.GONE);
                        }
                    });
                    va.start();

                    break;
                }
                case SCALE: {
                    iconImageView[1].setAlpha(0.7f);
                    iconImageView[1].setScaleX(0.7f);
                    iconImageView[1].setScaleY(0.7f);

                    ValueAnimator va1 = ValueAnimator.ofFloat(0, 0.3f);
                    va1.addUpdateListener(animation -> {
                        float factor = (float) animation.getAnimatedValue();

                        iconImageView[0].setAlpha(1f - factor);
                        iconImageView[0].setScaleX(1f - factor);
                        iconImageView[0].setScaleY(1f - factor);
                    });
                    va1.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (iconChanged) {
                                iconImageView[0].setVisibility(View.GONE);
                                iconImageView[1].setVisibility(View.VISIBLE);
                            }
                            int i = iconChanged ? 1 : 0;
                            iconImageView[i].setProgress(0);
                            iconImageView[i].playAnimation();
                        }
                    });

                    ValueAnimator va2 = ValueAnimator.ofFloat(0.3f, 0);
                    va2.addUpdateListener(animation -> {
                        float factor = (float) animation.getAnimatedValue();

                        int i = iconChanged ? 1 : 0;
                        iconImageView[i].setScaleX(1f - factor);
                        iconImageView[i].setScaleY(1f - factor);
                    });
                    va2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (iconChanged) {
                                iconImageView[0].setAutoRepeat(autoRepeat);
                                iconImageView[0].setAnimation(iconImageView[1].getAnimatedDrawable());
                                iconImageView[0].setBackground(iconImageView[1].getBackground());
                                iconImageView[0].setColorFilter(iconImageView[1].getColorFilter());
                                iconImageView[0].setAlpha(1f);
                                iconImageView[0].setScaleX(1);
                                iconImageView[0].setScaleY(1);
                                iconImageView[0].setVisibility(View.VISIBLE);
                                iconImageView[1].setVisibility(View.GONE);
                            }
                        }
                    });

                    AnimatorSet as = new AnimatorSet();
                    as.playSequentially(va1, va2);
                    as.setDuration(va1.getDuration() / 2);
                    as.start();

                    break;
                }
                case ANIMATE:
                    if (iconChanged) {
                        iconImageView[0].setAutoRepeat(autoRepeat);
                        iconImageView[0].setAnimation(iconImageView[1].getAnimatedDrawable());
                        iconImageView[0].setBackground(iconImageView[1].getBackground());
                        iconImageView[0].setColorFilter(iconImageView[1].getColorFilter());

                        //iconImageView[0].setProgress(0);
                        iconImageView[0].playAnimation();
                    }
                    break;
            }

            if (captionChanged) {
                captionTextView[1].setText(caption);
                captionTextView[1].setAlpha(0);
                captionTextView[1].setTranslationY(AndroidUtilities.dp(8f));
                captionTextView[1].setVisibility(View.VISIBLE);

                ValueAnimator va = ValueAnimator.ofFloat(0, 1f);
                va.addUpdateListener(animation -> {
                    float factor = (float) animation.getAnimatedValue();

                    captionTextView[1].setAlpha(factor);
                    captionTextView[1].setTranslationY(AndroidUtilities.dp(8f) * (1 - factor));

                    captionTextView[0].setAlpha(1 - factor);
                });
                va.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        TextView tmp = captionTextView[0];
                        captionTextView[0] = captionTextView[1];
                        captionTextView[1] = tmp;
                        captionTextView[1].setVisibility(View.GONE);
                    }
                });
                va.start();
            }
        } else {
            iconImageView[0].setAutoRepeat(autoRepeat);
            iconImageView[0].setAnimation(iconRes, iconSize, iconSize);
            iconImageView[0].setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(iconSize), backgroundColor));
            iconImageView[0].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            captionTextView[0].setText(caption);
            invalidate();
        }
    }

    public void playAnimation(boolean play) {
        if (play) {
            iconImageView[0].setProgress(0);
            iconImageView[0].playAnimation();
        } else {
            iconImageView[0].stopAnimation();
        }
    }

    public enum IconAnimation {
        ANIMATE,
        POINT,
        SCALE,
    }
}
