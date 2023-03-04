package org.telegram.ui.Components.voip2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPHintView extends FrameLayout {

    private final static int COLOR_DARK = ColorUtils.setAlphaComponent(Color.BLACK, 50);

    FrameLayout textLayout;
    TextView text;

    ImageView arrow;

    public VoIPHintView(@NonNull Context context) {
        super(context);

        textLayout = new FrameLayout(context);
        textLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), COLOR_DARK));
        addView(textLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, 6, 0, 0));

        text = new TextView(context);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setTextColor(Color.WHITE);
        //text.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        //text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        textLayout.addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 12, 4, 12, 4));

        arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.tooltip_arrow_up);
        arrow.setColorFilter(new PorterDuffColorFilter(COLOR_DARK, PorterDuff.Mode.MULTIPLY));
        addView(arrow, LayoutHelper.createFrame(14, 6, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
    }

    public void setText(String text) {
        this.text.setText(text);
    }
}
