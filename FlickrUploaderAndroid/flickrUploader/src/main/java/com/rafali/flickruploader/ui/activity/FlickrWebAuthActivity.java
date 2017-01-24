package com.rafali.flickruploader.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.auth.Permission;
import com.googlecode.flickrjandroid.oauth.OAuth;
import com.googlecode.flickrjandroid.oauth.OAuthInterface;
import com.googlecode.flickrjandroid.oauth.OAuthToken;
import com.googlecode.flickrjandroid.people.User;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

@EActivity(R.layout.flickr_web_auth_activity)
public class FlickrWebAuthActivity extends AppCompatActivity {

    public static final int RESULT_CODE_AUTH = 2227;
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrWebAuthActivity.class);

    // The CALLBACK_SCHEME must match the intent-filter in AndroidManifest.xml
    private static final String CALLBACK_SCHEME = "flickruploader-flickr-oauth";
    private static final Uri OAUTH_CALLBACK_URI = Uri.parse(CALLBACK_SCHEME + "://oauth");

    @ViewById(R.id.progress_container)
    View progressContainer;

    @ViewById(R.id.error_container)
    View errorContainer;

    @ViewById(R.id.progress_text)
    TextView progressText;

    @ViewById(R.id.error_text)
    TextView errorText;

    @Click(R.id.error_button)
    void onErrorClick() {
        loadAuthorizationUrl();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);
    }

    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    @AfterViews
    protected void onAfterViews() {
        setLoading("Opening browser…");
        if (System.currentTimeMillis() - Utils.getLongProperty(STR.lastBrowserOpenForAuth) < 10 * 60 * 1000L) {
            doDataCallback();
        } else {
            loadAuthorizationUrl();
        }
    }

    /**
     * Authorize with Flickr.
     *
     * <p>Inspired by:
     * https://github.com/yuyang226/FlickrjApi4Android/blob/f4698097ae81c37937f0c4e46c5bad9f81b963e9/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/tasks/OAuthTask.java#L74
     */
    @Background
    void loadAuthorizationUrl() {
        LOG.info("Loading authorization URL");

        try {
            Flickr f = FlickrApi.get();
            OAuthToken oauthToken =
                    f.getOAuthInterface().getRequestToken(OAUTH_CALLBACK_URI.toString());
            saveOAuthToken(null, null, oauthToken.getOauthToken(), oauthToken.getOauthTokenSecret());
            URL oauthUrl = f.getOAuthInterface().buildAuthenticationUrl(
                    Permission.READ, oauthToken);
            String result = oauthUrl.toString();

            if (result != null && !result.startsWith("error") ) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result)));
            } else {
                throw new IOException(result);
            }
        } catch (Throwable e) {
            setError(e);
        }
    }

    private void saveOAuthToken(String userName, String userId, String token, String tokenSecret) {
        LOG.info("Saving OAuth token: user={} id={} token={} secret={}",
                userName, userId, token, tokenSecret);
        Utils.setStringProperty(STR.accessToken, token);
        Utils.setStringProperty(STR.accessTokenSecret, tokenSecret);
        Utils.setStringProperty(STR.userId, userId);
        Utils.setStringProperty(STR.userName, userName);
    }

    @Override
    protected void onStart() {
        super.onStart();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        GoogleAnalytics.getInstance(this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
    }

    @Override
    protected void onResume() {
        LOG.info("Resuming");
        doDataCallback();
        super.onResume();
    }

    @UiThread
    void setLoading(String message) {
        errorContainer.setVisibility(View.GONE);
        progressContainer.setVisibility(View.VISIBLE);
        progressText.setText(message);
    }

    @UiThread
    void setError(Throwable e) {
        LOG.error("setError", e);
        errorContainer.setVisibility(View.VISIBLE);
        progressContainer.setVisibility(View.GONE);
        errorText.setText("Error: " + (ToolString.isBlank(e.getMessage()) ? e.getClass().getName() : e.getMessage()));
    }

    /**
     * @see "https://github.com/yuyang226/FlickrjApi4Android/blob/1b672163d89a34f603898e1d3d3bbd2d3fbb9666/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/FlickrjAndroidSampleActivity.java#L164"
     */
    private OAuth getOAuthToken() {
        LOG.info("Getting OAuth token");

        String oauthTokenString = Utils.getStringProperty(STR.accessToken, null);
        String tokenSecret = Utils.getStringProperty(STR.accessTokenSecret, null);
        if (oauthTokenString == null && tokenSecret == null) {
            LOG.warn("No oauth token retrieved");
            return null;
        }

        OAuth oauth = new OAuth();
        String userName = Utils.getStringProperty(STR.userName, null);
        String userId = Utils.getStringProperty(STR.userId, null);
        if (userId != null) {
            User user = new User();
            user.setUsername(userName);
            user.setId(userId);
            oauth.setUser(user);
        }

        OAuthToken oauthToken = new OAuthToken();
        oauth.setToken(oauthToken);
        oauthToken.setOauthToken(oauthTokenString);
        oauthToken.setOauthTokenSecret(tokenSecret);
        LOG.debug("Retrieved token from preference store: oauth token={}, and token secret={}", oauthTokenString, tokenSecret);
        return oauth;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // See: https://github.com/yuyang226/FlickrjApi4Android/blob/1b672163d89a34f603898e1d3d3bbd2d3fbb9666/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/FlickrjAndroidSampleActivity.java#L91
        setIntent(intent);
    }

    /**
     * @see "https://github.com/yuyang226/FlickrjApi4Android/blob/1b672163d89a34f603898e1d3d3bbd2d3fbb9666/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/FlickrjAndroidSampleActivity.java#L110"
     */
    @Background
    void doDataCallback() {
        LOG.info("Doing the data callback");

        try {
            Utils.clearProperty(STR.lastBrowserOpenForAuth);
            setLoading("Almost done…");

            // Note that the intent has to be set in onNewIntent() first:
            // https://github.com/yuyang226/FlickrjApi4Android/blob/1b672163d89a34f603898e1d3d3bbd2d3fbb9666/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/FlickrjAndroidSampleActivity.java#L91
            Intent intent = getIntent();

            String scheme = intent.getScheme();
            OAuth savedToken = getOAuthToken();
            if (CALLBACK_SCHEME.equals(scheme) && (savedToken == null || savedToken.getUser() == null)) {
                Uri uri = intent.getData();
                String query = uri.getQuery();
                LOG.debug("Returned Query: {}", query);
                String[] data = query.split("&");
                if (data.length == 2) {
                    String oauthToken = data[0].substring(data[0].indexOf("=") + 1);
                    String oauthVerifier = data[1]
                            .substring(data[1].indexOf("=") + 1); //$NON-NLS-1$
                    LOG.debug("OAuth Token: {}; OAuth Verifier: {}", oauthToken, oauthVerifier);

                    OAuth oauth = getOAuthToken();
                    LOG.debug("OAuth: {}", oauth);
                    if (oauth != null && oauth.getToken() != null && oauth.getToken().getOauthTokenSecret() != null) {
                        // From:
                        // https://github.com/yuyang226/FlickrjApi4Android/blob/f4698097ae81c37937f0c4e46c5bad9f81b963e9/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/tasks/GetOAuthTokenTask.java#L30
                        Flickr f = FlickrApi.get();
                        OAuthInterface oauthApi = f.getOAuthInterface();
                        OAuth finalOauth = oauthApi.getAccessToken(
                                oauthToken,
                                oauth.getToken().getOauthTokenSecret(),
                                oauthVerifier);
                        onOauthDone(finalOauth);
                    }
                }
            }
        } catch (Throwable e) {
            setError(e);
        }
    }

    /**
     * @see "https://github.com/yuyang226/FlickrjApi4Android/blob/1b672163d89a34f603898e1d3d3bbd2d3fbb9666/flickrj-android-sample-android/src/com/gmail/yuyang226/flickrj/sample/android/FlickrjAndroidSampleActivity.java#L137"
     */
    private void onOauthDone(OAuth oAuth) {
        LOG.info("Auth done");

        if (oAuth == null) {
            setError(new IOException("Authorization failed"));
            return;
        }

        User user = oAuth.getUser();
        OAuthToken token = oAuth.getToken();
        if (user == null || user.getId() == null || token == null
                || token.getOauthToken() == null
                || token.getOauthTokenSecret() == null) {
            setError(new IOException("Authorization failed"));
            return;
        }

        String message = String.format(Locale.US, "Authorization Succeed: user=%s, userId=%s, oauthToken=%s, tokenSecret=%s", //$NON-NLS-1$
                user.getUsername(), user.getId(), token.getOauthToken(), token.getOauthTokenSecret());
        LOG.info(message);
        saveOAuthToken(user.getUsername(), user.getId(), token.getOauthToken(), token.getOauthTokenSecret());

        FlickrApi.reset();
        FlickrApi.syncMedia();
        setResult(RESULT_CODE_AUTH);

        // Launch the main app here before finishing, otherwise we just end up back in the browser
        startActivity(FlickrUploaderActivity_.intent(this).get());

        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

}
