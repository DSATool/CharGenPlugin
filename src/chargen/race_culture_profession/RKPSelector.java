/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package chargen.race_culture_profession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Tuple3;
import dsatool.util.Util;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class RKPSelector {
	@FXML
	private Node pane;
	@FXML
	private TreeView<RKP> tree;
	@FXML
	private TreeItem<RKP> root;
	@FXML
	private VBox variantsBox;

	private RKP currentChoice;
	private List<RKP> currentVariants;
	private final List<RKP> variants = new ArrayList<>();
	private final Map<RKP, ChangeListener<Boolean>> validListeners = new HashMap<>();
	private final Map<RKP, ChangeListener<Boolean>> suggestedListeners = new HashMap<>();
	private ObservableList<Node> variantNodes;

	private Runnable updateValue;

	public RKPSelector(final Runnable updateValue) {
		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("RKPSelector.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		this.updateValue = updateValue;

		tree.getSelectionModel().selectedItemProperty().addListener((ChangeListener<TreeItem<RKP>>) (observable, oldValue, newValue) -> {
			updateSelection(newValue);
		});

		tree.setCellFactory(tv -> {
			final TreeCell<RKP> cell = new TreeCell<>() {
				@Override
				public void updateItem(final RKP item, final boolean empty) {
					super.updateItem(item, empty);
					if (empty) {
						setText(null);
						setGraphic(null);
					} else {
						setText(item.toString());
						Util.addReference(this, item.data, 45 + item.depth * 12, tree.widthProperty());
					}
				}
			};
			final ChangeListener<Boolean> validListener = (o, oldV, newV) -> {
				cell.getStyleClass().remove("invalid");
				if (!newV) {
					cell.getStyleClass().remove("valid");
					cell.getStyleClass().add("invalid");
				}
			};
			final ChangeListener<Boolean> suggestedListener = (o, oldV, newV) -> {
				cell.getStyleClass().remove("valid");
				if (newV && cell.getItem().valid.get()) {
					cell.getStyleClass().add("valid");
				}
			};
			cell.itemProperty().addListener((observable, oldValue, newValue) -> {
				if (oldValue != null) {
					oldValue.valid.removeListener(validListener);
					oldValue.suggested.removeListener(suggestedListener);
				}
				cell.getStyleClass().remove("valid");
				cell.getStyleClass().remove("invalid");
				if (newValue != null) {
					newValue.valid.addListener(validListener);
					newValue.suggested.addListener(suggestedListener);
					if (!newValue.valid.get()) {
						cell.getStyleClass().add("invalid");
					} else if (newValue.suggested.get()) {
						cell.getStyleClass().add("valid");
					}
				}
			});
			return cell;
		});
	}

	private void addItem(final TreeItem<RKP> parent, final String name, final JSONObject item,
			final Function<Tuple3<String, JSONObject, RKP>, RKP> constructor) {
		if (!item.getBoolOrDefault("kombinierbar", false)) {
			final TreeItem<RKP> treeItem = new TreeItem<>(constructor.apply(new Tuple3<>(name, item, parent.getValue())));
			parent.getChildren().add(treeItem);
			if (item.containsKey("Varianten")) {
				final JSONObject variants = item.getObj("Varianten");
				for (final String variantName : variants.keySet()) {
					final JSONObject variant = variants.getObj(variantName);
					addItem(treeItem, variantName, variant, constructor);
				}
			}
		}
	}

	private TreeItem<RKP> findChild(final TreeItem<RKP> item, final String child) {
		for (final TreeItem<RKP> current : item.getChildren()) {
			if (child.equals(current.getValue().name)) return current;
		}
		return null;
	}

	public Node getControl() {
		return pane;
	}

	public RKP getCurrentChoice() {
		return currentChoice;
	}

	public List<RKP> getCurrentVariants() {
		return currentVariants;
	}

	public void select(final String top, final JSONArray modifications) {
		TreeItem<RKP> current = findChild(root, top);
		String old = top;

		if (current == null) {
			current = root;
			return;
		}

		if (modifications != null) {
			int i = 0;
			for (; i < modifications.size(); ++i) {
				TreeItem<RKP> child = findChild(current, modifications.getString(i));
				if (child != null) {
					old = modifications.getString(i);
				} else {
					child = findChild(current, old);
					--i;
				}
				current.setExpanded(true);
				if (child == null) {
					break;
				}
				current = child;
			}
			if (variantNodes != null) {
				for (Math.max(--i, 0); i < modifications.size(); ++i) {
					for (final Node box : variantNodes) {
						final String name = ((CheckBox) box).getText();
						final String actualName = modifications.getString(i);
						if (name.length() >= actualName.length() + 5 && name.length() <= actualName.length() + 6 && name.startsWith(actualName + " (")) {
							((CheckBox) box).setSelected(true);
						}
					}
				}
			}
		}

		while (!current.isLeaf()) {
			current.setExpanded(true);
			current = findChild(current, old);
		}
		tree.getSelectionModel().select(current);
	}

	public void setData(final JSONObject data, final Function<Tuple3<String, JSONObject, RKP>, RKP> itemConstructor) {
		root.getChildren().clear();
		root.setValue(null);
		if (data != null) {
			for (final String itemName : data.keySet()) {
				final JSONObject item = data.getObj(itemName);
				addItem(root, itemName, item, itemConstructor);
			}
		}
		tree.getSelectionModel().clearSelection();
		updateSelection(null);
	}

	public void setRoot(final String name, final JSONObject data, final Function<Tuple3<String, JSONObject, RKP>, RKP> itemConstructor) {
		root.getChildren().clear();
		if (data != null) {
			root.setValue(itemConstructor.apply(new Tuple3<>(name, data, null)));
			for (final String itemName : data.getObj("Varianten").keySet()) {
				final JSONObject item = data.getObj("Varianten").getObj(itemName);
				addItem(root, itemName, item, itemConstructor);
			}
			tree.getSelectionModel().select(root);
			updateSelection(root);
		} else {
			tree.getSelectionModel().clearSelection();
			updateSelection(null);
		}
	}

	private void updateSelection(final TreeItem<RKP> selected) {
		variantNodes = variantsBox.getChildren();
		variantNodes.clear();
		for (final RKP variant : variants) {
			variant.suggested.removeListener(suggestedListeners.get(variant));
			variant.valid.removeListener(validListeners.get(variant));
		}
		variants.clear();
		suggestedListeners.clear();
		validListeners.clear();
		if (selected != null && selected.getChildren().isEmpty()) {
			final RKP value = selected.getValue();
			final List<RKP> actualVariants = value.getVariants();
			currentVariants = new ArrayList<>(actualVariants.size());
			for (final RKP variant : value.getVariants()) {
				final CheckBox variantCheckbox = new CheckBox(variant.name + " (" + Util.getSignedIntegerString(variant.getCost(0)) + ")");
				variantCheckbox.setPrefWidth(500);
				Util.addReference(variantCheckbox, variant.data, 60, variantCheckbox.widthProperty());
				variantCheckbox.selectedProperty().addListener((o, oldV, newV) -> {
					if (newV) {
						currentVariants.add(variant);
					} else {
						currentVariants.remove(variant);
					}
					updateValue.run();
				});
				if (!variant.valid.get()) {
					variantCheckbox.getStyleClass().add("invalid");
				} else if (variant.suggested.get()) {
					variantCheckbox.getStyleClass().add("valid");
				}
				variants.add(variant);
				final ChangeListener<Boolean> validListener = (o, oldV, newV) -> {
					variantCheckbox.getStyleClass().remove("invalid");
					if (!newV) {
						variantCheckbox.getStyleClass().remove("valid");
						variantCheckbox.getStyleClass().add("invalid");
					}
				};
				final ChangeListener<Boolean> suggestedListener = (o, oldV, newV) -> {
					variantCheckbox.getStyleClass().remove("valid");
					if (newV && variant.valid.get()) {
						variantCheckbox.getStyleClass().add("valid");
					}
				};
				variant.suggested.addListener(suggestedListener);
				variant.valid.addListener(validListener);
				validListeners.put(variant, validListener);
				suggestedListeners.put(variant, suggestedListener);
				variantNodes.add(variantCheckbox);
			}
			currentChoice = value;
		} else {
			if (selected != null && !selected.getChildren().isEmpty()) {
				selected.setExpanded(true);
			}
			currentChoice = null;
			currentVariants = null;
		}
		if (selected != null) {
			updateValue.run();
		}
	}

	public void updateSuggestedPossible(final Function<RKP, Boolean> suggested, final Function<RKP, Boolean> possible) {
		updateSuggestedPossible(root, suggested, possible);
	}

	private Tuple<Boolean, Boolean> updateSuggestedPossible(final TreeItem<RKP> treeItem, final Function<RKP, Boolean> suggested,
			final Function<RKP, Boolean> possible) {
		final RKP item = treeItem.getValue();
		if (item != null) {
			for (final RKP variant : item.getVariants()) {
				if (suggested.apply(variant)) {
					variant.valid.set(true);
					variant.suggested.set(true);
				} else if (!possible.apply(variant)) {
					variant.valid.set(false);
					variant.suggested.set(false);
				} else {
					variant.valid.set(true);
					variant.suggested.set(false);
				}
			}
		}
		if (item != null && suggested.apply(item)) {
			item.valid.set(true);
			item.suggested.set(true);
			for (final TreeItem<RKP> variantItem : treeItem.getChildren()) {
				updateSuggestedPossible(variantItem, rkp -> false, rkp -> true);
			}
			return new Tuple<>(true, true);
		} else if (item != null && possible.apply(item)) {
			item.valid.set(true);
			item.suggested.set(false);
			for (final TreeItem<RKP> variantItem : treeItem.getChildren()) {
				updateSuggestedPossible(variantItem, rkp -> false, rkp -> true);
			}
			return new Tuple<>(true, false);
		} else {
			if (item != null && treeItem.getChildren().size() == 0) {
				item.valid.set(false);
				item.suggested.set(false);
				return new Tuple<>(false, false);
			} else {
				boolean someValid = false;
				boolean allSuggested = true;
				for (final TreeItem<RKP> variantItem : treeItem.getChildren()) {
					final Tuple<Boolean, Boolean> child = updateSuggestedPossible(variantItem, suggested, possible);
					if (child._1) {
						someValid = true;
					}
					if (!child._2) {
						allSuggested = false;
					}
				}
				if (item != null) {
					item.valid.set(someValid);
					item.suggested.set(allSuggested);
				}
				return new Tuple<>(someValid, allSuggested);
			}
		}
	}

}
