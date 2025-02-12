package org.yae.qr;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProverkacheckaClient {
    public interface SuccessCallback {
        public void onSuccess(String resp);
    }
    public interface FailureCallback{
        public void onFailure(Exception ex);
    }
    protected String token = "";
    protected final OkHttpClient client =  new OkHttpClient();
    public ProverkacheckaClient(String token) {
        this.token = token;
    }

    public void getByQRRawAsync(String qrraw, SuccessCallback onSuccess, FailureCallback onFailure) {
        var reqBody = new FormBody.Builder()
                .add("qrraw", qrraw)
                .add("token", token)
                .build();

        var request = new Request.Builder()
                .url("https://proverkacheka.com/api/v1/check/get")
                .method("POST", reqBody)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if(onFailure !=null) {
                    onFailure.onFailure(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try(var body = response.body()) {
                    onSuccess.onSuccess(body.string());
                }
            }
        });
    }

}
