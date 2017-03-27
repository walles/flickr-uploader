package com.googlecode.flickrjandroid;

import android.support.annotation.Nullable;

import com.googlecode.flickrjandroid.uploader.ImageParameter;
import com.googlecode.flickrjandroid.uploader.UploaderResponse;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader2.BuildConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

class UploadThread {
    private static final Logger LOG = LoggerFactory.getLogger(UploadThread.class);
    private static final int LIMIT = 970;

    private final Media media;
    private final List<Parameter> parameters;
    private Object response;
    private final URL url;
    private final DocumentBuilder builder;

    private final Thread thread;

    private HttpURLConnection conn = null;
    private InputStream in;

    private final Object lock = new Object();

    @Nullable
    private Exception killedWithException = null;
    private long uploadStartMs;

    UploadThread(Media media, URL url, List<Parameter> parameters) throws ParserConfigurationException {
        this.media = media;
        this.parameters = parameters;
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

    private static void reportProgress(Media media, int progress) {
        media.setProgress(progress);
        UploadService.onUploadProgress(media);
    }

    private static void writeParam(Parameter param, DataOutputStream out, String boundary,
            Media media) throws IOException {
        String name = param.getName();
        out.writeBytes("\r\n");
        if (param instanceof ImageParameter) {
            ImageParameter imageParam = (ImageParameter) param;
            Object value = param.getValue();
            out.writeBytes(String.format(Locale.US,
                    "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n", name,
                    imageParam.getImageName()));
            out.writeBytes(String.format(Locale.US, "Content-Type: image/%s\r\n\r\n",
                    imageParam.getImageType()));
            if (value instanceof File) {
                File file = (File) value;
                try (InputStream in = new FileInputStream(file)) {
                    long start = System.currentTimeMillis();
                    byte[] buf = new byte[512];
                    int res;
                    int bytesRead = 0;
                    int currentProgress = 2;
                    while ((res = in.read(buf)) != -1) {
                        out.write(buf, 0, res);
                        bytesRead += res;

                        int tmpProgress = (int) Math.min(LIMIT,
                                LIMIT * ((double) bytesRead) / file.length());
                        if (currentProgress != tmpProgress) {
                            currentProgress = tmpProgress;
                            reportProgress(media, currentProgress);
                        }
                    }
                    LOG.debug("output in {} ms", System.currentTimeMillis() - start);
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

    public void kill(boolean isTimeout) {
        synchronized (lock) {
            if (isTimeout) {
                long dt = System.currentTimeMillis() - uploadStartMs;
                killedWithException = new TimeoutException("Upload timed out after " + dt + "ms");
            } else {
                killedWithException = new InterruptedException("Upload killed");
            }
        }

        if (conn != null) {
            try {
                conn.setConnectTimeout(50);
                conn.setReadTimeout(50);
                conn.disconnect();
            } catch (Exception e) {
                LOG.error("Error killing connection", e);
            }
        } else {
            LOG.warn("HttpURLConnection is null");
        }
        if (in != null) {
            try {
                in.close();
            } catch (Exception e) {
                LOG.error("Closing InputStream failed", e);
            }
        } else {
            LOG.warn("InputStream is null");
        }

        thread.interrupt();
        LOG.warn("{} is interrupted : {}", this, thread.isInterrupted());
    }

    private void startSupervisionThread() {
        final String threadName = "Upload supervisor: <" + media + ">";
        Thread supervisionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long lastProgressChange = System.currentTimeMillis();
                int lastProgress = 0;
                while (thread.isAlive() && !thread.isInterrupted() && media.getProgress() < 999
                        && System.currentTimeMillis() - lastProgressChange < 2 * 60 * 1000L) {
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
                if (media.getProgress() < 999
                        && System.currentTimeMillis() - lastProgressChange >= 2 * 60 * 1000L) {
                    LOG.warn("Upload is taking too long, started {} ago",
                            ToolString.formatDuration(System.currentTimeMillis()
                                    - media.getTimestampUploadStarted()));

                    kill(true);
                }
            }
        });
        supervisionThread.setName(threadName);
        supervisionThread.start();
    }

    private void setResponse(Object response) {
        synchronized (lock) {
            if (this.response != null) {
                //noinspection AccessToStaticFieldLockedOnInstance
                LOG.warn("Upload response set multiple times",
                        new RuntimeException("Upload response already set to <" + response
                                + ">, not resetting"));
            }

            this.response = response;
        }
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
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Host", "api.flickr.com");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            boundary = "--" + boundary;

            int contentLength = 0;
            contentLength += boundary.getBytes("UTF-8").length;
            for (Parameter parameter : parameters) {
                contentLength += "\r\n".getBytes("UTF-8").length;
                if (parameter.getValue() instanceof String) {
                    contentLength +=
                            ("Content-Disposition: form-data; name=\"" + parameter.getName()
                                    + "\"\r\n").getBytes("UTF-8").length;
                    contentLength +=
                            ("Content-Type: text/plain; charset=UTF-8\r\n\r\n").getBytes(
                                    "UTF-8").length;
                    contentLength += ((String) parameter.getValue()).getBytes("UTF-8").length;
                } else if (parameter instanceof ImageParameter
                        && parameter.getValue() instanceof File) {
                    ImageParameter imageParam = (ImageParameter) parameter;
                    File file = (File) parameter.getValue();
                    contentLength += String.format(Locale.US,
                            "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n",
                            parameter.getName(), imageParam.getImageName())
                            .getBytes("UTF-8").length;
                    contentLength += String.format(Locale.US, "Content-Type: image/%s\r\n\r\n",
                            imageParam.getImageType()).getBytes("UTF-8").length;

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
            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.writeBytes(boundary);
                reportProgress(media, 2);

                for (Parameter parameter : parameters) {
                    writeParam(parameter, out, boundary, media);
                }

                out.writeBytes("--\r\n\r\n");
            }

            reportProgress(media, LIMIT + 1);
            int responseCode = conn.getResponseCode();
            if (responseCode < 0) {
                throw new IOException("Upload error: " + responseCode);
            } else if ((responseCode != HttpURLConnection.HTTP_OK)) {
                String errorMessage = REST.readFromStream(conn.getErrorStream());
                String detailMessage = "Connection Failed. Response Code: " + responseCode
                        + ", Response Message: " + conn.getResponseMessage() + ", Error: "
                        + errorMessage;
                throw new IOException(detailMessage);
            }

            synchronized (lock) {
                if (killedWithException != null) {
                    throw new UploadService.UploadException("upload canceled by user", false);
                }
            }

            UploaderResponse response = new UploaderResponse();
            in = conn.getInputStream();
            Document document = builder.parse(in);
            response.parse(document);
            setResponse(response);
        } catch (Exception e) {
            setResponse(e);
        } finally {
            try {
                reportProgress(media, 1000);
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) {
                LOG.error("Finishing off upload thread {} failed", this, e);
            }
        }
    }

    public Response doUpload() throws IOException, FlickrException, SAXException
    {
        synchronized (lock) {
            uploadStartMs = System.currentTimeMillis();
        }
        thread.start();

        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new IOException("Interrupted waiting for upload to finish", e);
        }

        synchronized (lock) {
            if (killedWithException != null) {
                if (response == null) {
                    response = killedWithException;
                } else if (!(response instanceof Throwable)) {
                    // We have some kind of response for the killed thread, just overwrite it
                    response = killedWithException;
                } else {
                    // Response is some kind of exception, add the kill reason as the ultimate cause
                    Throwable throwable = (Throwable) response;
                    while (throwable.getCause() != null) {
                        throwable = throwable.getCause();
                    }
                    throwable.initCause(killedWithException);
                }
            }

            //noinspection AccessToStaticFieldLockedOnInstance
            LOG.debug("response : {}", response);

            if (response instanceof Response) {
                return (Response) response;
            } else if (response instanceof IOException) {
                throw (IOException) response;
            } else if (response instanceof FlickrException) {
                throw (FlickrException) response;
            } else if (response instanceof SAXException) {
                throw (SAXException) response;
            } else if (response instanceof Throwable) {
                Throwable throwable = (Throwable) response;
                throw new UploadService.UploadException(throwable.getMessage(), throwable);
            }
            return null;
        }
    }
}
