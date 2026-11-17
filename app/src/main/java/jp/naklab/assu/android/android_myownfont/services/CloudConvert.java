package jp.naklab.assu.android.android_myownfont.services;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CloudConvert {
    final String TAG = "CloudConvert";
    private String outputFileName = "";
    private String targetFormat = "ttf";
    private OnConvertFinishListener listener;

    public void setListener(OnConvertFinishListener listener) {
        this.listener = listener;
    }

    public void convert(String fontSvgfilePath, String fontName) {
        convert(fontSvgfilePath, fontName, "ttf", APIUtil.string);
    }

    public void convert(String fontSvgfilePath, String fontName, String format, String apiKey) {
        this.targetFormat = (format != null && format.equalsIgnoreCase("otf")) ? "otf" : "ttf";
        CloudConvertService service = CloudConvertServiceGenerator.createService(CloudConvertService.class);

        outputFileName = fontName.replaceAll("\\s+", "");

        // POST SVG file
        File file = new File(fontSvgfilePath);

        if (!file.exists()) {
            Log.d(TAG, "not exists " + file.getPath());
            if (listener != null) listener.onConvertFailure("File does not exist: " + file.getPath());
            return;
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        Call<ResponseBody> call = service.upload(targetFormat, apiKey != null ? apiKey : APIUtil.string, body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Response failed: " + response.code() + " " + response.message());
                    if (listener != null) listener.onConvertFailure("HTTP " + response.code() + ": " + response.message());
                    return;
                }
                DownloadFileAsyncTask downloadFileAsyncTask = new DownloadFileAsyncTask();
                downloadFileAsyncTask.execute(response.body().byteStream());
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Request failed", t);
                if (listener != null) listener.onConvertFailure(t.getMessage());
            }
        });
    }

    private class DownloadFileAsyncTask extends AsyncTask<InputStream, Void, String> {
        final String appDirectoryName = "fonts";
        final File imageRoot = new File(Environment.getExternalStorageDirectory().getPath(), appDirectoryName);
        final String filename = outputFileName + "." + targetFormat;

        @Override
        protected String doInBackground(InputStream... params) {
            InputStream inputStream = params[0];
            if (!imageRoot.exists()) {
                imageRoot.mkdirs();
            }
            File file = new File(imageRoot, filename);
            OutputStream output = null;
            try {
                output = new FileOutputStream(file);

                byte[] buffer = new byte[1024];
                int read;

                Log.d(TAG, "Writing to: " + file.getAbsolutePath());
                while ((read = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                return file.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, "IO Exception: " + e.getMessage(), e);
                return null;
            } finally {
                try {
                    if (output != null) output.close();
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing stream", e);
                }
            }
        }

        @Override
        protected void onPostExecute(String resultPath) {
            super.onPostExecute(resultPath);
            if (resultPath != null) {
                Log.d(TAG, "Download success: " + resultPath);
                if (listener != null) listener.onConvertComplete(resultPath, targetFormat);
            } else {
                if (listener != null) listener.onConvertFailure("Failed to write font file.");
            }
        }
    }

    public interface OnConvertFinishListener {
        void onConvertComplete(String filePath, String format);
        void onConvertFailure(String error);
    }
}
