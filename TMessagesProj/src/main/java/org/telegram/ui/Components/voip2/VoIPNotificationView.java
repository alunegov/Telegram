package org.telegram.ui.Components.voip2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPNotificationView extends FrameLayout {

    private final static int COLOR_DARK = ColorUtils.setAlphaComponent(Color.BLACK, 50);

    TextView text;

    public VoIPNotificationView(Context context) {
        super(context);

        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), COLOR_DARK));

        text = new TextView(context);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setTextColor(Color.WHITE);
        //text.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        //text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 12, 4, 12, 4));
    }

    public void setText(String text) {
        this.text.setText(text);
    }

    public void setTypeface(Typeface tf) {
        text.setTypeface(tf);
    }
}
