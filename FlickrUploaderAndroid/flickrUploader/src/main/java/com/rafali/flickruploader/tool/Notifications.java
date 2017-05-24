package com.rafali.flickruploader.tool;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.service.UploadService.BasicUploadProgressListener;
import com.rafali.flickruploader.service.UploadService.UploadProgressListener;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity_;
import com.rafali.flickruploader2.R;

import org.androidannotations.api.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

public class Notifications {

	private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);

	private static final NotificationManager manager =
			(NotificationManager) FlickrUploader.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	private static PendingIntent resultPendingIntent;

	private static Notification.Builder builderUploading;
	private static Notification.Builder builderUploaded;

	private static UploadProgressListener uploadProgressListener = new BasicUploadProgressListener() {
		@Override
		public void onProgress(Media media) {
			Notifications.notifyProgress(media);
		}

		@Override
		public void onFinished(int nbUploaded, int nbError) {
			Notifications.notifyFinished(nbUploaded, nbError);
		}
	};

	public static void init() {
		UploadService.register(uploadProgressListener);
	}

	private static void ensureBuilders() {
		if (resultPendingIntent == null) {
			Intent resultIntent = new Intent(FlickrUploader.getAppContext(), FlickrUploaderActivity_.class);
			resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			resultIntent.setAction(Intent.ACTION_MAIN);
			resultPendingIntent = PendingIntent.getActivity(FlickrUploader.getAppContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		if (builderUploading == null) {
			builderUploading = new Notification.Builder(FlickrUploader.getAppContext());
			builderUploading.setContentIntent(resultPendingIntent);
			builderUploading.setContentTitle("Uploading to Flickr");
			builderUploading.setPriority(Notification.PRIORITY_MIN);
			builderUploading.setSmallIcon(R.drawable.upload);

			builderUploaded = new Notification.Builder(FlickrUploader.getAppContext());
			builderUploaded.setSmallIcon(R.drawable.upload);
			builderUploaded.setPriority(Notification.PRIORITY_MIN);
			builderUploaded.setContentIntent(resultPendingIntent);
			// builderUploaded.setProgress(1000, 1000, false);
			builderUploaded.setTicker("Upload finished");
			builderUploaded.setContentTitle("Upload finished");
			builderUploaded.setAutoCancel(true);

		}
	}

	private static long lastUpdate = 0;

	private static void notifyProgress(final Media media) {
		try {
			if (!Utils.getBooleanProperty("notification_progress", true)) {
				return;
			}

			if (System.currentTimeMillis() - lastUpdate < 1000) {
				return;
			}

			lastUpdate = System.currentTimeMillis();

			int currentPosition = UploadService.getRecentlyUploadedSize();
			int total = UploadService.getNbTotal();
			int progress = media.getProgress();

			ensureBuilders();

			Notification.Builder builder = builderUploading;
			builder.setProgress(1000, progress, false);
			builder.setContentText(media.getName());
			builder.setContentInfo(currentPosition + " / " + total);
			builder.setSmallIcon(android.R.drawable.stat_sys_upload);

			CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
			if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap bitmap = Utils.getBitmap(media, VIEW_SIZE.small);
						if (bitmap != null) {
							Utils.getCache().put(media.getPath() + "_" + VIEW_SIZE.small, bitmap);
						}
					}
				});
			} else {
				builder.setLargeIcon(bitmapDrawable.getBitmap());
			}

			Notification notification = builder.build();
			// notification.iconLevel = progress / 10;
			manager.notify(0, notification);
		} catch (Exception e) {
			LOG.error("FIXME: Log message missing", e);
		}

	}

	private static void notifyFinished(int nbUploaded, int nbError) {
		try {
			manager.cancelAll();
			FlickrUploaderActivity uploaderActivity = FlickrUploaderActivity.getInstance();
			if (uploaderActivity == null || uploaderActivity.isPaused()) {

				if (!Utils.getBooleanProperty("notification_finished", true)) {
					return;
				}

				ensureBuilders();

				Notification.Builder builder = builderUploaded;
				String text = nbUploaded + " media recently uploaded to Flickr";
				if (nbError > 0) {
					text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
				}
				builder.setContentText(text);
				builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);

				manager.notify(0, builder.build());
			}
		} catch (Exception e) {
			LOG.error("Failed to handle upload-finished notification", e);
		}
	}

	public static void clear() {
		manager.cancelAll();
	}
}
