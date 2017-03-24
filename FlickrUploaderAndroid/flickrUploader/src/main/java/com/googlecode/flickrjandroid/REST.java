/*
 * Copyright (c) 2005 Aetrion LLC.
 */
package com.googlecode.flickrjandroid;

import com.googlecode.flickrjandroid.oauth.OAuthUtils;
import com.googlecode.flickrjandroid.util.IOUtilities;
import com.googlecode.flickrjandroid.util.StringUtilities;
import com.googlecode.flickrjandroid.util.UrlUtilities;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader2.BuildConfig;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Transport implementation using the REST interface.
 *
 * @author Anthony Eden
 * @version $Id: REST.java,v 1.26 2009/07/01 22:07:08 x-mago Exp $
 */
public class REST extends Transport {
	private static final Logger LOG = LoggerFactory.getLogger(REST.class);

	private static final String UTF8 = "UTF-8";
	private static final String PATH = "/services/rest/";

	/**
	 * Construct a new REST transport instance.
	 */
    private REST() throws ParserConfigurationException {
		setTransportType(REST);
		setHost(Flickr.DEFAULT_API_HOST);
		setPath(PATH);
		setResponseClass(RESTResponse.class);
    }

	/**
	 * Construct a new REST transport instance using the specified host endpoint.
	 *
	 * @param host The host endpoint
	 */
	public REST(String host) throws ParserConfigurationException {
		this();
		setHost(host);
	}

	/**
	 * Invoke an HTTP GET request on a remote host. You must close the InputStream after you are done with.
	 *
	 * @param path
	 *            The request path
	 * @param parameters
	 *            The parameters (collection of Parameter objects)
	 * @return The Response
	 */
	@Override
	public Response get(String path, List<Parameter> parameters) throws IOException, JSONException {
		parameters.add(new Parameter("nojsoncallback", "1"));
		parameters.add(new Parameter("format", "json"));
		String data = getLine(path, parameters);
		return new RESTResponse(data, parameters.toString());
	}

	private InputStream getInputStream(URL url) throws IOException {
		if (BuildConfig.DEBUG) {
			LOG.info("GET URL: {}", url);
		}
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (url.toString().contains("method=flickr.test.echo")) {
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
		}
		conn.addRequestProperty("Cache-Control", "no-cache,max-age=0");
		conn.addRequestProperty("Pragma", "no-cache");
		conn.setRequestMethod("GET");
        conn.connect();
		if (BuildConfig.DEBUG) {
            LOG.info("response code : {}", conn.getResponseCode());
		}
		return conn.getInputStream();
	}

	/**
	 * Send a GET request to the provided URL with the given parameters, then return the response as a String.
	 *
	 * @return the data in String
	 */
    private String getLine(String path, List<Parameter> parameters) throws IOException {
		URL url = UrlUtilities.buildUrl(getHost(), getPort(), path, parameters);
        LOG.info("url : {}", url);
		try (InputStream in = getInputStream(url);
             BufferedReader rd = new BufferedReader(new InputStreamReader(in, OAuthUtils.ENC)))
        {
            final StringBuilder buf = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                buf.append(line);
            }

            if (BuildConfig.DEBUG) {
                LOG.info("response : {}", buf);
            }
            return buf.toString();
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw e;
        }
	}

	/**
	 * <p>
	 * A helper method for sending a GET request to the provided URL with the given parameters, then return the response as a Map.
	 * </p>
	 *
	 * <p>
	 * Please make sure the response data is a Map before calling this method.
	 * </p>
	 *
	 * @return the data in Map with key value pairs
	 */
	public Map<String, String> getMapData(boolean getRequestMethod, String path, List<Parameter> parameters) throws IOException {
		String data = getRequestMethod ? getLine(path, parameters) : sendPost(path, parameters);
		return getDataAsMap(URLDecoder.decode(data, OAuthUtils.ENC));
	}

	private Map<String, String> getDataAsMap(String data) {
		Map<String, String> result = new HashMap<>();
		if (data != null) {
			for (String string : StringUtilities.split(data, "&")) {
				String[] values = StringUtilities.split(string, "=");
				if (values.length == 2) {
					result.put(values[0], values[1]);
				}
			}
		}
		return result;
	}

	@Override
	protected Response sendUpload(String path, List<Parameter> parameters) throws IOException, FlickrException, SAXException {
		return sendUpload(path, parameters, null);
	}

	private static Map<Media, UploadThread> uploadThreads = new ConcurrentHashMap<>();

	public static void kill(Media media) {
		try {
			UploadThread uploadThread = uploadThreads.get(media);
            LOG.warn("killing {}, uploadThread={}", media, uploadThread);
			if (uploadThread != null) {
				uploadThread.kill();
			}
		} catch (Exception e) {
			LOG.error("Error killing media upload", e);
		}
	}

	public Response sendUpload(final String path, final List<Parameter> parameters, final Media media) throws IOException, FlickrException, SAXException {
		if (BuildConfig.DEBUG) {
			LOG.debug("Send Upload Input Params: path '{}'; parameters {}", path, parameters);
		}

        URL url = UrlUtilities.buildPostUrl(getHost(), getPort(), path);
        UploadThread uploadThread;
        try {
            uploadThread = new UploadThread(media, url, parameters);
        } catch (ParserConfigurationException e) {
            throw new IOException("Error creating upload thread", e);
        }
        uploadThreads.put(media, uploadThread);

        return uploadThread.doUpload();
    }

	private String sendPost(String path, List<Parameter> parameters) throws IOException {
		String method = null;
		int timeout = 0;
		for (Parameter parameter : parameters) {
            if (parameter.getName().equalsIgnoreCase("method")) {
                method = (String) parameter.getValue();
                if (method.equals("flickr.test.echo")) {
                    timeout = 5000;
                }
            } else if (parameter.getName().equalsIgnoreCase("machine_tags") && ((String) parameter.getValue()).contains("file:md5sum")) {
                timeout = 10000;
            }
        }
		if (BuildConfig.DEBUG) {
            LOG.debug("API {}, timeout={}", method, timeout);
            LOG.trace("Send Post Input Params: path '{}'; parameters {}", path, parameters);
        }
		HttpURLConnection conn = null;
		String data = null;
		try {
            URL url = UrlUtilities.buildPostUrl(getHost(), getPort(), path);
            if (BuildConfig.DEBUG) {
                LOG.info("Post URL: {}", url);
            }
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String postParam = encodeParameters(parameters);
            byte[] bytes = postParam.getBytes(UTF8);
            conn.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.addRequestProperty("Cache-Control", "no-cache,max-age=0");
            conn.addRequestProperty("Pragma", "no-cache");
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            if (timeout > 0) {
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
            }
            conn.connect();
            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.write(bytes);
            }

            int responseCode = HttpURLConnection.HTTP_OK;
            try {
                responseCode = conn.getResponseCode();
            } catch (IOException e) {
                LOG.error("Failed to get the POST response code", e);
                try (InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        responseCode = conn.getResponseCode();
                    }
                }
            }
            if ((responseCode != HttpURLConnection.HTTP_OK)) {
                String errorMessage = readFromStream(conn.getErrorStream());
                throw new IOException("Connection Failed. Response Code: " + responseCode + ", Response Message: " + conn.getResponseMessage() + ", Error: " + errorMessage);
            }

            String result = readFromStream(conn.getInputStream());
            data = result.trim();
            return data;
        } finally {
            if (conn != null)
                conn.disconnect();
            if (BuildConfig.DEBUG) {
                LOG.info("Send Post Result: {}", data);
            }
        }
	}

	public static String readFromStream(InputStream input) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			StringBuilder buffer = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
			}
			return buffer.toString();
		} finally {
			IOUtilities.close(input);
		}
	}

	@Override
	public Response post(String path, List<Parameter> parameters) throws IOException, JSONException {
		String data = sendPost(path, parameters);
		return new RESTResponse(data, parameters.toString());
	}

    private static String encodeParameters(List<Parameter> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return "";
		}
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < parameters.size(); i++) {
			if (i != 0) {
				buf.append("&");
			}
			Parameter param = parameters.get(i);
			buf.append(UrlUtilities.encode(param.getName())).append("=").append(UrlUtilities.encode(String.valueOf(param.getValue())));
		}
		return buf.toString();
	}
}
