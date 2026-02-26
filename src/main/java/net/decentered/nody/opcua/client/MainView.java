package net.decentered.nody.opcua.client;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;

@Route("")
public class MainView extends VerticalLayout {

    public MainView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // ---------- Header with logo ----------
        Image logo = new Image("images/logo-light.svg", "Nody UA Logo");
        logo.setWidth("64px");
        logo.setHeight("64px");

        H1 title = new H1("Nody OPCUA");
        title.addClassName("gradient-text");

        HorizontalLayout header = new HorizontalLayout(logo, title);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        // ---------- Connection input ----------
        TextField urlField = new TextField("Connection URL");
        urlField.setPlaceholder("opc.tcp://localhost:4840");
        urlField.setWidth("400px");

        Button connectButton = new Button("Connect");
        connectButton.addClassName("button-primary");

        HorizontalLayout connectionLayout = new HorizontalLayout(urlField, connectButton);
        connectionLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        connectionLayout.setSpacing(true);

        // ---------- Data panel ----------
        Div dataPanel = new Div();
        dataPanel.addClassName("data-panel");
        dataPanel.setText("Data will appear here...");
        dataPanel.setWidthFull();
        dataPanel.setHeight("300px");

        // ---------- Theme toggle ----------
        Button toggleTheme = new Button("Toggle Dark/Light");
        toggleTheme.addClickListener(e -> {
            UI ui = UI.getCurrent();
            boolean dark = ui.getElement().getThemeList().contains(Lumo.DARK);

            if (dark) {
                ui.getElement().getThemeList().remove(Lumo.DARK);
                logo.setSrc("images/logo-light.svg");
                ui.getPage().executeJs("localStorage.setItem('theme','light');");
            } else {
                ui.getElement().getThemeList().add(Lumo.DARK);
                logo.setSrc("images/logo-dark.svg");
                ui.getPage().executeJs("localStorage.setItem('theme','dark');");
            }
        });

        // ---------- Restore previous theme ----------
        UI.getCurrent().getPage().executeJs(
                "const theme = localStorage.getItem('theme');" +
                        "if(theme === 'dark') {" +
                        "document.documentElement.setAttribute('theme','dark');" +
                        "document.querySelector('img').src='images/logo-dark.svg';" +
                        "}"
        );

        // ---------- Add all components ----------
        add(header, toggleTheme, connectionLayout, dataPanel);
    }
}