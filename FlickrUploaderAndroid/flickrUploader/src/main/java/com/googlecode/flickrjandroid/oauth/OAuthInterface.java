/*
 * Copyright (c) 2005 Aetrion LLC.
 */

package com.googlecode.flickrjandroid.oauth;

import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.FlickrException;
import com.googlecode.flickrjandroid.Parameter;
import com.googlecode.flickrjandroid.REST;
import com.googlecode.flickrjandroid.RequestContext;
import com.googlecode.flickrjandroid.Transport;
import com.googlecode.flickrjandroid.auth.Permission;
import com.googlecode.flickrjandroid.people.User;
import com.googlecode.flickrjandroid.util.UrlUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authentication interface for the new Flickr OAuth 1a support: http://www.flickr.com/services/api/auth.oauth.html
 *
 * @author Toby Yu
 */
public class OAuthInterface {

	private static final String KEY_OAUTH_CALLBACK_CONFIRMED = "oauth_callback_confirmed";
	private static final String KEY_OAUTH_TOKEN = "oauth_token";
	private static final String KEY_OAUTH_TOKEN_SECRET = "oauth_token_secret";
	private static final String KEY_OAUTH_VERIFIER = "oauth_verifier";

    private static final String PATH_OAUTH_REQUEST_TOKEN = "/services/oauth/request_token";
	private static final String PATH_OAUTH_ACCESS_TOKEN = "/services/oauth/access_token";
	public static final String PATH_REST = "/services/rest";
	private static final String URL_REQUEST_TOKEN = "https://" + Flickr.DEFAULT_API_HOST + PATH_OAUTH_REQUEST_TOKEN;
	private static final String URL_ACCESS_TOKEN = "https://" + Flickr.DEFAULT_API_HOST + PATH_OAUTH_ACCESS_TOKEN;

	public static final String URL_REST = "https://" + Flickr.DEFAULT_API_HOST + PATH_REST;

	public static final String PARAM_OAUTH_CONSUMER_KEY = "oauth_consumer_key";
	private static final String PARAM_OAUTH_TOKEN = "oauth_token";

	private String apiKey;
	private String sharedSecret;
	private REST oauthTransport;
	private static final Logger LOG = LoggerFactory.getLogger(OAuthInterface.class);

	/**
	 * Construct the AuthInterface.
	 *
	 * @param apiKey
	 *            The API key
	 * @param transport
	 *            The Transport interface
	 */
	public OAuthInterface(String apiKey, String sharedSecret, Transport transport) {
		this.apiKey = apiKey;
		this.sharedSecret = sharedSecret;
        oauthTransport = (REST) transport;
	}

	/**
	 * Get a request token.
	 */
	public OAuthToken getRequestToken(String callbackUrl) throws IOException, FlickrException {
		if (callbackUrl == null)
			callbackUrl = "oob";
		List<Parameter> parameters = new ArrayList<>();
		parameters.add(new Parameter("oauth_callback", callbackUrl));
		parameters.add(new Parameter(OAuthInterface.PARAM_OAUTH_CONSUMER_KEY, apiKey));
		OAuthUtils.addBasicOAuthParams(parameters);
		String signature = OAuthUtils.getSignature(OAuthUtils.REQUEST_METHOD_GET, URL_REQUEST_TOKEN, parameters, sharedSecret, null);
		// This method call must be signed.
		parameters.add(new Parameter("oauth_signature", signature));

		LOG.info("Getting Request Token " + PATH_OAUTH_REQUEST_TOKEN + " with parameters: {}", parameters);
		String token = null;
		String token_secret = null;
		int retry = 0;

		Throwable exc = null;
		while (token == null && retry < 5) {
			try {
				exc = null;
				Map<String, String> response = oauthTransport.getMapData(true, PATH_OAUTH_REQUEST_TOKEN, parameters);
				if (response.isEmpty()) {
					exc = new FlickrException("Empty Response", "Empty Response");
				}

				if (response.containsKey(KEY_OAUTH_CALLBACK_CONFIRMED) == false || Boolean.valueOf(response.get(KEY_OAUTH_CALLBACK_CONFIRMED)) == false) {
					exc = new FlickrException("Error", "Invalid response: " + response);
				}

				LOG.info("Response: {}", response);
				token = response.get(KEY_OAUTH_TOKEN);
				token_secret = response.get(KEY_OAUTH_TOKEN_SECRET);
			} catch (Exception e) {
				exc = e;
			} finally {
				retry++;
				if (exc != null) {
					try {
						LOG.warn("sleeping a bit, retry={}", retry, exc);
						Thread.sleep(retry * 1000L);
					} catch (InterruptedException e) {
                        LOG.warn("Interrupted waiting for request token", e);
					}
				}
			}
		}

		if (token == null && exc != null) {
			if (exc instanceof FlickrException)
				throw (FlickrException) exc;
			else if (exc instanceof RuntimeException)
				throw (RuntimeException) exc;
			else
			    throw (IOException) exc;
		}

		OAuth oauth = new OAuth();
		oauth.setToken(new OAuthToken(token, token_secret));
		// RequestContext.getRequestContext().setOAuth(oauth);
		return oauth.getToken();
	}

	public OAuth getAccessToken(String token, String tokenSecret, String oauthVerifier) throws IOException, FlickrException {
		List<Parameter> parameters = new ArrayList<>();
		parameters.add(new Parameter(OAuthInterface.PARAM_OAUTH_CONSUMER_KEY, apiKey));
		parameters.add(new OAuthTokenParameter(token));
		parameters.add(new Parameter(KEY_OAUTH_VERIFIER, oauthVerifier));
		OAuthUtils.addBasicOAuthParams(parameters);
		// OAuthUtils.signPost(sharedSecret, URL_ACCESS_TOKEN, parameters);

		String signature = OAuthUtils.getSignature(OAuthUtils.REQUEST_METHOD_POST, URL_ACCESS_TOKEN, parameters, sharedSecret, tokenSecret);
		// This method call must be signed.
		parameters.add(new Parameter("oauth_signature", signature));

		// OAuthUtils.addOAuthParams(this.sharedSecret, URL_ACCESS_TOKEN, parameters);

		int retry = 0;
		Throwable exc = null;
		OAuth result = null;
		while (result == null && retry < 5) {
			try {
				exc = null;
				result = null;
				Map<String, String> response = oauthTransport.getMapData(false, PATH_OAUTH_ACCESS_TOKEN, parameters);
				LOG.info("Response: {}", response);
				if (response.isEmpty()) {
					exc = new FlickrException("Empty Response", "Empty Response");
				}
				if (!response.containsKey("oauth_token")) {
					exc = new FlickrException("Error", "Invalid response: " + response);
				}

				if (exc == null) {
					result = new OAuth();
					User user = new User();
					user.setId(response.get("user_nsid"));
					user.setUsername(response.get("username"));
					user.setRealName(response.get("fullname"));
					result.setUser(user);
					result.setToken(new OAuthToken(response.get("oauth_token"), response.get("oauth_token_secret")));
					RequestContext.getRequestContext().setOAuth(result);
				}
			} catch (Exception e) {
				exc = e;
			} finally {
				retry++;
				if (exc != null) {
					try {
						LOG.warn("sleeping a bit, retry={}", retry, exc);
						Thread.sleep(retry * 1000L);
					} catch (InterruptedException e) {
                        LOG.warn("Interrupted waiting for access token", e);
					}
				}
			}
		}

		if (token == null && exc != null) {
			if (exc instanceof FlickrException) {
                throw (FlickrException) exc;
            } else if (exc instanceof RuntimeException) {
                throw (RuntimeException) exc;
            } else {
                throw (IOException) exc;
            }
		}

		return result;
	}

	/**
	 * Build the authentication URL using the given permission and frob.
	 *
	 * The hostname used here is www.flickr.com. It differs from the api-default api.flickr.com.
	 *
	 * @param permission
	 *            The Permission
	 * @return The URL
	 */
	public URL buildAuthenticationUrl(Permission permission, OAuthToken oauthToken) throws MalformedURLException {
		List<Parameter> parameters = new ArrayList<>();
		parameters.add(new Parameter(PARAM_OAUTH_TOKEN, oauthToken.getOauthToken()));
		if (permission != null) {
			parameters.add(new Parameter("perms", permission.toString()));
		}

		int port = oauthTransport.getPort();
		String path = "/services/oauth/authorize";

		return UrlUtilities.buildUrl(Flickr.DEFAULT_API_HOST, port, path, parameters);
	}

}
