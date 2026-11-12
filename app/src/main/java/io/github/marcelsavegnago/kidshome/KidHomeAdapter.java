package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class KidHomeAdapter extends BaseAdapter {
    private static final int[] PASTEL_COLORS = {
            R.color.kid_tile_pastel_1,
            R.color.kid_tile_pastel_2,
            R.color.kid_tile_pastel_3,
            R.color.kid_tile_pastel_4,
            R.color.kid_tile_pastel_5,
            R.color.kid_tile_pastel_6,
    };

    private final Context context;
    private final PackageManager pm;
    private final LayoutInflater inflater;
    private final List<KidHomeItem> items = new ArrayList<>();
    private final float cornerRadiusPx;
    private final ColorMatrixColorFilter greyFilter;

    public KidHomeAdapter(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.inflater = LayoutInflater.from(context);
        this.cornerRadiusPx = context.getResources().getDimension(R.dimen.kid_tile_radius);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        this.greyFilter = new ColorMatrixColorFilter(matrix);
    }

    public void setItems(List<KidHomeItem> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public KidHomeItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_kid_home, parent, false);
        }
        ImageView icon = view.findViewById(R.id.kidIcon);
        ImageView lockBadge = view.findViewById(R.id.kidLockBadge);
        TextView label = view.findViewById(R.id.kidLabel);
        TextView timeLabel = view.findViewById(R.id.kidTimeLabel);
        View inner = view.findViewById(R.id.kidTileInner);
        KidHomeItem item = items.get(position);
        label.setText(item.label);
        view.setContentDescription(item.locked
                ? context.getString(R.string.cd_app_locked, item.label)
                : item.label);
        applyPastel(inner, position, item.locked);

        if (item.type == KidHomeItem.Type.APP) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(item.packageName, 0);
                Drawable d = info.loadIcon(pm);
                icon.setImageDrawable(d);
            } catch (Exception e) {
                icon.setImageResource(R.drawable.ic_notification);
            }
        } else {
            icon.setImageResource(R.drawable.ic_web_shortcut);
            FaviconHelper.loadInto(context, item.url, icon, R.drawable.ic_web_shortcut);
        }

        if (item.locked) {
            icon.setColorFilter(greyFilter);
            lockBadge.setVisibility(View.VISIBLE);
            inner.setAlpha(0.72f);
        } else {
            icon.clearColorFilter();
            lockBadge.setVisibility(View.GONE);
            inner.setAlpha(1f);
        }

        if (item.timeLabel != null && !item.timeLabel.isEmpty()) {
            timeLabel.setVisibility(View.VISIBLE);
            timeLabel.setText(item.timeLabel);
        } else {
            timeLabel.setVisibility(View.GONE);
            timeLabel.setText("");
        }
        return view;
    }

    private void applyPastel(View tile, int position, boolean locked) {
        if (tile == null) {
            return;
        }
        int colorRes = locked ? R.color.kid_tile_locked : PASTEL_COLORS[position % PASTEL_COLORS.length];
        int color = ContextCompat.getColor(context, colorRes);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(cornerRadiusPx);
        shape.setColor(color);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x33000000),
                shape,
                null
        );
        tile.setBackground(ripple);
    }
}
