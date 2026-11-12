package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class KidHomeAdapter extends BaseAdapter {
    private final Context context;
    private final PackageManager pm;
    private final List<KidHomeItem> items = new ArrayList<>();

    public KidHomeAdapter(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
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
            view = LayoutInflater.from(context).inflate(R.layout.item_kid_home, parent, false);
        }
        ImageView icon = view.findViewById(R.id.kidIcon);
        TextView label = view.findViewById(R.id.kidLabel);
        KidHomeItem item = items.get(position);
        label.setText(item.label);

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
        return view;
    }
}
