/*
 * Copyright (C) 2012-2016 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * HSR Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.ui.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialogFragment;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.data.VpnType.VpnTypeFeature;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.security.TrustedCertificateEntry;
import org.strongswan.android.ui.adapter.CertificateIdentitiesAdapter;
import org.strongswan.android.ui.view.TextInputLayoutHelper;

import java.security.cert.X509Certificate;

public class VpnProfileDetailActivity extends AppCompatActivity {
    private static final int SELECT_TRUSTED_CERTIFICATE = 0;
    private static final int MTU_MIN = 1280;
    private static final int MTU_MAX = 1500;

    private Long mId;//前面界面传递过来的profile id
    private String mUserCertLoading;
    private String mSelectedUserId;

    private VpnProfileDataSource mDataSource;//数据库
    private TrustedCertificateEntry mCACertEntry;
    private TrustedCertificateEntry mUserCertEntry;
    private CertificateIdentitiesAdapter mSelectUserIdAdapter;
    private VpnType mVpnType = VpnType.IKEV2_EAP;//连接类型，默认值IKEV2_EAP
    private VpnProfile mProfile;//配置文件

    private Spinner mSelectVpnType_spinner;
    private Spinner mSelectUserId_spinner;

    private LinearLayout mAdvancedSettings_ll;
    private ViewGroup mUserCertificate;
    private ViewGroup mUsernamePassword;

    private RelativeLayout mSelectUserCert;
    private RelativeLayout mSelectCert_relativelayout;
    private RelativeLayout mTncNotice;

    private MultiAutoCompleteTextView mName;
    private MultiAutoCompleteTextView mRemoteId;

    private EditText mGateway;
    private EditText mUsername;
    private EditText mPassword;
    private EditText mMTU;
    private EditText mPort;

    private TextInputLayoutHelper mNameWrap;
    private TextInputLayoutHelper mUsernameWrap;
    private TextInputLayoutHelper mRemoteIdWrap;
    private TextInputLayoutHelper mMTUWrap;
    private TextInputLayoutHelper mPortWrap;

    private CheckBox mCheckAuto_checkbox;
    private CheckBox mShowAdvanced_checkbox;
    private CheckBox mBlockIPv4_checkbox;
    private CheckBox mBlockIPv6_checkbox;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("添加VPN配置文件");
        //打开数据库
        mDataSource = new VpnProfileDataSource(this);
        mDataSource.open();
        //加载布局文件
        setContentView(R.layout.profile_detail_view);

        //初始化view
        mName = (MultiAutoCompleteTextView) findViewById(R.id.name);
        mNameWrap = (TextInputLayoutHelper) findViewById(R.id.name_wrap);
        mGateway = (EditText) findViewById(R.id.gateway);
        mSelectVpnType_spinner = (Spinner) findViewById(R.id.vpn_type);
        mTncNotice = (RelativeLayout) findViewById(R.id.tnc_notice);

        mUsernamePassword = (ViewGroup) findViewById(R.id.username_password_group);
        mUsername = (EditText) findViewById(R.id.username);
        mUsernameWrap = (TextInputLayoutHelper) findViewById(R.id.username_wrap);
        mPassword = (EditText) findViewById(R.id.password);

        mUserCertificate = (ViewGroup) findViewById(R.id.user_certificate_group);
        mSelectUserCert = (RelativeLayout) findViewById(R.id.select_user_certificate);
        mSelectUserId_spinner = (Spinner) findViewById(R.id.select_user_id);

        mCheckAuto_checkbox = (CheckBox) findViewById(R.id.ca_auto);
        mSelectCert_relativelayout = (RelativeLayout) findViewById(R.id.select_certificate);

        mShowAdvanced_checkbox = (CheckBox) findViewById(R.id.show_advanced);
        mAdvancedSettings_ll = (LinearLayout) findViewById(R.id.advanced_settings);

        mRemoteId = (MultiAutoCompleteTextView) findViewById(R.id.remote_id);
        mRemoteIdWrap = (TextInputLayoutHelper) findViewById(R.id.remote_id_wrap);
        mMTU = (EditText) findViewById(R.id.mtu);
        mMTUWrap = (TextInputLayoutHelper) findViewById(R.id.mtu_wrap);
        mPort = (EditText) findViewById(R.id.port);
        mPortWrap = (TextInputLayoutHelper) findViewById(R.id.port_wrap);
        mBlockIPv4_checkbox = (CheckBox) findViewById(R.id.split_tunneling_v4);
        mBlockIPv6_checkbox = (CheckBox) findViewById(R.id.split_tunneling_v6);

        final SpaceTokenizer spaceTokenizer = new SpaceTokenizer();
        mName.setTokenizer(spaceTokenizer);//配置名字（可选）
        mRemoteId.setTokenizer(spaceTokenizer);//服务器id
        final ArrayAdapter<String> completeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);
        mName.setAdapter(completeAdapter);
        mRemoteId.setAdapter(completeAdapter);

//        mGateway.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//            }
//
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//            }
//
//            @Override
//            public void afterTextChanged(Editable s) {
//                completeAdapter.clear();
//                completeAdapter.add(mGateway.getText().toString());
//                if (TextUtils.isEmpty(mGateway.getText())) {
//                    mNameWrap.setHelperText(getString(R.string.profile_name_hint));
//                    mRemoteIdWrap.setHelperText(getString(R.string.profile_remote_id_hint));
//                } else {
//                    mNameWrap.setHelperText(String.format(getString(R.string.profile_name_hint_gateway), mGateway.getText()));
//                    mRemoteIdWrap.setHelperText(String.format(getString(R.string.profile_remote_id_hint_gateway), mGateway.getText()));
//                }
//            }
//        });

        //选择连接类型的spinner 点击事件，从对应VpnType中取值
        mSelectVpnType_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mVpnType = VpnType.values()[position];
                updateCredentialView();//根据选择的连接类型显示需要配置的属性
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {	/* should not happen */
                mVpnType = VpnType.IKEV2_EAP;
                updateCredentialView();//根据选择的连接类型显示需要配置的属性
            }
        });

        ((TextView) mTncNotice.findViewById(android.R.id.text1)).setText(R.string.tnc_notice_title);
        ((TextView) mTncNotice.findViewById(android.R.id.text2)).setText(R.string.tnc_notice_subtitle);
        mTncNotice.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new TncNoticeDialog().show(VpnProfileDetailActivity.this.getSupportFragmentManager(), "TncNotice");
            }
        });

        //用户证书
        mSelectUserCert.setOnClickListener(new SelectUserCertOnClickListener());
        //根据用户证书，生成用户身份信息
        mSelectUserIdAdapter = new CertificateIdentitiesAdapter(this);
        mSelectUserId_spinner.setAdapter(mSelectUserIdAdapter);
        //选择用户身份
        mSelectUserId_spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mUserCertEntry != null) {
                    /* we don't store the subject DN as it is in the reverse order and the default anyway */
                    mSelectedUserId = position == 0 ? null : mSelectUserIdAdapter.getItem(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSelectedUserId = null;
            }
        });

        //自动选择CA证书
        mCheckAuto_checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateCertificateSelector();//设置显示或隐藏添加ca证书
            }
        });

        //选择CA证书，执行的操作,跳转界面
        mSelectCert_relativelayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VpnProfileDetailActivity.this, TrustedCertificatesActivity.class);
                intent.setAction(TrustedCertificatesActivity.SELECT_CERTIFICATE);
                startActivityForResult(intent, SELECT_TRUSTED_CERTIFICATE);
            }
        });

        //显示高级选项
        mShowAdvanced_checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateAdvancedSettings();//设置显示或隐藏
            }
        });

        //初始化界面数据
        mId = savedInstanceState == null ? null : savedInstanceState.getLong(VpnProfileDataSource.KEY_ID);
        if (mId == null) {
            Bundle extras = getIntent().getExtras();
            mId = extras == null ? null : extras.getLong(VpnProfileDataSource.KEY_ID);
        }

        loadProfileData(savedInstanceState);//加载配置文件数据
        updateCredentialView();//加载用户证书，显示或隐藏
        updateCertificateSelector();//加载CA证书选择器，显示还是隐藏
        updateAdvancedSettings();//加载高级设置，显示或隐藏

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDataSource.close();
        Log.e("tag", "mCACertEntry==" + (mCACertEntry == null));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //保存设置
        if (mId != null) {
            outState.putLong(VpnProfileDataSource.KEY_ID, mId);
        }
        if (mUserCertEntry != null) {
            outState.putString(VpnProfileDataSource.KEY_USER_CERTIFICATE, mUserCertEntry.getAlias());
        }
        if (mSelectedUserId != null) {
            outState.putString(VpnProfileDataSource.KEY_LOCAL_ID, mSelectedUserId);
        }
        if (mCACertEntry != null) {
            outState.putString(VpnProfileDataSource.KEY_CERTIFICATE, mCACertEntry.getAlias());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.menu_cancel://取消
                finish();
                return true;
            case R.id.menu_accept:
                saveProfile();//保存
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_TRUSTED_CERTIFICATE://CA证书，选择完回调
                if (resultCode == RESULT_OK) {
                    String alias = data.getStringExtra(VpnProfileDataSource.KEY_CERTIFICATE);
                    X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);//根据别名生成证书
                    mCACertEntry = certificate == null ? null : new TrustedCertificateEntry(alias, certificate);//生成TrustedCertificateEntry
                    updateCertificateSelector();//调整显示状态
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Update the UI to enter credentials depending on the type of VPN currently selected
     * 根据选择的连接类型显示需要配置的属性
     */
    private void updateCredentialView() {
        TextView text1 = (TextView) mSelectUserCert.findViewById(android.R.id.text1);
        TextView text2 = (TextView) mSelectUserCert.findViewById(android.R.id.text2);
        mUsernamePassword.setVisibility(mVpnType.has(VpnTypeFeature.USER_PASS) ? View.VISIBLE : View.GONE);
        mUserCertificate.setVisibility(mVpnType.has(VpnTypeFeature.CERTIFICATE) ? View.VISIBLE : View.GONE);
        mTncNotice.setVisibility(mVpnType.has(VpnTypeFeature.BYOD) ? View.VISIBLE : View.GONE);

        if (mVpnType.has(VpnTypeFeature.CERTIFICATE)) {
            mSelectUserId_spinner.setEnabled(false);
            if (mUserCertLoading != null) {
                text1.setText(mUserCertLoading);
                text2.setText(R.string.loading);
            } else if (mUserCertEntry != null) {	/* clear any errors and set the new data */
                text1.setError(null);
                text1.setText(mUserCertEntry.getAlias());
                text2.setText(mUserCertEntry.getCertificate().getSubjectDN().toString());
                mSelectUserIdAdapter.setCertificate(mUserCertEntry);
                mSelectUserId_spinner.setSelection(mSelectUserIdAdapter.getPosition(mSelectedUserId));
                mSelectUserId_spinner.setEnabled(true);
            } else {
                text1.setText("选择证书");
                text2.setText("请选择一个证书");
                mSelectUserIdAdapter.setCertificate(null);
            }
        }
    }

    /**
     * Show an alert in case the previously selected certificate is not found anymore
     * or the user did not select a certificate in the spinner.
     */
    private void showCertificateAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(VpnProfileDetailActivity.this);
        builder.setTitle("未发现CA证书");
        builder.setMessage(R.string.alert_text_nocertfound);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    /**
     * Update the CA certificate selection UI depending on whether the
     * certificate should be automatically selected or not.
     * CA证书选择器，显示还是隐藏
     */
    private void updateCertificateSelector() {
        if (!mCheckAuto_checkbox.isChecked()) {//非选中时，显示布局
            mSelectCert_relativelayout.setEnabled(true);
            mSelectCert_relativelayout.setVisibility(View.VISIBLE);

            if (mCACertEntry != null) {
                ((TextView) mSelectCert_relativelayout.findViewById(android.R.id.text1)).setText(mCACertEntry.getSubjectPrimary());
                ((TextView) mSelectCert_relativelayout.findViewById(android.R.id.text2)).setText(mCACertEntry.getSubjectSecondary());
            } else {
                ((TextView) mSelectCert_relativelayout.findViewById(android.R.id.text1)).setText(R.string.profile_ca_select_certificate_label);
                ((TextView) mSelectCert_relativelayout.findViewById(android.R.id.text2)).setText(R.string.profile_ca_select_certificate);
            }
        } else {//选中时隐藏布局，自动选择CA证书
            mSelectCert_relativelayout.setEnabled(false);
            mSelectCert_relativelayout.setVisibility(View.GONE);
        }
    }

    /**
     * Update the advanced settings UI depending on whether any advanced settings have already been made.
     * 更新高级设置
     */
    private void updateAdvancedSettings() {
        boolean show = mShowAdvanced_checkbox.isChecked();
        if (!show && mProfile != null) {
            Integer st = mProfile.getSplitTunneling();
            show = mProfile.getRemoteId() != null || mProfile.getMTU() != null || mProfile.getPort() != null || (st != null && st != 0);
        }
        mShowAdvanced_checkbox.setVisibility(show ? View.GONE : View.VISIBLE);
        mAdvancedSettings_ll.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Save or update the profile depending on whether we actually have a
     * profile object or not (this was created in updateProfileData)
     * 保存配置文件到数据库
     */
    private void saveProfile() {
        //检查输入是否有效先
        if (verifyInput()) {
            if (mProfile != null) {
                updateProfileData();
                mDataSource.updateVpnProfile(mProfile);
            } else {
                mProfile = new VpnProfile();
                updateProfileData();
                mDataSource.insertProfile(mProfile);//保存到数据库中
            }
            setResult(RESULT_OK, new Intent().putExtra(VpnProfileDataSource.KEY_ID, mProfile.getId()));//数据传递给前一个界面
            finish();
        }
    }

    /**
     * Verify the user input and display error messages.
     * 检查输入
     *
     * @return true if the input is valid
     */
    private boolean verifyInput() {
        boolean valid = true;
        if (mGateway.getText().toString().trim().isEmpty()) {
            mGateway.setError("ip地址必填");
            valid = false;
        }
        if (mVpnType.has(VpnTypeFeature.USER_PASS)) {
            if (mUsername.getText().toString().trim().isEmpty()) {
                mUsername.setError("用户名不可为空");
                valid = false;
            }
        }
        if (mVpnType.has(VpnTypeFeature.CERTIFICATE) && mUserCertEntry == null) {	/* let's show an error icon */
            ((TextView) mSelectUserCert.findViewById(android.R.id.text1)).setError("还没有选择用户证书");
            valid = false;
        }
        if (!mCheckAuto_checkbox.isChecked() && mCACertEntry == null) {
            showCertificateAlert();
            valid = false;
        }
        Integer mtu = getInteger(mMTU);
        if (mtu != null && (mtu < MTU_MIN || mtu > MTU_MAX)) {
            mMTUWrap.setError(String.format(getString(R.string.alert_text_out_of_range), MTU_MIN, MTU_MAX));
            valid = false;
        }
        Integer port = getInteger(mPort);
        if (port != null && (port < 1 || port > 65535)) {
            mPortWrap.setError(String.format(getString(R.string.alert_text_out_of_range), 1, 65535));
            valid = false;
        }
        return valid;
    }

    /**
     * Update the profile object with the data entered by the user
     * 更新用户输入的配置数据
     */
    private void updateProfileData() {
        /* the name is optional, we default to the gateway if none is given */
        String name = mName.getText().toString().trim();//名字
        String gateway = mGateway.getText().toString().trim();//网关
        mProfile.setName(name.isEmpty() ? gateway : name);//1
        mProfile.setGateway(gateway);//2

        mProfile.setVpnType(mVpnType);//3连接类型
        if (mVpnType.has(VpnTypeFeature.USER_PASS)) {//查看特征是否需要用户名，密码
            mProfile.setUsername(mUsername.getText().toString().trim());//4
            String password = mPassword.getText().toString().trim();
            password = password.isEmpty() ? null : password;
            mProfile.setPassword(password);//5
        }
        if (mVpnType.has(VpnTypeFeature.CERTIFICATE)) {//查看特征是否需要证书，
            mProfile.setUserCertificateAlias(mUserCertEntry.getAlias());//6
            mProfile.setLocalId(mSelectedUserId);//7
        }

        String certAlias = mCheckAuto_checkbox.isChecked() ? null : mCACertEntry.getAlias();
        mProfile.setCertificateAlias(certAlias);//8
        String remote_id = mRemoteId.getText().toString().trim();
        mProfile.setRemoteId(remote_id.isEmpty() ? null : remote_id);//9
        mProfile.setMTU(getInteger(mMTU));//10
        mProfile.setPort(getInteger(mPort));//11
        int st = 0;
        st |= mBlockIPv4_checkbox.isChecked() ? VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4 : 0;
        st |= mBlockIPv6_checkbox.isChecked() ? VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6 : 0;
        mProfile.setSplitTunneling(st == 0 ? null : st);//12
    }

    /**
     * Load an existing profile if we got an ID
     * 初始化数据
     *
     * @param savedInstanceState previously saved state
     */
    private void loadProfileData(Bundle savedInstanceState) {
        String useralias = null, local_id = null, alias = null;

        if (mId != null && mId != 0) {
            mProfile = mDataSource.getVpnProfile(mId);
            if (mProfile != null) {
                mName.setText(mProfile.getName());
                mGateway.setText(mProfile.getGateway());
                mVpnType = mProfile.getVpnType();
                mUsername.setText(mProfile.getUsername());
                mPassword.setText(mProfile.getPassword());
                mRemoteId.setText(mProfile.getRemoteId());
                mMTU.setText(mProfile.getMTU() != null ? mProfile.getMTU().toString() : null);
                mPort.setText(mProfile.getPort() != null ? mProfile.getPort().toString() : null);
                mBlockIPv4_checkbox.setChecked(mProfile.getSplitTunneling() != null ? (mProfile.getSplitTunneling() & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) != 0 : false);
                mBlockIPv6_checkbox.setChecked(mProfile.getSplitTunneling() != null ? (mProfile.getSplitTunneling() & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) != 0 : false);
                useralias = mProfile.getUserCertificateAlias();
                local_id = mProfile.getLocalId();
                alias = mProfile.getCertificateAlias();
                getSupportActionBar().setTitle(mProfile.getName());
            } else {
                Log.e(VpnProfileDetailActivity.class.getSimpleName(),
                        "VPN profile with id " + mId + " not found");
                finish();
            }
        }

        mSelectVpnType_spinner.setSelection(mVpnType.ordinal());

		/* check if the user selected a user certificate previously */
        useralias = savedInstanceState == null ? useralias : savedInstanceState.getString(VpnProfileDataSource.KEY_USER_CERTIFICATE);
        local_id = savedInstanceState == null ? local_id : savedInstanceState.getString(VpnProfileDataSource.KEY_LOCAL_ID);
        if (useralias != null) {
            UserCertificateLoader loader = new UserCertificateLoader(this, useralias);
            mUserCertLoading = useralias;
            mSelectedUserId = local_id;
            loader.execute();
        }

		/* check if the user selected a CA certificate previously */
        alias = savedInstanceState == null ? alias : savedInstanceState.getString(VpnProfileDataSource.KEY_CERTIFICATE);
        mCheckAuto_checkbox.setChecked(alias == null);
        if (alias != null) {
            X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);
            if (certificate != null) {
                mCACertEntry = new TrustedCertificateEntry(alias, certificate);
            } else {	/* previously selected certificate is not here anymore */
                showCertificateAlert();
                mCACertEntry = null;
            }
        }
    }

    /**
     * Get the integer value in the given text box or null if empty
     *
     * @param view text box (numeric entry assumed)
     */
    private Integer getInteger(EditText view) {
        String value = view.getText().toString().trim();
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    //用户证书点击事件
    private class SelectUserCertOnClickListener implements OnClickListener, KeyChainAliasCallback {
        @SuppressWarnings("WrongConstant")
        @Override
        public void onClick(View v) {
            String useralias = mUserCertEntry != null ? mUserCertEntry.getAlias() : null;
            KeyChain.choosePrivateKeyAlias(VpnProfileDetailActivity.this, this, new String[]{"RSA"}, null, null, -1, useralias);
        }

        @Override
        public void alias(final String alias) {
            if (alias != null) {	/* otherwise the dialog was canceled, the request denied */
                try {
                    final X509Certificate[] chain = KeyChain.getCertificateChain(VpnProfileDetailActivity.this, alias);
                    /* alias() is not called from our main thread */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (chain != null && chain.length > 0) {
                                mUserCertEntry = new TrustedCertificateEntry(alias, chain[0]);
                            }
                            updateCredentialView();//改变布局状态
                        }
                    });
                } catch (KeyChainException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Load the selected user certificate asynchronously.  This cannot be done
     * from the main thread as getCertificateChain() calls back to our main
     * thread to bind to the KeyChain service resulting in a deadlock.
     */
    private class UserCertificateLoader extends AsyncTask<Void, Void, X509Certificate> {
        private final Context mContext;
        private final String mAlias;

        public UserCertificateLoader(Context context, String alias) {
            mContext = context;
            mAlias = alias;
        }

        @Override
        protected X509Certificate doInBackground(Void... params) {
            X509Certificate[] chain = null;
            try {
                chain = KeyChain.getCertificateChain(mContext, mAlias);
            } catch (KeyChainException | InterruptedException e) {
                e.printStackTrace();
            }
            if (chain != null && chain.length > 0) {
                return chain[0];
            }
            return null;
        }

        @Override
        protected void onPostExecute(X509Certificate result) {
            if (result != null) {
                mUserCertEntry = new TrustedCertificateEntry(mAlias, result);
            } else {	/* previously selected certificate is not here anymore */
                ((TextView) mSelectUserCert.findViewById(android.R.id.text1)).setError("");
                mUserCertEntry = null;
            }
            mUserCertLoading = null;
            updateCredentialView();//改变显示状态
        }
    }

    /**
     * Dialog with notification message if EAP-TNC is used.
     */
    public static class TncNoticeDialog extends AppCompatDialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.tnc_notice_title)
                    .setMessage(Html.fromHtml(getString(R.string.tnc_notice_details)))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    }).create();
        }
    }

    /**
     * Tokenizer implementation that separates by white-space
     */
    public static class SpaceTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;

            while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
                i--;
            }
            return i;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();

            while (i < len) {
                if (Character.isWhitespace(text.charAt(i))) {
                    return i;
                } else {
                    i++;
                }
            }
            return len;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();

            if (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
                return text;
            } else {
                if (text instanceof Spanned) {
                    SpannableString sp = new SpannableString(text + " ");
                    TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, sp, 0);
                    return sp;
                } else {
                    return text + " ";
                }
            }
        }
    }
}
