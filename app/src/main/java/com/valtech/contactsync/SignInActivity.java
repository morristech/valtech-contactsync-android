package com.valtech.contactsync;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;
import com.valtech.contactsync.api.ApiClient;
import com.valtech.contactsync.api.OAuthException;
import com.valtech.contactsync.api.UserInfoResponse;
import com.valtech.contactsync.setting.Settings;

public class SignInActivity extends AccountAuthenticatorActivity {
  private static final String TAG = SignInActivity.class.getSimpleName();

  private ApiClient apiClient;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    apiClient = new ApiClient(this);
    Intent intent = getIntent();

    if (intent != null && intent.getData() != null && getString(R.string.app_scheme).equals(intent.getData().getScheme())) {
      Log.i(TAG, "OAuth callback received.");
      String code = getIntent().getData().getQueryParameter("code");
      new SignInTask().execute(code);
    } else {
      Log.i(TAG, "Launching browser to start OAuth flow.");
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, apiClient.getAuthorizeUrl());
      startActivity(browserIntent);
      finish();
    }
  }

  private class SignInTask extends AsyncTask<String, Void, Bundle> {
    @Override
    protected Bundle doInBackground(String... params) {
      try {
        ApiClient.TokenResponse tokenResponse = apiClient.getAccessTokenAndRefreshToken(params[0]);
        UserInfoResponse userInfoResponse = apiClient.getUserInfoMeResource(tokenResponse.accessToken);

        Log.i(TAG, "Signing in as user " + userInfoResponse.email + " in country " + userInfoResponse.countryCode + ".");

        AccountManager accountManager = AccountManager.get(SignInActivity.this);
        Account account = new Account(userInfoResponse.email, getString(R.string.account_type));
        boolean added = accountManager.addAccountExplicitly(account, tokenResponse.refreshToken, null);

        if (added) {
          Log.i(TAG, "Added account " + userInfoResponse.email + ".");
          Settings.setSyncEnabled(SignInActivity.this, userInfoResponse.countryCode, true);
          Log.i(TAG, "Enabled sync for " + userInfoResponse.countryCode + ".");
        } else {
          Log.i(TAG, "Updated refresh token for account " + userInfoResponse.email + ".");
          accountManager.setPassword(account, tokenResponse.refreshToken);

          // need to test getting an access token from the new refresh token to clear the
          // "sign in error" notification
          try {
            String accessToken = accountManager.blockingGetAuthToken(account, "access_token", true);
            accountManager.invalidateAuthToken(account.type, accessToken);
          } catch (Exception e) {
            Log.e(TAG, "Could not get access token from new refresh token.", e);
          }
        }

        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);

        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, userInfoResponse.email);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_type));
        return result;
      } catch (OAuthException e) {
        Log.e(TAG, "OAuth exception during sign-in", e);
        Bundle result = new Bundle();
        result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        return result;
      }
    }

    @Override
    protected void onPostExecute(final Bundle result) {
      if (result.containsKey(AccountManager.KEY_ERROR_CODE)) {
        new AlertDialog.Builder(SignInActivity.this)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setTitle(R.string.error)
          .setMessage(R.string.auth_error_message)
          .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              SignInActivity.this.setAccountAuthenticatorResult(result);
              SignInActivity.this.finish();
            }
          })
          .show();
      } else {
        String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
        Toast.makeText(SignInActivity.this, String.format(getString(R.string.account_added_toast), accountName), Toast.LENGTH_LONG).show();
        SignInActivity.this.setAccountAuthenticatorResult(result);
        SignInActivity.this.finish();
      }
    }
  }
}
