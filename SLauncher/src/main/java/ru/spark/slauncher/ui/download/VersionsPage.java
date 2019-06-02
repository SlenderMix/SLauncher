package ru.spark.slauncher.ui.download;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ru.spark.slauncher.download.DownloadProvider;
import ru.spark.slauncher.download.VersionList;
import ru.spark.slauncher.task.TaskExecutor;
import ru.spark.slauncher.ui.FXUtils;
import ru.spark.slauncher.ui.animation.ContainerAnimations;
import ru.spark.slauncher.ui.animation.TransitionHandler;
import ru.spark.slauncher.ui.wizard.Refreshable;
import ru.spark.slauncher.ui.wizard.WizardController;
import ru.spark.slauncher.ui.wizard.WizardPage;
import ru.spark.slauncher.util.Logging;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class VersionsPage extends BorderPane implements WizardPage, Refreshable {
    private final String gameVersion;
    private final DownloadProvider downloadProvider;
    private final String libraryId;
    private final String title;
    private final WizardController controller;
    private final TransitionHandler transitionHandler;
    private final VersionList<?> versionList;
    @FXML
    private JFXListView<VersionsPageItem> list;
    @FXML
    private JFXSpinner spinner;
    @FXML
    private StackPane failedPane;
    @FXML
    private StackPane emptyPane;
    @FXML
    private StackPane root;
    @FXML
    private JFXCheckBox chkRelease;
    @FXML
    private JFXCheckBox chkSnapshot;
    @FXML
    private JFXCheckBox chkOld;
    @FXML
    private HBox checkPane;
    @FXML
    private VBox centrePane;
    private TaskExecutor executor;

    public VersionsPage(WizardController controller, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.downloadProvider = downloadProvider;
        this.libraryId = libraryId;
        this.controller = controller;
        this.versionList = downloadProvider.getVersionListById(libraryId);

        FXUtils.loadFXML(this, "/assets/fxml/download/versions.fxml");

        transitionHandler = new TransitionHandler(root);

        if (versionList.hasType()) {
            centrePane.getChildren().setAll(checkPane, list);
        } else
            centrePane.getChildren().setAll(list);

        InvalidationListener listener = o -> list.getItems().setAll(loadVersions());
        chkRelease.selectedProperty().addListener(listener);
        chkSnapshot.selectedProperty().addListener(listener);
        chkOld.selectedProperty().addListener(listener);

        list.setOnMouseClicked(e -> {
            if (list.getSelectionModel().getSelectedIndex() < 0)
                return;
            controller.getSettings().put(libraryId, list.getSelectionModel().getSelectedItem().getRemoteVersion());
            callback.run();
        });
        refresh();
    }

    private List<VersionsPageItem> loadVersions() {
        return versionList.getVersions(gameVersion).stream()
                .filter(it -> {
                    switch (it.getVersionType()) {
                        case RELEASE:
                            return chkRelease.isSelected();
                        case SNAPSHOT:
                            return chkSnapshot.isSelected();
                        case OLD:
                            return chkOld.isSelected();
                        default:
                            return true;
                    }
                })
                .sorted()
                .map(VersionsPageItem::new).collect(Collectors.toList());
    }

    @Override
    public void refresh() {
        transitionHandler.setContent(spinner, ContainerAnimations.FADE.getAnimationProducer());
        executor = versionList.refreshAsync(gameVersion, downloadProvider).whenComplete((isDependentSucceeded, exception) -> {
            if (isDependentSucceeded) {
                List<VersionsPageItem> items = loadVersions();

                Platform.runLater(() -> {
                    if (versionList.getVersions(gameVersion).isEmpty()) {
                        transitionHandler.setContent(emptyPane, ContainerAnimations.FADE.getAnimationProducer());
                    } else {
                        if (items.isEmpty()) {
                            chkRelease.setSelected(true);
                            chkSnapshot.setSelected(true);
                            chkOld.setSelected(true);
                        } else {
                            list.getItems().setAll(items);
                        }
                        transitionHandler.setContent(centrePane, ContainerAnimations.FADE.getAnimationProducer());
                    }
                });
            } else {
                Logging.LOG.log(Level.WARNING, "Failed to fetch versions list", exception);
                Platform.runLater(() -> {
                    transitionHandler.setContent(failedPane, ContainerAnimations.FADE.getAnimationProducer());
                });
            }
        }).executor().start();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(libraryId);
        if (executor != null)
            executor.cancel();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onBack() {
        controller.onPrev(true);
    }

    @FXML
    private void onSponsor() {
        FXUtils.openLink("https://vk.com/slauncher");
    }
}
