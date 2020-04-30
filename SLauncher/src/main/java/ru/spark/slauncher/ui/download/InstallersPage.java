package ru.spark.slauncher.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ru.spark.slauncher.download.RemoteVersion;
import ru.spark.slauncher.game.GameRepository;
import ru.spark.slauncher.setting.Theme;
import ru.spark.slauncher.ui.FXUtils;
import ru.spark.slauncher.ui.SVG;
import ru.spark.slauncher.ui.construct.Validator;
import ru.spark.slauncher.ui.wizard.WizardController;
import ru.spark.slauncher.ui.wizard.WizardPage;
import ru.spark.slauncher.util.StringUtils;
import ru.spark.slauncher.util.i18n.I18n;
import ru.spark.slauncher.util.platform.OperatingSystem;

import java.util.Map;

public class InstallersPage extends Control implements WizardPage {
    protected final WizardController controller;

    protected InstallerPageItem game = new InstallerPageItem("game");
    protected InstallerPageItem fabric = new InstallerPageItem("fabric");
    protected InstallerPageItem forge = new InstallerPageItem("forge");
    protected InstallerPageItem liteLoader = new InstallerPageItem("liteloader");
    protected InstallerPageItem optiFine = new InstallerPageItem("optifine");
    protected JFXTextField txtName = new JFXTextField();
    protected BooleanProperty installable = new SimpleBooleanProperty();

    public InstallersPage(WizardController controller, GameRepository repository, String gameVersion, InstallerWizardDownloadProvider downloadProvider) {
        this.controller = controller;

        Validator hasVersion = new Validator(s -> !repository.hasVersion(s) && StringUtils.isNotBlank(s));
        hasVersion.setMessage(I18n.i18n("install.new_game.already_exists"));
        Validator nameValidator = new Validator(OperatingSystem::isNameValid);
        nameValidator.setMessage(I18n.i18n("install.new_game.malformed"));
        txtName.getValidators().addAll(hasVersion, nameValidator);
        installable.bind(Bindings.createBooleanBinding(() -> txtName.validate(),
                txtName.textProperty()));
        txtName.setText(gameVersion);

        InstallerPageItem[] libraries = new InstallerPageItem[]{game, fabric, forge, liteLoader, optiFine};

        for (InstallerPageItem library : libraries) {
            String libraryId = library.id;
            if (libraryId.equals("game")) continue;
            library.action.set(e ->
                    controller.onNext(new VersionsPage(controller, I18n.i18n("install.installer.choose", I18n.i18n("install.installer." + libraryId)), gameVersion, downloadProvider, libraryId, () -> controller.onPrev(false))));
            library.removeAction.set(e -> {
                controller.getSettings().remove(libraryId);
                reload();
            });
        }
    }

    @Override
    public String getTitle() {
        return I18n.i18n("install.new_game");
    }

    private String getVersion(String id) {
        return ((RemoteVersion) controller.getSettings().get(id)).getSelfVersion();
    }

    protected void reload() {
        InstallerPageItem[] libraries = new InstallerPageItem[]{game, fabric, forge, liteLoader, optiFine};

        for (InstallerPageItem library : libraries) {
            String libraryId = library.id;
            if (controller.getSettings().containsKey(libraryId)) {
                library.label.set(I18n.i18n("install.installer.version", I18n.i18n("install.installer." + libraryId), getVersion(libraryId)));
                library.removable.set(true);
            } else {
                library.label.setValue(I18n.i18n("install.installer.not_installed", I18n.i18n("install.installer." + libraryId)));
                library.removable.set(false);
            }
        }
    }

    @Override
    public void onNavigate(Map<String, Object> settings) {
        reload();
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @FXML
    protected void onInstall() {
        controller.getSettings().put("name", txtName.getText());
        controller.onFinish();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallersPageSkin(this);
    }

    protected static class InstallerPageItem {
        String id;
        StringProperty label = new SimpleStringProperty();
        BooleanProperty removable = new SimpleBooleanProperty();
        ObjectProperty<EventHandler<? super MouseEvent>> removeAction = new SimpleObjectProperty<>();
        ObjectProperty<EventHandler<? super MouseEvent>> action = new SimpleObjectProperty<>();

        public InstallerPageItem(String id) {
            this.id = id;
        }
    }

    protected static class InstallersPageSkin extends SkinBase<InstallersPage> {

        protected static class InstallersPageItemSkin extends BorderPane {
            final ImageView imageView;
            final Label label;

            InstallersPageItemSkin(String imageUrl, InstallerPageItem item, boolean clickable) {
                getStyleClass().add("card");

                setLeft(FXUtils.limitingSize(imageView = new ImageView(new Image(imageUrl, 32, 32, true, true)), 32, 32));
                setCenter(label = new Label());
                label.textProperty().bind(item.label);
                BorderPane.setMargin(label, new Insets(0, 0, 0, 8));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                if (clickable) {
                    HBox right = new HBox();
                    right.setAlignment(Pos.CENTER_RIGHT);
                    setRight(right);
                    JFXButton closeButton = new JFXButton();
                    closeButton.setGraphic(SVG.close(Theme.blackFillBinding(), -1, -1));
                    right.getChildren().add(closeButton);
                    closeButton.getStyleClass().add("toggle-icon4");
                    closeButton.visibleProperty().bind(item.removable);
                    closeButton.onMouseClickedProperty().bind(item.removeAction);
                    onMouseClickedProperty().bind(item.action);
                    JFXButton arrowButton = new JFXButton();
                    arrowButton.setGraphic(SVG.arrowRight(Theme.blackFillBinding(), -1, -1));
                    arrowButton.onMouseClickedProperty().bind(item.action);
                    arrowButton.getStyleClass().add("toggle-icon4");
                    right.getChildren().add(arrowButton);
                    setCursor(Cursor.HAND);
                }
            }
        }

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected InstallersPageSkin(InstallersPage control) {
            super(control);

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(16));

            VBox list = new VBox(8);
            root.setCenter(list);
            {
                HBox versionNamePane = new HBox(8);
                versionNamePane.setAlignment(Pos.CENTER_LEFT);
                versionNamePane.getStyleClass().add("card");
                versionNamePane.setStyle("-fx-padding: 20 8 20 16");

                versionNamePane.getChildren().add(new Label(I18n.i18n("archive.name")));

                control.txtName.setMaxWidth(300);
                versionNamePane.getChildren().add(control.txtName);
                list.getChildren().add(versionNamePane);
            }

            InstallersPageItemSkin game = new InstallersPageItemSkin("/assets/img/grass.png", control.game, false);
            InstallersPageItemSkin fabric = new InstallersPageItemSkin("/assets/img/fabric.png", control.fabric, true);
            InstallersPageItemSkin forge = new InstallersPageItemSkin("/assets/img/forge.png", control.forge, true);
            InstallersPageItemSkin liteLoader = new InstallersPageItemSkin("/assets/img/chicken.png", control.liteLoader, true);
            InstallersPageItemSkin optiFine = new InstallersPageItemSkin("/assets/img/command.png", control.optiFine, true);
            list.getChildren().addAll(game, fabric, forge, liteLoader, optiFine);
            list.getChildren().forEach(node -> JFXDepthManager.setDepth(node, 1));

            {
                JFXButton installButton = new JFXButton(I18n.i18n("button.install"));
                installButton.disableProperty().bind(control.installable.not());
                installButton.getStyleClass().add("jfx-button-raised");
                installButton.setButtonType(JFXButton.ButtonType.RAISED);
                installButton.setPrefWidth(100);
                installButton.setPrefHeight(40);
                installButton.setOnMouseClicked(e -> control.onInstall());
                BorderPane.setAlignment(installButton, Pos.CENTER_RIGHT);
                root.setBottom(installButton);
            }

            getChildren().setAll(root);
        }
    }
}
