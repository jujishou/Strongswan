package org.strongswan.android.ui.adapter;

import android.content.Context;
import android.widget.ArrayAdapter;

import org.strongswan.android.R;
import org.strongswan.android.security.TrustedCertificateEntry;

public class CertificateIdentitiesAdapter extends ArrayAdapter<String> {
    TrustedCertificateEntry mCertificate;

    public CertificateIdentitiesAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        extractIdentities();
    }

    /**
     * Set a new certificate for this adapter.
     *
     * @param certificate the certificate to extract identities from (null to clear)
     */
    public void setCertificate(TrustedCertificateEntry certificate) {
        mCertificate = certificate;
        clear();
        extractIdentities();
    }

    private void extractIdentities() {
        if (mCertificate == null) {
            add(getContext().getString(R.string.profile_user_select_id_init));
        } else {
            add(String.format(getContext().getString(R.string.profile_user_select_id_default), mCertificate.getCertificate().getSubjectDN().getName()));
            addAll(mCertificate.getSubjectAltNames());
        }
    }
}
