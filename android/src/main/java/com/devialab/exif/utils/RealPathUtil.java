package com.devialab.exif.utils;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/*
 * Taken from react-native-image-picker
 */

public class RealPathUtil {
    public static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	public static @Nullable Uri compatUriFromFile(@NonNull final Context context,
												  @NonNull final File file) {
		Uri result = null;
		if (Build.VERSION.SDK_INT < 21) {
			result = Uri.fromFile(file);
		}
		else {
			final String packageName = context.getApplicationContext().getPackageName();
			final String authority =  new StringBuilder(packageName).append(".provider").toString();
			try {
				result = FileProvider.getUriForFile(context, authority, file);
			}
			catch(IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	@SuppressLint("NewApi")
	public static @Nullable String getRealPathFromURI(@NonNull final Context context,
													  @NonNull final Uri uri) {

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			if (isFileProviderUri(context, uri))
				return getFileProviderPath(context, uri);

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	                                   String[] selectionArgs) {
		return getFilePathFromURI(context, uri);
	}


	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(@NonNull final Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	/**
	 * @param context The Application context
	 * @param uri The Uri is checked by functions
	 * @return Whether the Uri authority is FileProvider
	 */
	public static boolean isFileProviderUri(@NonNull final Context context,
	                                        @NonNull final Uri uri) {
		final String packageName = context.getPackageName();
		final String authority = new StringBuilder(packageName).append(".provider").toString();
		return authority.equals(uri.getAuthority());
	}

	/**
	 * @param context The Application context
	 * @param uri The Uri is checked by functions
	 * @return File path or null if file is missing
	 */
	public static @Nullable String getFileProviderPath(@NonNull final Context context,
	                                                   @NonNull final Uri uri)
	{
		final File appDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		final File file = new File(appDir, uri.getLastPathSegment());
		return file.exists() ? file.toString(): null;
	}
	public static String getFilePathFromURI(Context context, Uri contentUri) {
		//copy file and send new file path
		String fileName = getFileName(contentUri);
		if (!TextUtils.isEmpty(fileName)) {
            File rootDataDir = context.getFilesDir();
			File copyFile = new File(rootDataDir + File.separator + fileName);
			copy(context, contentUri, copyFile);
			return copyFile.getAbsolutePath();
		}
		return null;
	}

	public static String getFileName(Uri uri) {
		if (uri == null) return null;
		String fileName = null;
		String path = uri.getPath();
		int cut = path.lastIndexOf('/');
		if (cut != -1) {
			fileName = path.substring(cut + 1);
		}
		return fileName;
	}

	public static void copy(Context context, Uri srcUri, File dstFile) {
		try {
			InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
			if (inputStream == null) return;
			OutputStream outputStream = new FileOutputStream(dstFile);
            copyLarge(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);
			inputStream.close();
			outputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    static void copyLarge(final InputStream input, final OutputStream output, final byte[] buffer)
            throws IOException {
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
    }
}
