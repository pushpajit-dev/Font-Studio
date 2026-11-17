package jp.naklab.assu.android.android_myownfont.services;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface CloudConvertService {
    @Multipart
    @POST("v1/convert?input=upload&inputformat=svg&wait=true&download=inline")
    Call<ResponseBody> upload(
            @Query("outputformat") String outputFormat,
            @Query("apikey") String apiKey,
            @Part MultipartBody.Part file
    );
}
