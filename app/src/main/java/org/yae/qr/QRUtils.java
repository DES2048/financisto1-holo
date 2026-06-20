package org.yae.qr;

import android.content.Context;
import android.widget.Toast;

import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Transaction;
import tw.tib.financisto.model.TransactionStatus;
import tw.tib.financisto.utils.MyPreferences;

interface Callback {
    void onCallback();
}

interface OnResultCallback<T> {
    void onResult(T result);
}

public class QRUtils {
    public interface SuccessScanCallback {
        void onSuccess();
    }

    public interface OnFailureCallback {
        void onFailure(Exception e);
    }
    private static final SimpleDateFormat formatter=
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private static final GmsBarcodeScannerOptions scannerOpts = new GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()
                .allowManualInput()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
    public static boolean isReceiptString(String qrraw) {
        return true;
    }

    public static void checkBarcodeModuleInstalled(Context context, GmsBarcodeScanner scanner, OnResultCallback<Boolean> onResult) {
        var moduleInstall = ModuleInstall.getClient(context);
        moduleInstall.areModulesAvailable(scanner).addOnSuccessListener(response -> {
            onResult.onResult(response.areModulesAvailable());
        }).addOnFailureListener(e -> {
            throw new RuntimeException(e);
        });
    }

    public static void performScan(Context context, SuccessScanCallback cb, OnFailureCallback fb) {

        var scanner = GmsBarcodeScanning.getClient(context,scannerOpts);

        checkBarcodeModuleInstalled(context, scanner, result -> {
            if (result) {
                innerScan(context, scanner, cb, fb);
            } else {
                // install module
                var moduleRequest = ModuleInstallRequest.newBuilder().addApi(scanner).build();
                Toast.makeText(context, "Installing modules...", Toast.LENGTH_LONG).show();

                ModuleInstall.getClient(context).installModules(moduleRequest).addOnSuccessListener(response -> {
                    if(response.areModulesAlreadyInstalled()) {
                        Toast.makeText(context, "Modules installed", Toast.LENGTH_SHORT).show();

                        innerScan(context, scanner, cb, fb);
                    }
                });
            }
        });
        return;

    }

    private static void innerScan(Context context, GmsBarcodeScanner scanner, SuccessScanCallback cb, OnFailureCallback fb) {
        //Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show();
        scanner.startScan().addOnSuccessListener(barcode -> {
            var qrraw = barcode.getRawValue();
            if (qrraw != null) {
                // TODO: check raw value
                // make client
                var token = MyPreferences.getProverkachekaToken(context);
                // TODO check token
                var client = new ProverkacheckaClient(token);

                    client.getByQRRawAsync(qrraw, resp -> {
                        JSONObject receiptJson;
                        try {
                            receiptJson = new JSONObject(resp);
                            // check code
                            var code = receiptJson.getInt("code");
                            if (code != 1) {
                                fb.onFailure(new RuntimeException(receiptJson.getString("data")));
                                return;
                            }
                        } catch (JSONException e) {
                            fb.onFailure(e);
                            return;
                        }
                        try {
                            var receiptData = receiptJson.getJSONObject("data").getJSONObject("json");
                            var transaction = QRUtils.createTransactionFromJson(receiptData);
                            var db = new DatabaseAdapter(context);
                            db.open();
                            db.insertOrUpdate(transaction);
                            db.close();
                            if(cb != null) {
                                cb.onSuccess();
                            }
                        } catch (Exception e) {
                            fb.onFailure(e);
                        }

                    }, fb::onFailure);
            }
        }).addOnFailureListener(fb::onFailure);
    }
    public static Transaction createTransactionFromJson(JSONObject obj) {
        var t = new Transaction();
        // TODO from preferences
        t.fromAccountId = 1;
        t.status = TransactionStatus.PN;
        try {

            // date
            t.dateTime = formatter.parse(obj.getString("dateTime")).getTime();// '2020-09-24T18:37:00
            // total amount
            t.fromAmount = -obj.getLong("totalSum");

            // items
            var items = obj.getJSONArray("items");
            if (items.length() == 1) {
                var item = items.getJSONObject(0);
                t.note = item.getString("name");
            } else {
                long seqId = 0;
                t.categoryId = -1; // set to split
                t.splits = new ArrayList<>(items.length());
                for (int i=0; i< items.length(); i++) {
                    var item = items.getJSONObject(i);
                    Transaction split = new Transaction();
                    split.id = --seqId;
                    split.fromAccountId = t.fromAccountId;
                    split.originalCurrencyId = t.originalCurrencyId;
                    split.fromAmount = -item.getLong("sum");
                    split.note = item.getString("name");
                    t.splits.add(split);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
