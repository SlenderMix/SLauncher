package ru.spark.slauncher.ui.export;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.validation.RequiredFieldValidator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import ru.spark.slauncher.Launcher;
import ru.spark.slauncher.auth.Account;
import ru.spark.slauncher.setting.Accounts;
import ru.spark.slauncher.ui.Controllers;
import ru.spark.slauncher.ui.FXUtils;
import ru.spark.slauncher.ui.construct.ComponentList;
import ru.spark.slauncher.ui.construct.Validator;
import ru.spark.slauncher.ui.wizard.WizardController;
import ru.spark.slauncher.ui.wizard.WizardPage;
import ru.spark.slauncher.util.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModpackInfoPage extends Control implements WizardPage {
    private final WizardController controller;
    private final boolean canIncludeLauncher;
    private final boolean showFileApi;

    private SimpleStringProperty versionName = new SimpleStringProperty();
    private SimpleStringProperty modpackName = new SimpleStringProperty();
    private SimpleStringProperty modpackFileApi = new SimpleStringProperty();
    private SimpleStringProperty modpackAuthor = new SimpleStringProperty();
    private SimpleStringProperty modpackVersion = new SimpleStringProperty("1.0");
    private SimpleStringProperty modpackDescription = new SimpleStringProperty();
    private SimpleBooleanProperty includingLauncher = new SimpleBooleanProperty();
    private ObjectProperty<EventHandler<? super MouseEvent>> next = new SimpleObjectProperty<>();

    public ModpackInfoPage(WizardController controller, String version) {
        this.controller = controller;
        modpackName.set(version);
        modpackAuthor.set(Optional.ofNullable(Accounts.getSelectedAccount()).map(Account::getUsername).orElse(""));
        versionName.set(version);

        List<File> launcherJar = Launcher.getCurrentJarFiles();
        canIncludeLauncher = launcherJar != null;
        showFileApi = controller.getSettings().get(ModpackTypeSelectionPage.MODPACK_TYPE) == ModpackTypeSelectionPage.MODPACK_TYPE_SERVER;

        next.set(e -> onNext());
    }

    @FXML
    private void onNext() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.i18n("modpack.wizard.step.initialization.save"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.i18n("modpack"), "*.zip"));
        File file = fileChooser.showSaveDialog(Controllers.getStage());
        if (file == null) {
            controller.onEnd();
            return;
        }
        controller.getSettings().put(MODPACK_NAME, modpackName.get());
        controller.getSettings().put(MODPACK_FILE_API, modpackFileApi.get());
        controller.getSettings().put(MODPACK_VERSION, modpackVersion.get());
        controller.getSettings().put(MODPACK_AUTHOR, modpackAuthor.get());
        controller.getSettings().put(MODPACK_FILE, file);
        controller.getSettings().put(MODPACK_DESCRIPTION, modpackDescription.get());
        controller.getSettings().put(MODPACK_INCLUDE_LAUNCHER, includingLauncher.get());
        controller.onNext();
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        controller.getSettings().remove(MODPACK_NAME);
        controller.getSettings().remove(MODPACK_VERSION);
        controller.getSettings().remove(MODPACK_AUTHOR);
        controller.getSettings().remove(MODPACK_DESCRIPTION);
        controller.getSettings().remove(MODPACK_INCLUDE_LAUNCHER);
        controller.getSettings().remove(MODPACK_FILE);
    }

    @Override
    public String getTitle() {
        return I18n.i18n("modpack.wizard.step.1.title");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModpackInfoPageSkin(this);
    }

    public static final String MODPACK_NAME = "modpack.name";
    public static final String MODPACK_FILE_API = "modpack.file_api";
    public static final String MODPACK_VERSION = "modpack.version";
    public static final String MODPACK_AUTHOR = "archive.author";
    public static final String MODPACK_DESCRIPTION = "modpack.description";
    public static final String MODPACK_INCLUDE_LAUNCHER = "modpack.include_launcher";
    public static final String MODPACK_FILE = "modpack.file";

    public static class ModpackInfoPageSkin extends SkinBase<ModpackInfoPage> {
        private final JFXTextField txtModpackName;
        private final JFXTextField txtModpackFileApi;
        private final JFXTextField txtModpackAuthor;
        private final JFXTextField txtModpackVersion;

        public ModpackInfoPageSkin(ModpackInfoPage skinnable) {
            super(skinnable);

            Insets insets = new Insets(5, 0, 12, 0);
            Insets componentListMargin = new Insets(16, 0, 16, 0);

            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            getChildren().setAll(scroll);

            {
                BorderPane borderPane = new BorderPane();
                borderPane.setStyle("-fx-padding: 16;");
                scroll.setContent(borderPane);

                if (skinnable.controller.getSettings().get(ModpackTypeSelectionPage.MODPACK_TYPE) == ModpackTypeSelectionPage.MODPACK_TYPE_SERVER) {
                    Hyperlink hyperlink = new Hyperlink(I18n.i18n("modpack.wizard.step.initialization.server"));
                    hyperlink.setOnMouseClicked(e -> {
                        FXUtils.openLink("https://slauncher.ru/#server-modpack");
                    });
                    borderPane.setTop(hyperlink);
                } else {
                    Label label = new Label(I18n.i18n("modpack.wizard.step.initialization.warning"));
                    label.setWrapText(true);
                    label.setTextAlignment(TextAlignment.JUSTIFY);
                    borderPane.setTop(label);
                }

                {
                    ComponentList list = new ComponentList();
                    BorderPane.setMargin(list, componentListMargin);
                    borderPane.setCenter(list);

                    {
                        BorderPane borderPane1 = new BorderPane();
                        borderPane1.setLeft(new Label(I18n.i18n("modpack.wizard.step.initialization.exported_version")));

                        Label versionNameLabel = new Label();
                        versionNameLabel.textProperty().bind(skinnable.versionName);
                        borderPane1.setRight(versionNameLabel);
                        list.getContent().add(borderPane1);
                    }

                    {
                        txtModpackName = new JFXTextField();
                        txtModpackName.textProperty().bindBidirectional(skinnable.modpackName);
                        txtModpackName.setLabelFloat(true);
                        txtModpackName.setPromptText(I18n.i18n("modpack.name"));
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        validator.setMessage(I18n.i18n("modpack.not_a_valid_name"));
                        txtModpackName.getValidators().add(validator);
                        StackPane.setMargin(txtModpackName, insets);
                        list.getContent().add(txtModpackName);
                    }

                    if (skinnable.showFileApi) {
                        txtModpackFileApi = new JFXTextField();
                        txtModpackFileApi.textProperty().bindBidirectional(skinnable.modpackFileApi);
                        txtModpackFileApi.setLabelFloat(true);
                        txtModpackFileApi.setPromptText(I18n.i18n("modpack.file_api"));
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        txtModpackFileApi.getValidators().add(validator);
                        txtModpackFileApi.getValidators().add(new Validator(s -> {
                            try {
                                new URL(s).toURI();
                                return true;
                            } catch (IOException | URISyntaxException e) {
                                return false;
                            }
                        }));
                        StackPane.setMargin(txtModpackFileApi, insets);
                        list.getContent().add(txtModpackFileApi);
                    } else {
                        txtModpackFileApi = null;
                    }

                    {
                        txtModpackAuthor = new JFXTextField();
                        txtModpackAuthor.textProperty().bindBidirectional(skinnable.modpackAuthor);
                        txtModpackAuthor.setLabelFloat(true);
                        txtModpackAuthor.setPromptText(I18n.i18n("archive.author"));
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        txtModpackAuthor.getValidators().add(validator);
                        StackPane.setMargin(txtModpackAuthor, insets);
                        list.getContent().add(txtModpackAuthor);
                    }

                    {
                        txtModpackVersion = new JFXTextField();
                        txtModpackVersion.textProperty().bindBidirectional(skinnable.modpackVersion);
                        txtModpackVersion.setLabelFloat(true);
                        txtModpackVersion.setPromptText(I18n.i18n("archive.version"));
                        RequiredFieldValidator validator = new RequiredFieldValidator();
                        txtModpackVersion.getValidators().add(validator);
                        StackPane.setMargin(txtModpackVersion, insets);
                        list.getContent().add(txtModpackVersion);
                    }

                    {
                        JFXTextArea area = new JFXTextArea();
                        area.textProperty().bindBidirectional(skinnable.modpackDescription);
                        area.setLabelFloat(true);
                        area.setPromptText(I18n.i18n("modpack.desc"));
                        area.setMinHeight(400);
                        StackPane.setMargin(area, insets);
                        list.getContent().add(area);
                    }

                    if (skinnable.controller.getSettings().get(ModpackTypeSelectionPage.MODPACK_TYPE) == ModpackTypeSelectionPage.MODPACK_TYPE_SL) {
                        BorderPane borderPane1 = new BorderPane();
                        borderPane1.setLeft(new Label(I18n.i18n("modpack.wizard.step.initialization.include_launcher")));
                        list.getContent().add(borderPane1);

                        JFXToggleButton button = new JFXToggleButton();
                        button.setDisable(!skinnable.canIncludeLauncher);
                        button.selectedProperty().bindBidirectional(skinnable.includingLauncher);
                        button.setSize(8);
                        button.setMinHeight(16);
                        button.setMaxHeight(16);
                        borderPane1.setRight(button);
                    }
                }

                {
                    HBox hbox = new HBox();
                    hbox.setAlignment(Pos.CENTER_RIGHT);
                    borderPane.setBottom(hbox);

                    JFXButton nextButton = new JFXButton();
                    nextButton.onMouseClickedProperty().bind(skinnable.next);
                    nextButton.setPrefWidth(100);
                    nextButton.setPrefHeight(40);
                    nextButton.setButtonType(JFXButton.ButtonType.RAISED);
                    nextButton.setText(I18n.i18n("wizard.next"));
                    nextButton.getStyleClass().add("jfx-button-raised");
                    if (skinnable.showFileApi) {
                        nextButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                                        !txtModpackName.validate() || !txtModpackVersion.validate() || !txtModpackAuthor.validate() || !txtModpackFileApi.validate(),
                                txtModpackName.textProperty(), txtModpackAuthor.textProperty(), txtModpackVersion.textProperty(), txtModpackFileApi.textProperty()));
                    } else {
                        nextButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                                        !txtModpackName.validate() || !txtModpackVersion.validate() || !txtModpackAuthor.validate(),
                                txtModpackName.textProperty(), txtModpackAuthor.textProperty(), txtModpackVersion.textProperty()));
                    }
                    hbox.getChildren().add(nextButton);
                }
            }

            FXUtils.smoothScrolling(scroll);
        }
    }
}
