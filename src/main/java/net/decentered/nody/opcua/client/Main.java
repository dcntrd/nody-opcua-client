package net.decentered.nody.opcua.client;

import net.decentered.nody.opcua.client.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.milo.opcua.stack.core.security.*;

import javax.swing.*;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    static void main() {

        logger.info("Starting Client");

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("Nimbus Look and Feel could not be loaded - using default");
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MainFrame mainFrame = new MainFrame();
                mainFrame.setVisible(true);

            }
        });

    }

}
