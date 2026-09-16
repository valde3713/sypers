package dk.sypers.app;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import java.util.HashSet;
import java.util.Set;

public class SypersCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        String number = details.getHandle() == null
                ? ""
                : details.getHandle().getSchemeSpecificPart().replaceAll("\\D", "");
        Set<String> blocked = getSharedPreferences("sypers", MODE_PRIVATE)
                .getStringSet("blockedNumbers", new HashSet<>());

        if (blocked.contains(number)) {
            CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build();
            respondToCall(details, response);
        } else {
            respondToCall(details, new CallResponse.Builder().build());
        }
    }
}
