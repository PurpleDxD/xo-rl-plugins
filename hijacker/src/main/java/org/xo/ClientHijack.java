package org.xo;

import net.runelite.client.RuneLite;

public class ClientHijack {

    public ClientHijack() {
        new Thread(() -> {
            while (RuneLite.getInjector() == null) {
                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            RuneLite.getInjector().getInstance(HijackedClientBackup.class).start();
        }).start();
    }

}
