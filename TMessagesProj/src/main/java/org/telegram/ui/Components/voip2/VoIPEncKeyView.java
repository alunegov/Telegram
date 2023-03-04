package org.telegram.ui.Components.voip2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPEncKeyView extends FrameLayout {

    private final static int COLOR_DARK = ColorUtils.setAlphaComponent(Color.BLACK, 50);

    LinearLayout emojiLayout;

    ImageView[] emojiViews = new ImageView[4];

    VoIPHintView hintTextView;

    VoIPNotificationView hideRationaleButton;

    LinearLayout rationaleLayout;

    TextView rationaleHeaderTextView;

    public TextView rationaleTextTextView;

    boolean isHintAcked;

    boolean isExpanded;

    Callback callback;

    public VoIPEncKeyView(@NonNull Context context) {
        super(context);

        emojiLayout = new LinearLayout(context);
        emojiLayout.setOnClickListener(v -> { if (callback != null && !isExpanded) callback.onShowRationaleClick(); });
        this.addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new ImageView(context);
            emojiViews[i].setScaleType(ImageView.ScaleType.FIT_XY);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(26, 26, i == 0 ? 0 : 8, 0, 0, 0));
        }

        hintTextView = new VoIPHintView(context);
        hintTextView.setText("Encryption key of this call");
        this.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        hideRationaleButton = new VoIPNotificationView(context);
        hideRationaleButton.setText("Hide Emoji");
        hideRationaleButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        hideRationaleButton.setVisibility(View.GONE);
        hideRationaleButton.setOnClickListener(v -> { if (callback != null && isExpanded) callback.onHideRationaleClick(); });
        this.addView(hideRationaleButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        rationaleLayout = new LinearLayout(context);
        rationaleLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), COLOR_DARK));
        rationaleLayout.setOrientation(LinearLayout.VERTICAL);
        rationaleLayout.setVisibility(View.GONE);
        this.addView(rationaleLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 100, 0, 0));

        rationaleHeaderTextView = new TextView(context);
        rationaleHeaderTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        rationaleHeaderTextView.setText("This call is end-to end encrypted");
        //rationaleHeaderTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        rationaleHeaderTextView.setTextColor(Color.WHITE);
        rationaleHeaderTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        rationaleHeaderTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        rationaleLayout.addView(rationaleHeaderTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 15, 75, 15, 15));

        rationaleTextTextView = new TextView(context);
        rationaleTextTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        rationaleTextTextView.setText("If the emoji on Diana's screen are the same, this call is 100% secure.");
        //rationaleTextTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        rationaleTextTextView.setTextColor(Color.WHITE);
        rationaleTextTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        rationaleLayout.addView(rationaleTextTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 15, 0, 15, 20));
    }

    public boolean getIsExpanded() {
        return isExpanded;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEmojis(Emoji.EmojiDrawable[] emojiDrawables, boolean isHintAcked, boolean animated) {
        this.isHintAcked = isHintAcked;

        for (int i = 0; i < 4; i++) {
            emojiViews[i].setImageDrawable(emojiDrawables[i]);
            //emojiViews[i].setContentDescription(emoji[i]);

            if (animated) {
                emojiViews[i].setAlpha(0f);
                emojiViews[i].setScaleX(0f);
                emojiViews[i].setScaleY(0f);
                emojiViews[i].animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator()).start();
            }
        }

        if (!isHintAcked) {
            if (animated) {
                hintTextView.setAlpha(0f);
                hintTextView.setScaleX(0.4f);
                hintTextView.setScaleY(0.4f);
                hintTextView.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator()).start();
            }
        } else {
            hintTextView.setVisibility(View.GONE);
        }
    }

    public void setExpanded(boolean isExpanded, boolean animated) {
        if (this.isExpanded == isExpanded) {
            return;
        }
        this.isExpanded = isExpanded;

        if (isExpanded) {
            if (animated) {
                hintTextView.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        hintTextView.setVisibility(View.GONE);
                    }
                }).start();

                hideRationaleButton.setVisibility(View.VISIBLE);
                hideRationaleButton.animate().alpha(1f).setListener(null).start();

                rationaleLayout.setVisibility(View.VISIBLE);
                rationaleLayout.setTranslationY(AndroidUtilities.dp(-110f));
                rationaleLayout.setScaleX(0.4f);
                rationaleLayout.setScaleY(0.4f);
                rationaleLayout.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setListener(null).start();

                emojiLayout.animate().translationY(AndroidUtilities.dp(125f)).scaleX(1.5f).scaleY(1.5f).start();
            } else {
                hintTextView.setVisibility(View.GONE);
                hideRationaleButton.setVisibility(View.VISIBLE);
                rationaleLayout.setVisibility(View.VISIBLE);
                emojiLayout.setTranslationY(AndroidUtilities.dp(125f));
                emojiLayout.setScaleX(1.5f);
                emojiLayout.setScaleY(1.5f);
            }

            isHintAcked = true;
        } else {
            if (animated) {
                if (!isHintAcked) {
                    hintTextView.setVisibility(View.VISIBLE);
                    hintTextView.animate().alpha(1f).setListener(null).start();
                }

                hideRationaleButton.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        hideRationaleButton.setVisibility(View.GONE);
                    }
                }).start();

                rationaleLayout.animate().alpha(0f).translationY(AndroidUtilities.dp(-110f)).scaleX(0.4f).scaleY(0.4f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rationaleLayout.setVisibility(View.GONE);
                    }
                }).start();

                emojiLayout.animate().translationY(AndroidUtilities.dp(0f)).scaleX(1f).scaleY(1f).start();
            } else {
                if (!isHintAcked) {
                    hintTextView.setVisibility(View.VISIBLE);
                }
                hideRationaleButton.setVisibility(View.GONE);
                rationaleLayout.setVisibility(View.GONE);
                emojiLayout.setTranslationY(AndroidUtilities.dp(0f));
                emojiLayout.setScaleX(1f);
                emojiLayout.setScaleY(1f);
            }
        }
    }

    public interface Callback {
        void onShowRationaleClick();

        void onHideRationaleClick();
    }
}
