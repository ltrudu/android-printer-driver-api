/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aevi.demoprinterdriver.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.print.PrintHelper;
import android.util.Log;
import android.widget.Toast;

import com.aevi.android.rxmessenger.MessageException;
import com.aevi.android.rxmessenger.activity.NoSuchInstanceException;
import com.aevi.android.rxmessenger.activity.ObservableActivityHelper;
import com.aevi.demoprinterdriver.R;
import com.aevi.print.PrintPreview;
import com.aevi.print.model.PrintJob;
import com.aevi.print.model.PrintPayload;
import com.aevi.print.model.PrinterSettings;

import java.util.List;
import java.util.UUID;

import static androidx.print.PrintHelper.COLOR_MODE_MONOCHROME;
import static androidx.print.PrintHelper.ORIENTATION_PORTRAIT;
import static androidx.print.PrintHelper.OnPrintFinishCallback;
import static androidx.print.PrintHelper.SCALE_MODE_FIT;
import static com.aevi.print.model.PrintJob.State.PRINTED;
import static com.aevi.print.model.PrinterMessages.ERROR_PRINT_FAILED;

public class AndroidPrintActivity extends Activity {

    public static final String KEY_PAYLOAD = "payload";
    public static final String KEY_PRINTER_SETTINGS = "settings";
    private static final String TAG = AndroidPrintActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent.hasExtra(KEY_PAYLOAD) && intent.hasExtra(KEY_PRINTER_SETTINGS)) {
            PrintPayload printPayload = PrintPayload.fromJson(intent.getStringExtra(KEY_PAYLOAD));
            PrinterSettings printerSettings = PrinterSettings.fromJson(intent.getStringExtra(KEY_PRINTER_SETTINGS));
            printReceipt(printPayload, printerSettings);
        } else {
            finish();
        }
    }

    private void printReceipt(PrintPayload payload, PrinterSettings printerSettings) {
        ObservableActivityHelper<PrintJob> resultHelper;
        try {
            resultHelper = ObservableActivityHelper.getInstance(getIntent());
        } catch (NoSuchInstanceException e) {
            Log.e(TAG, "Failed to print via Android", e);
            Toast.makeText(this, R.string.printing_service_failure, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (isAndroidPrintServiceInstalled()) {
            try {
                String jobName = UUID.randomUUID().toString();
                handleSimplePrint(payload, printerSettings, resultHelper, jobName);
            } catch (Throwable t) {
                // YES we are catching all exceptions here
                Log.e(TAG, "Failed to print via Android", t);
                resultHelper.sendErrorToClient(new MessageException(ERROR_PRINT_FAILED, t.getMessage()));
            }
        } else {
            resultHelper.sendErrorToClient(new MessageException(ERROR_PRINT_FAILED, "Android print service is unavailable"));
            finish();
        }
    }

    public boolean isAndroidPrintServiceInstalled() {

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolveInfo = packageManager.queryIntentServices(new Intent("android.printservice.PrintService"), 0);

        if (resolveInfo != null && resolveInfo.size() > 0) {
            Log.i(TAG, "Android print service is available");
            return true;
        } else {
            Log.i(TAG, "Android print service is unavailable");
            return false;
        }
    }

    private void handleSimplePrint(PrintPayload payload, PrinterSettings printerSettings, final ObservableActivityHelper<PrintJob> resultHelper,
                                   String jobName) {
        final Bitmap printBitmap = new PrintPreview(payload, printerSettings).getBitmap();
        PrintHelper helper = new PrintHelper(this);
        helper.setScaleMode(SCALE_MODE_FIT);
        helper.setOrientation(ORIENTATION_PORTRAIT);
        helper.setColorMode(COLOR_MODE_MONOCHROME);
        helper.printBitmap(jobName, printBitmap, new OnPrintFinishCallback() {
            @Override
            public void onFinish() {
                resultHelper.sendMessageToClient(new PrintJob(PRINTED));
                printBitmap.recycle();
                finish();
            }
        });
    }
}
