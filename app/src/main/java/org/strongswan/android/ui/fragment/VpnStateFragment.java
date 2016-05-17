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

package org.strongswan.android.ui.fragment;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.logic.VpnStateService.VpnStateListener;
import org.strongswan.android.logic.imc.ImcState;
import org.strongswan.android.logic.imc.RemediationInstruction;
import org.strongswan.android.ui.activity.LogActivity;
import org.strongswan.android.ui.activity.RemediationInstructionsActivity;

import java.util.ArrayList;
import java.util.List;

public class VpnStateFragment extends Fragment implements VpnStateListener {
    private static final String KEY_ERROR_CONNECTION_ID = "error_connection_id";
    private static final String KEY_DISMISSED_CONNECTION_ID = "dismissed_connection_id";

    private TextView mProfileNameTextView;//配置名
    private TextView mProfileTextView;//配置名字，用于显示隐藏
    private TextView mStateTextView;//状态
    private Button mActionButton;//连接按钮
    private ProgressBar mProgress;//连接显示的进度条
    private AlertDialog mErrorDialog;

    private long mErrorConnectionID;
    private long mDismissedConnectionID;

    private VpnStateService mService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //获取服务对象
            mService = ((VpnStateService.LocalBinder) service).getService();
            //注册状态改变监听
            mService.registerListener(VpnStateFragment.this);
            //
            updateView();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		/* bind to the service only seems to work from the ApplicationContext */
        Context context = getActivity().getApplicationContext();
        context.bindService(new Intent(context, VpnStateService.class), mServiceConnection, Service.BIND_AUTO_CREATE);

        mErrorConnectionID = 0;
        mDismissedConnectionID = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ERROR_CONNECTION_ID)) {
            mErrorConnectionID = (Long) savedInstanceState.getSerializable(KEY_ERROR_CONNECTION_ID);
            mDismissedConnectionID = (Long) savedInstanceState.getSerializable(KEY_DISMISSED_CONNECTION_ID);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(KEY_ERROR_CONNECTION_ID, mErrorConnectionID);
        outState.putSerializable(KEY_DISMISSED_CONNECTION_ID, mDismissedConnectionID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.vpn_state_fragment, null);
        mProgress = (ProgressBar) view.findViewById(R.id.progress);
        mStateTextView = (TextView) view.findViewById(R.id.vpn_state);
        mProfileTextView = (TextView) view.findViewById(R.id.vpn_profile_label);
        mProfileNameTextView = (TextView) view.findViewById(R.id.vpn_profile_name);
        mActionButton = (Button) view.findViewById(R.id.action);

        mActionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //断开连接，或取消连接
                if (mService != null) {
                    mService.disconnect();
                }
            }
        });
        enableActionButton(null);//隐藏按钮

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        //注册监听
        if (mService != null) {
            mService.registerListener(this);
            //调整连接状态
            updateView();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        //取消注册
        if (mService != null) {
            mService.unregisterListener(this);
        }
        hideErrorDialog();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            getActivity().getApplicationContext().unbindService(mServiceConnection);
        }
    }

    @Override
    public void stateChanged() {
        //服务中状态改变监听回调，改变连接状态
        updateView();
    }

    /**
     * 改变状态
     */
    public void updateView() {
        long connectionID = mService.getConnectionID();//获取连接id
        VpnProfile profile = mService.getProfile();//配置
        State state = mService.getState();//连接状态
        ErrorState error = mService.getErrorState();//错误状态
        ImcState imcState = mService.getImcState();//
        String name = "";
        String gateway = "";

        if (profile != null) {
            name = profile.getName();
            gateway = profile.getGateway();
        }

        if (reportError(connectionID, name, error, imcState)) {
            return;
        }

        mProfileNameTextView.setText(name);

        switch (state) {
            case DISABLED://未连接时状态
                showProfile(false);
                mProgress.setVisibility(View.GONE);
                enableActionButton(null);//隐藏按钮
                mStateTextView.setText(R.string.state_disabled);
                break;
            case CONNECTING://连接中
                showProfile(true);
                mProgress.setVisibility(View.VISIBLE);
                enableActionButton(getString(android.R.string.cancel));
                mStateTextView.setText(R.string.state_connecting);
                break;
            case CONNECTED://已连接
                showProfile(true);
                mProgress.setVisibility(View.GONE);
                enableActionButton(getString(R.string.disconnect));
                mStateTextView.setText(R.string.state_connected);
                break;
            case DISCONNECTING://断开中
                showProfile(true);
                mProgress.setVisibility(View.VISIBLE);
                enableActionButton(null);//隐藏按钮
                mStateTextView.setText(R.string.state_disconnecting);
                break;
        }
    }

    private boolean reportError(long connectionID, String name, ErrorState error, ImcState imcState) {
        if (connectionID > mDismissedConnectionID) {
            /* report error if it hasn't been dismissed yet */
            mErrorConnectionID = connectionID;
        } else {
            /* ignore all other errors */
            error = ErrorState.NO_ERROR;
        }
        if (error == ErrorState.NO_ERROR) {
            hideErrorDialog();
            return false;
        } else if (mErrorDialog != null) {
            /* we already show the dialog */
            return true;
        }
        mProfileNameTextView.setText(name);
        showProfile(true);
        mProgress.setVisibility(View.GONE);
        enableActionButton(null);//隐藏按钮
        mStateTextView.setText(R.string.state_error);
        switch (error) {
            case AUTH_FAILED:
                if (imcState == ImcState.BLOCK) {
                    showErrorDialog(R.string.error_assessment_failed);
                } else {
                    showErrorDialog(R.string.error_auth_failed);
                }
                break;
            case PEER_AUTH_FAILED:
                showErrorDialog(R.string.error_peer_auth_failed);
                break;
            case LOOKUP_FAILED:
                showErrorDialog(R.string.error_lookup_failed);
                break;
            case UNREACHABLE:
                showErrorDialog(R.string.error_unreachable);
                break;
            default:
                showErrorDialog(R.string.error_generic);
                break;
        }
        return true;
    }

    /**
     * 显示或隐藏信息
     *
     * @param show 显示或隐藏
     */
    private void showProfile(boolean show) {
        mProfileTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileNameTextView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    //改变按钮的状态
    private void enableActionButton(String text) {
        mActionButton.setText(text);//
        mActionButton.setEnabled(text != null);//为空时不可用
        mActionButton.setVisibility(text != null ? View.VISIBLE : View.GONE);//为空时隐藏
    }

    /**
     * 隐藏错误对话框
     */
    private void hideErrorDialog() {
        if (mErrorDialog != null) {
            mErrorDialog.dismiss();
            mErrorDialog = null;
        }
    }

    //清除错误
    private void clearError() {
        if (mService != null) {
            mService.disconnect();
        }
        mDismissedConnectionID = mErrorConnectionID;
        updateView();
    }

    /**
     * 显示错误dialog
     *
     * @param textid textid
     */
    private void showErrorDialog(int textid) {
        final List<RemediationInstruction> instructions = mService.getRemediationInstructions();
        final boolean show_instructions = mService.getImcState() == ImcState.BLOCK && !instructions.isEmpty();
        int text = show_instructions ? R.string.show_remediation_instructions : R.string.show_log;

        mErrorDialog = new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.error_introduction) + " " + getString(textid))
                .setCancelable(false)
                .setNeutralButton(text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearError();
                        dialog.dismiss();
                        Intent intent;
                        if (show_instructions) {
                            intent = new Intent(getActivity(), RemediationInstructionsActivity.class);
                            intent.putParcelableArrayListExtra(
                                    RemediationInstructionsFragment.EXTRA_REMEDIATION_INSTRUCTIONS, new ArrayList<>(instructions));
                        } else {
                            intent = new Intent(getActivity(), LogActivity.class);
                        }
                        startActivity(intent);
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        clearError();
                        dialog.dismiss();
                    }
                }).create();
        //注册消失监听，将dialog及时置空，GC
        mErrorDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mErrorDialog = null;
            }
        });
        mErrorDialog.show();
    }
}
