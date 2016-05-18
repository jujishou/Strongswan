package org.strongswan.android.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.strongswan.android.R;
import org.strongswan.android.security.TrustedCertificateEntry;

import java.util.List;

public class TrustedCertificateAdapter extends ArrayAdapter<TrustedCertificateEntry> {
    public TrustedCertificateAdapter(Context context) {
        super(context, R.layout.trusted_certificates_item);
    }

    /**
     * Set new data for this adapter.
     *
     * @param data the new data (null to clear)
     */
    public void setData(List<TrustedCertificateEntry> data) {
        clear();
        if (data != null) {
            addAll(data);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(R.layout.trusted_certificates_item, parent, false);
        }
        TrustedCertificateEntry item = getItem(position);
        TextView text = (TextView) view.findViewById(R.id.subject_primary);
        text.setText(item.getSubjectPrimary());
        text = (TextView) view.findViewById(R.id.subject_secondary);
        text.setText(item.getSubjectSecondary());
        return view;
    }
}
