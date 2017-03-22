/*
 * Copyright (c) 2005 Aetrion LLC.
 */
package com.googlecode.flickrjandroid;

import com.googlecode.flickrjandroid.oauth.OAuthUtils;
import com.googlecode.flickrjandroid.uploader.ImageParameter;
import com.googlecode.flickrjandroid.uploader.UploaderResponse;
import com.googlecode.flickrjandroid.util.IOUtilities;
import com.googlecode.flickrjandroid.util.StringUtilities;
import com.googlecode.flickrjandroid.util.UrlUtilities;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader2.BuildConfig;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
		InputStream in = null;
		BufferedReader rd = null;
		try {
            in = getInputStream(url);
            rd = new BufferedReader(new InputStreamReader(in, OAuthUtils.ENC));
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
        } finally {
            IOUtilities.close(in);
            IOUtilities.close(rd);
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

	private static class UploadThread {
		private final Media media;
		private final List<Parameter> parameters;
		private final Object[] responseContainer;
        private final URL url;
        private final DocumentBuilder builder;

        private final Thread thread;

		private HttpURLConnection conn = null;
		private DataOutputStream out = null;
		private InputStream in;

		private UploadThread(Media media, URL url, List<Parameter> parameters, Object[] responseContainer)
                throws ParserConfigurationException
        {
			this.media = media;
			this.parameters = parameters;
			this.responseContainer = responseContainer;
            this.url = url;

            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builder = builderFactory.newDocumentBuilder();

            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    UploadThread.this.run();
                }
            });
            thread.setName("Upload <" + media.getName() + ">");
        }

		private boolean killed = false;

        private static void reportProgress(Media media, int progress) {
            media.setProgress(progress);
            UploadService.onUploadProgress(media);
        }

        private static void writeParam(Parameter param, DataOutputStream out, String boundary, Media media) throws IOException {
            String name = param.getName();
            out.writeBytes("\r\n");
            if (param instanceof ImageParameter) {
                ImageParameter imageParam = (ImageParameter) param;
                Object value = param.getValue();
                out.writeBytes(String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n", name, imageParam.getImageName()));
                out.writeBytes(String.format(Locale.US, "Content-Type: image/%s\r\n\r\n", imageParam.getImageType()));
                if (value instanceof File) {
                    File file = (File) value;
                    InputStream in = new FileInputStream(file);
                    try {
                        long start = System.currentTimeMillis();
                        byte[] buf = new byte[512];
                        int res;
                        int bytesRead = 0;
                        int currentProgress = 2;
                        while ((res = in.read(buf)) != -1) {
                            out.write(buf, 0, res);
                            bytesRead += res;

                            int tmpProgress = (int) Math.min(LIMIT, LIMIT * ((double)bytesRead) / file.length());
                            if (currentProgress != tmpProgress) {
                                currentProgress = tmpProgress;
                                reportProgress(media, currentProgress);
                            }
                        }
                        LOG.debug("output in {} ms", System.currentTimeMillis() - start);
                    } finally {
                        in.close();
                    }
                } else if (value instanceof byte[]) {
                    out.write((byte[]) value);
                } else {
                    String valueType = "<null>";
                    if (value != null) {
                        valueType = value.getClass().toString();
                    }
                    LOG.warn("Not writing {} <{}>=<{}>", valueType, param.getName(), value);
                }
            } else {
                out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
                out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
                out.write(((String) param.getValue()).getBytes("UTF-8"));
            }
            out.writeBytes("\r\n");
            out.writeBytes(boundary);
        }

        private void kill() {
			killed = true;
			if (conn != null) {
				try {
					conn.setConnectTimeout(50);
					conn.setReadTimeout(50);
					conn.disconnect();
				} catch (Exception e) {
					LOG.error("Error setting up connection", e);
				}
			} else {
				LOG.warn("HttpURLConnection is null");
			}
			if (out != null) {
				try {
					LOG.warn("closing DataOutputStream");
					out.close();
					LOG.warn("DataOutputStream closed");
				} catch (Exception e) {
					LOG.error("Closing DataOutputStream failed", e);
				}
			} else {
				LOG.warn("DataOutputStream is null");
			}
			if (in != null) {
				try {
					LOG.warn("closing InputStream");
					in.close();
					LOG.warn("InputStream closed");
				} catch (Exception e) {
					LOG.error("Closing InputStream failed", e);
				}
			} else {
				LOG.warn("InputStream is null");
			}

			thread.interrupt();
			LOG.warn("{} is interrupted : {}", this, thread.isInterrupted());
			onFinish();
		}

        private void startSupervisionThread() {
            final String threadName = "Upload supervisor: <" + media + ">";
            Thread supervisionThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    long lastProgressChange = System.currentTimeMillis();
                    int lastProgress = 0;
                    while (thread.isAlive() && !thread.isInterrupted() && media.getProgress() < 999 && System.currentTimeMillis() - lastProgressChange < 2 * 60 * 1000L) {
                        if (media.getProgress() > LIMIT) {
                            reportProgress(media, Math.min(998, media.getProgress() + 1));
                        }
                        if (lastProgress != media.getProgress()) {
                            lastProgress = media.getProgress();
                            lastProgressChange = System.currentTimeMillis();
                        }
                        try {
                            // The whole point of this thread is to wake up from time to time and
                            // report progress or shut down if it's taking too long
                            //
                            //noinspection BusyWait
                            Thread.sleep(Math.max(1000, (media.getProgress() - LIMIT) * 600));
                        } catch (InterruptedException e) {
                            LOG.warn("Thread interrupted: <{}>", threadName, e);
                        }
                    }
                    if (media.getProgress() < 999 && System.currentTimeMillis() - lastProgressChange >= 2 * 60 * 1000L) {
                        LOG.warn("Upload is taking too long, started {} ago",
                                ToolString.formatDuration(System.currentTimeMillis() - media.getTimestampUploadStarted()));

                        kill();
                    }
                }
            });
            supervisionThread.setName(threadName);
            supervisionThread.start();
        }

        private void run() {
            startSupervisionThread();
            reportProgress(media, 0);
			try {
				if (BuildConfig.DEBUG) {
					LOG.debug("Post URL: {}", url);
				}
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");

				String boundary = "---------------------------7d273f7a0d3";
				conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
				conn.setRequestProperty("Host", "api.flickr.com");
				conn.setDoInput(true);
				conn.setDoOutput(true);

				boundary = "--" + boundary;

				int contentLength = 0;
				contentLength += boundary.getBytes("UTF-8").length;
				for (Parameter parameter : parameters) {
					contentLength += "\r\n".getBytes("UTF-8").length;
					if (parameter.getValue() instanceof String) {
						contentLength += ("Content-Disposition: form-data; name=\"" + parameter.getName() + "\"\r\n").getBytes("UTF-8").length;
						contentLength += ("Content-Type: text/plain; charset=UTF-8\r\n\r\n").getBytes("UTF-8").length;
						contentLength += ((String) parameter.getValue()).getBytes("UTF-8").length;
					} else if (parameter instanceof ImageParameter && parameter.getValue() instanceof File) {
						ImageParameter imageParam = (ImageParameter) parameter;
						File file = (File) parameter.getValue();
						contentLength += String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n", parameter.getName(), imageParam.getImageName())
								.getBytes("UTF-8").length;
						contentLength += String.format(Locale.US, "Content-Type: image/%s\r\n\r\n", imageParam.getImageType()).getBytes("UTF-8").length;

						LOG.debug("set to upload {} : {} bytes", file, file.length());
						contentLength += file.length();
						break;
					}
					contentLength += "\r\n".getBytes("UTF-8").length;
					contentLength += boundary.getBytes("UTF-8").length;
				}
				contentLength += "--\r\n\r\n".getBytes("UTF-8").length;

				contentLength += 213;// dirty hack to account for missing param somewhere
				LOG.debug("contentLength : {}", contentLength);

				conn.setRequestProperty("Content-Length", "" + contentLength);
				conn.setFixedLengthStreamingMode(contentLength);

				conn.connect();
				reportProgress(media, 1);
				out = new DataOutputStream(conn.getOutputStream());
				out.writeBytes(boundary);
				reportProgress(media, 2);

				for (Parameter parameter : parameters) {
					writeParam(parameter, out, boundary, media);
				}

				out.writeBytes("--\r\n\r\n");
				out.flush();

				LOG.debug("out.size() : {}", out.size());

				out.close();

				reportProgress(media, LIMIT + 1);
				int responseCode = -1;
				try {
					responseCode = conn.getResponseCode();
				} catch (IOException e) {
					LOG.error("Failed to get the POST response code", e);
					if (conn.getErrorStream() != null) {
						responseCode = conn.getResponseCode();
					}
					responseContainer[0] = e;
				} finally {
					reportProgress(media, 999);
				}
				if (responseCode < 0) {
					LOG.error("some error occured : {}", responseCode);
				} else if ((responseCode != HttpURLConnection.HTTP_OK)) {
					String errorMessage = readFromStream(conn.getErrorStream());
					String detailMessage = "Connection Failed. Response Code: " + responseCode + ", Response Message: " + conn.getResponseMessage() + ", Error: " + errorMessage;
					LOG.error("detailMessage : {}", detailMessage);
					throw new IOException(detailMessage);
				}
				if (killed) {
					LOG.warn("thread was killed");
					if (responseContainer[0] == null) {
						responseContainer[0] = new UploadService.UploadException("upload cancelled by user", false);
					}
				} else {
					UploaderResponse response = new UploaderResponse();
					in = conn.getInputStream();
					Document document = builder.parse(in);
					response.parse(document);
					responseContainer[0] = response;
				}
			} catch (Exception t) {
				responseContainer[0] = t;
			} finally {
				try {
					reportProgress(media, 1000);
					IOUtilities.close(out);
					if (conn != null)
						conn.disconnect();
				} catch (Exception e) {
					LOG.error("Finishing off upload thread {} failed", this, e);
				}
				onFinish();
			}
		}

        private void onFinish() {
			try {
				LOG.debug("finishing thread : {}", responseContainer[0]);
				uploadThreads.remove(media);
				synchronized (responseContainer) {
                    // Waited for in REST.sendUpload()

                    //noinspection NotifyWithoutCorrespondingWait,NakedNotify
                    responseContainer.notifyAll();
				}
			} catch (Exception e) {
				LOG.error("Error wrapping up upload", e);
			}
		}

        public void start() {
            thread.start();
        }
    }

	/*
	 * (non-Javadoc)
	 *
	 * @see com.gmail.yuyang226.flickr.Transport#sendUpload(java.lang.String, java.util.List)
	 */
	public Response sendUpload(final String path, final List<Parameter> parameters, final Media media) throws IOException, FlickrException, SAXException {
		if (BuildConfig.DEBUG) {
			LOG.debug("Send Upload Input Params: path '{}'; parameters {}", path, parameters);
		}

		final Object[] responseContainer = new Object[1];

        URL url = UrlUtilities.buildPostUrl(getHost(), getPort(), path);
        UploadThread uploadThread;
        try {
            uploadThread = new UploadThread(media, url, parameters, responseContainer);
        } catch (ParserConfigurationException e) {
            throw new IOException("Error creating upload thread", e);
        }
        uploadThreads.put(media, uploadThread);
		uploadThread.start();

		synchronized (responseContainer) {
			try {
				responseContainer.wait();
			} catch (InterruptedException e) {
                LOG.warn("Interrupted1 waiting for responseContainer", e);
			}
		}

		if (responseContainer[0] == null) {
			LOG.debug("response is null, waiting a bit more in case of thread interruption");
			synchronized (responseContainer) {
				try {
					responseContainer.wait(1000);
				} catch (InterruptedException e) {
                    LOG.warn("Interrupted2 waiting for responseContainer", e);
				}
			}
		}

        LOG.debug("response : {}", responseContainer[0]);

		if (responseContainer[0] instanceof Response) {
			return (Response) responseContainer[0];
		} else if (responseContainer[0] instanceof IOException) {
			throw (IOException) responseContainer[0];
		} else if (responseContainer[0] instanceof FlickrException) {
			throw (FlickrException) responseContainer[0];
		} else if (responseContainer[0] instanceof SAXException) {
			throw (SAXException) responseContainer[0];
		} else if (responseContainer[0] instanceof Throwable) {
			Throwable throwable = (Throwable) responseContainer[0];
			throw new UploadService.UploadException(throwable.getMessage(), throwable);
		}
		return null;

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
		DataOutputStream out = null;
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
            out = new DataOutputStream(conn.getOutputStream());
            out.write(bytes);
            out.flush();
            out.close();

            int responseCode = HttpURLConnection.HTTP_OK;
            try {
                responseCode = conn.getResponseCode();
            } catch (IOException e) {
                LOG.error("Failed to get the POST response code", e);
                if (conn.getErrorStream() != null) {
                    responseCode = conn.getResponseCode();
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
            IOUtilities.close(out);
            if (conn != null)
                conn.disconnect();
            if (BuildConfig.DEBUG) {
                LOG.info("Send Post Result: {}", data);
            }
        }
	}

	private static String readFromStream(InputStream input) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(input));
			StringBuilder buffer = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
			}
			return buffer.toString();
		} finally {
			IOUtilities.close(input);
			IOUtilities.close(reader);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.gmail.yuyang226.flickr.Transport#post(java.lang.String, java.util.List, boolean)
	 */
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

	private static final int LIMIT = 970;
}
