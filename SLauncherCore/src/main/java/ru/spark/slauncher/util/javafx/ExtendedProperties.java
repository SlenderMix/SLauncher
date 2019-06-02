package ru.spark.slauncher.util.javafx;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.collections.ObservableList;
import javafx.scene.control.*;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static ru.spark.slauncher.util.Pair.pair;

/**
 * @author Spark1337
 */
public final class ExtendedProperties {

    private static final String PROP_PREFIX = ExtendedProperties.class.getName();

    // ==== ComboBox ====
    @SuppressWarnings("unchecked")
    public static <T> ObjectProperty<T> selectedItemPropertyFor(ComboBox<T> comboBox) {
        return (ObjectProperty<T>) comboBox.getProperties().computeIfAbsent(
                PROP_PREFIX + ".comboxBox.selectedItem",
                any -> createPropertyForSelectionModel(comboBox, comboBox.selectionModelProperty()));
    }

    private static <T> ObjectProperty<T> createPropertyForSelectionModel(Object bean, Property<? extends SelectionModel<T>> modelProperty) {
        return new ReadWriteComposedProperty<>(bean, "extra.selectedItem",
                BindingMapping.of(modelProperty)
                        .flatMap(SelectionModel::selectedItemProperty),
                obj -> modelProperty.getValue().select(obj));
    }
    // ====

    // ==== Toggle ====
    @SuppressWarnings("unchecked")
    public static ObjectProperty<Toggle> selectedTogglePropertyFor(ToggleGroup toggleGroup) {
        return (ObjectProperty<Toggle>) toggleGroup.getProperties().computeIfAbsent(
                PROP_PREFIX + ".toggleGroup.selectedToggle",
                any -> createPropertyForToggleGroup(toggleGroup));
    }

    private static ObjectProperty<Toggle> createPropertyForToggleGroup(ToggleGroup toggleGroup) {
        return new ReadWriteComposedProperty<>(toggleGroup, "extra.selectedToggle",
                toggleGroup.selectedToggleProperty(),
                toggleGroup::selectToggle);
    }

    public static <T> ObjectProperty<T> createSelectedItemPropertyFor(ObservableList<? extends Toggle> items, Class<T> userdataType) {
        return selectedItemPropertyFor(new AutomatedToggleGroup(items), userdataType);
    }

    private ExtendedProperties() {
    }

    private static <T> ObjectProperty<T> createMappedPropertyForToggleGroup(ToggleGroup toggleGroup, Function<Toggle, T> mapper) {
        ObjectProperty<Toggle> selectedToggle = selectedTogglePropertyFor(toggleGroup);
        AtomicReference<Optional<T>> pendingItemHolder = new AtomicReference<>();

        Consumer<T> itemSelector = newItem -> {
            Optional<Toggle> toggleToSelect = toggleGroup.getToggles().stream()
                    .filter(toggle -> Objects.equals(newItem, mapper.apply(toggle)))
                    .findFirst();
            if (toggleToSelect.isPresent()) {
                pendingItemHolder.set(null);
                selectedToggle.set(toggleToSelect.get());
            } else {
                // We are asked to select an nonexistent item.
                // However, this item may become available in the future.
                // So here we store it, and once the associated toggle becomes available, we will update the selection.
                pendingItemHolder.set(Optional.ofNullable(newItem));
                selectedToggle.set(null);
            }
        };

        ReadWriteComposedProperty<T> property = new ReadWriteComposedProperty<>(toggleGroup, "extra.selectedItem",
                BindingMapping.of(selectedTogglePropertyFor(toggleGroup))
                        .map(mapper),
                itemSelector);

        InvalidationListener onTogglesChanged = any -> {
            Optional<T> pendingItem = pendingItemHolder.get();
            if (pendingItem.isPresent()) {
                itemSelector.accept(pendingItem.orElse(null));
            }
        };
        toggleGroup.getToggles().addListener(new WeakInvalidationListener(onTogglesChanged));
        property.addListener(new ReferenceHolder(onTogglesChanged));

        return property;
    }
    // ====

    // ==== CheckBox ====
    @SuppressWarnings("unchecked")
    public static ObjectProperty<Boolean> reservedSelectedPropertyFor(CheckBox checkbox) {
        return (ObjectProperty<Boolean>) checkbox.getProperties().computeIfAbsent(
                PROP_PREFIX + ".checkbox.reservedSelected",
                any -> new MappedProperty<>(checkbox, "ext.reservedSelected",
                        checkbox.selectedProperty(), it -> !(boolean) it, it -> !(boolean) it));
    }
    // ====

    @SuppressWarnings("unchecked")
    public static <T> ObjectProperty<T> selectedItemPropertyFor(ToggleGroup toggleGroup, Class<T> userdataType) {
        return (ObjectProperty<T>) toggleGroup.getProperties().computeIfAbsent(
                pair(PROP_PREFIX + ".toggleGroup.selectedItem", userdataType),
                any -> createMappedPropertyForToggleGroup(
                        toggleGroup,
                        toggle -> toggle == null ? null : userdataType.cast(toggle.getUserData())));
    }
}