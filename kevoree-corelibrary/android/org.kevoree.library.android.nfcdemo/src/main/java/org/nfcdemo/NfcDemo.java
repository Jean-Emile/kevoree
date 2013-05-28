package org.nfcdemo;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.kevoree.android.framework.helper.UIServiceHandler;
import org.kevoree.android.framework.service.events.IntentListener;
import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 27/05/13
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Library(name = "Android")
@ComponentType
public class NfcDemo extends AbstractComponentType {

    private NfcAdapter adapter;
    private Tag tag;
    private IntentListener intentListener;
    private LinearLayout mLayout;

    @Start
    public void start()
    {
        adapter = NfcAdapter.getDefaultAdapter(UIServiceHandler.getUIService().getRootActivity());
         intentListener =   new IntentListener() {
            @Override
            public void onNewIntent(Intent intent) {
                if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
                    tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);


                    int uidLen = tag.getId().length;

                    String uid = Common.byte2HexString(tag.getId());
                    uid += " (" + uidLen + " byte";
                    if (uidLen == 7) {
                        uid += ", CL2";
                    } else if (uidLen == 10) {
                        uid += ", CL3";
                    }
                    uid += ")";

                    Toast.makeText(UIServiceHandler.getUIService().getRootActivity(), "Discover Tag with ID --> "+uid, Toast.LENGTH_LONG).show();


                }
            }
        };

        UIServiceHandler.getUIService().addIntentListener(intentListener);

    }



    @Stop
    public void stop() {
        UIServiceHandler.getUIService().removeIntentListener(intentListener);
    }

    @Update
    public void update() {

    }
}
