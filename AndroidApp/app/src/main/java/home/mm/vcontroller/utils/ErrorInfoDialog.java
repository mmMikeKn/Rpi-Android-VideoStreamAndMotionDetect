package home.mm.vcontroller.utils;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Dialog;

import home.mm.vcontroller.R;

public class ErrorInfoDialog extends DialogFragment {
    private String mMessage;

    public void showInfo(FragmentManager fm, String msg) {
        mMessage = msg;
        show(fm, "ErrorInfoDialog");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        return new AlertDialog.Builder(getActivity(), R.style.alertDialog)
                .setTitle("Alert").setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(mMessage)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                       // activity.finish();
                    }
                })
                .create();
    }
}
