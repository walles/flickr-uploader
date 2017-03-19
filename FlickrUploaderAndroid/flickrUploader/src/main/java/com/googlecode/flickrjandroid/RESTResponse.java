/*
 * Copyright (c) 2005 Aetrion LLC.
 */
package com.googlecode.flickrjandroid;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flickr Response object.
 *
 * @author Anthony Eden
 */
public class RESTResponse implements Response {

	private static final Logger LOG = LoggerFactory.getLogger(RESTResponse.class);

	private JSONObject jsonObj;
	private String rawResponse;
	private String errorCode;
	private String errorMessage;

	public RESTResponse(String rawResponse, String requestDescription) throws JSONException {
		super();

		try {
			this.rawResponse = rawResponse;
			this.jsonObj = new JSONObject(rawResponse);
			String stat = this.jsonObj.getString("stat");
			if ("fail".equals(stat)) {
				this.errorCode = this.jsonObj.getString("code");
				this.errorMessage = this.jsonObj.getString("message") + " for request " + requestDescription;
			} else if (!"ok".equals(stat)) {
				throw new IllegalArgumentException("Unhandled response type <" + stat + ">");
			}
		} catch (JSONException e) {
			LOG.error("JSONException on <{}> for <{}>", rawResponse, requestDescription);
			throw e;
		}
	}

	@Override
	public void parse(String rawMessage) throws JSONException {
		throw new UnsupportedOperationException();
	}

	@Override
	public JSONObject getData() {
		return this.jsonObj;
	}

	@Override
	public boolean isError() {
		return errorCode != null;
	}

	@Override
	public String getErrorCode() {
		return errorCode;
	}

	@Override
	public String getErrorMessage() {
		return errorMessage;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.googlecode.flickrjandroid.Response#getRawResponse()
	 */
	@Override
	public String getRawResponse() {
		return rawResponse;
	}

}
