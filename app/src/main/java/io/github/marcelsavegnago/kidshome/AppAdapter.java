package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class AppAdapter extends BaseAdapter {
    private final Context context;
    private final List<ResolveInfo> apps;
    private final PackageManager packageManager;

    public AppAdapter(Context context, List<ResolveInfo> apps, PackageManager packageManager) {
        this.context = context;
        this.apps = apps;
        this.packageManager = packageManager;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ResolveInfo entry = apps.get(position);
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.layout_appinfo, parent, false);
        }

        ImageView icon = view.findViewById(R.id.ivIcon);
        TextView name = view.findViewById(R.id.tvName);
        TextView pkg = view.findViewById(R.id.tvPack);

        icon.setImageDrawable(entry.loadIcon(packageManager));
        name.setText(entry.loadLabel(packageManager));
        pkg.setText(entry.activityInfo.packageName);
        return view;
    }
}
