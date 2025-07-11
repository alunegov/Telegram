package org.telegram.ui.Components.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class ProfileButton extends LinearLayout {

    private ImageView imageView;
    private TextView textView;

    public ProfileButton(Context context) {
        super(context);

        setWillNotDraw(true);

        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        //setMinimumWidth(AndroidUtilities.dp(100));
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0x1fffffff));

        imageView = new ImageView(context);
        imageView.setColorFilter(Color.WHITE);
        addView(imageView, LayoutHelper.createLinear(30, 30, Gravity.CENTER_HORIZONTAL));

        textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 3, 0, 0));
    }

    public void setTextAndIcon(CharSequence caption, int resId, boolean isSvg) {
        textView.setText(caption);

        if (isSvg) {
            SvgHelper.SvgDrawable image = SvgHelper.getDrawable(resId, Color.WHITE);
            imageView.setImageDrawable(image);
        } else {
            imageView.setImageResource(resId);
        }
    }
}
