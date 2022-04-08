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
package chargen.pros_cons_skills;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import jsonant.event.JSONListener;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class GroupSelector {

	@FXML
	private TableView<ProConOrSkill> possibleTable;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleNameColumn;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleDescColumn;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleVariantColumn;
	@FXML
	private TableColumn<ProConOrSkill, Integer> possibleValueColumn;
	@FXML
	private TableColumn<ProConOrSkill, Boolean> possibleValidColumn;
	@FXML
	private TableColumn<ProConOrSkill, Boolean> possibleSuggestedColumn;

	private final JSONObject generationState;
	private final String type;

	private final JSONObject possibleProsOrCons;

	private Node parent;

	private final BooleanProperty showAll;
	private final ObservableMap<ProConOrSkill, ChangeListener<Boolean>> valid = FXCollections.observableHashMap();
	private final ObservableMap<ProConOrSkill, ChangeListener<Boolean>> invalid = FXCollections.observableHashMap();
	private final ObservableList<ProConOrSkill> allItems = FXCollections.observableArrayList(item -> new Observable[] { valid });

	private final JSONListener listener = o -> {
		initializePossibleTable();
	};

	private final Set<String> specialHandledProsAndCons;
	{
		specialHandledProsAndCons = new HashSet<>();
		specialHandledProsAndCons.add("Breitgefächerte Bildung");
		specialHandledProsAndCons.add("Veteran");
		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		for (final String attributeName : attributes.keySet()) {
			final JSONObject attribute = attributes.getObj(attributeName);
			specialHandledProsAndCons.add(attribute.getString("Herausragende Eigenschaft"));
			specialHandledProsAndCons.add(attribute.getString("Miserable Eigenschaft"));
		}
	}

	public GroupSelector(final JSONObject generationState, final String type, final JSONObject possibleProsOrCons, final BooleanProperty showAll) {
		this.generationState = generationState;
		this.type = type;
		this.possibleProsOrCons = possibleProsOrCons;
		this.showAll = showAll;

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("ProCon.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		possibleTable.setRowFactory(t -> {
			final TableRow<ProConOrSkill> row = new TableRow<>();

			final ContextMenu possibleMenu = new ContextMenu();

			final MenuItem addItem = new MenuItem("Hinzufügen");
			possibleMenu.getItems().add(addItem);
			addItem.setOnAction(o -> {
				final JSONObject hero = generationState.getObj("Held");
				final JSONObject target = hero.getObj(type);
				final ProConOrSkill skill = row.getItem();
				final String name = skill.getName();
				final boolean hasChoice = skill.getProOrCon().containsKey("Auswahl");
				final boolean hasText = skill.getProOrCon().containsKey("Freitext");
				JSONObject actual;
				if (hasChoice || hasText) {
					actual = skill.getActual().clone(target.getArr(name));
					target.getArr(name).add(actual);
				} else {
					actual = skill.getActual().clone(target);
					target.put(name, actual);
				}
				actual.removeKey("temporary:suppressEffects");
				actual.put("temporary:Chosen", true);
				HeroUtil.applyEffect(hero, name, skill.getProOrCon(), skill.getActual());
				target.notifyListeners(listener);
				initializePossibleTable();
			});

			if ("Sonderfertigkeiten".equals(type)) {
				final MenuItem cheaperItem = new MenuItem("Verbilligen");
				possibleMenu.getItems().add(cheaperItem);
				cheaperItem.setOnAction(o -> {
					final JSONObject hero = generationState.getObj("Held");
					final JSONObject target = hero.getObj("Verbilligte Sonderfertigkeiten");
					final ProConOrSkill skill = row.getItem();
					final String name = skill.getName();
					final boolean hasChoice = skill.getProOrCon().containsKey("Auswahl");
					final boolean hasText = skill.getProOrCon().containsKey("Freitext");
					if (hasChoice || hasText) {
						final JSONObject actual = skill.getActual().clone(target.getArr(name));
						actual.put("temporary:Chosen", true);
						if (!skill.hasFixedChoice()) {
							skill.getActual().removeKey("Auswahl");
							skill.getActual().removeKey("temporary:SetChoice");
						}
						if (!skill.hasFixedText()) {
							skill.getActual().removeKey("Freitext");
							skill.getActual().removeKey("temporary:SetText");
						}
						target.getArr(name).add(actual);
					} else {
						final JSONObject actual = skill.getActual().clone(target);
						actual.put("temporary:Chosen", true);
						target.put(name, actual);
						allItems.remove(skill);
						skill.validProperty().removeListener(valid.containsKey(skill) ? valid.get(skill) : invalid.get(skill));
					}
					skill.getActual().removeKey("Stufe");
					target.notifyListeners(listener);
					initializePossibleTable();
				});
			}

			row.setContextMenu(possibleMenu);

			return row;
		});

		possibleValueColumn.setOnEditCommit(t -> {
			if (t.getRowValue() != null) {
				t.getRowValue().setValue(t.getNewValue());
			}
		});

		valid.addListener((final MapChangeListener.Change<?, ?> o) -> {
			if (parent != null) {
				final boolean nonEmpty = !valid.isEmpty();
				parent.setVisible(nonEmpty);
				parent.setManaged(nonEmpty);
			}
		});

		if (!"Vorteile".equals(type) && !"Nachteile".equals(type)) {
			possibleValueColumn.setVisible(false);
			possibleValueColumn.setMinWidth(0);
			possibleValueColumn.setMaxWidth(0);
		}

		possibleTable.setItems(new FilteredList<>(allItems, valid::containsKey));

		ProConSkillUtil.setupTable(type, possibleTable, possibleNameColumn, possibleDescColumn, possibleVariantColumn, possibleValueColumn, possibleValidColumn,
				possibleSuggestedColumn);

		possibleTable.getSortOrder().add(possibleNameColumn);

		showAll.addListener((o, oldV, newV) -> initializePossibleTable());
	}

	public void activate(final JSONObject hero, final JSONObject currentProsOrCons) {
		initializePossibleTable();
		currentProsOrCons.addListener(listener);
		if ("Sonderfertigkeiten".equals(type)) {
			hero.getObj("Verbilligte Sonderfertigkeiten").addListener(listener);
		}
	}

	public void deactivate(final JSONObject hero, final JSONObject currentProsOrCons) {
		currentProsOrCons.removeListener(listener);
		if ("Sonderfertigkeiten".equals(type)) {
			hero.getObj("Verbilligte Sonderfertigkeiten").removeListener(listener);
		}
	}

	public Node getControl() {
		return possibleTable;
	}

	private void initializePossibleTable() {
		final JSONObject hero = generationState.getObj("Held");
		final JSONObject currentProsOrCons = hero.getObj(type);

		for (final Map.Entry<ProConOrSkill, ChangeListener<Boolean>> item : valid.entrySet()) {
			item.getKey().validProperty().removeListener(item.getValue());
		}

		for (final Map.Entry<ProConOrSkill, ChangeListener<Boolean>> item : invalid.entrySet()) {
			item.getKey().validProperty().removeListener(item.getValue());
		}

		valid.clear();
		invalid.clear();
		allItems.clear();

		final boolean isSkills = "Sonderfertigkeiten".equals(type);
		final boolean isCheaper = "Verbilligte Sonderfertigkeiten".equals(type);

		JSONObject suggested = new JSONObject(null);
		final JSONObject actualInvalid = new JSONObject(null);

		if (isSkills) {
			suggested = hero.getObj("Verbilligte Sonderfertigkeiten");
		} else if (!isCheaper) {
			for (final String currentType : new String[] { "Rasse", "Kultur", "Profession" }) {
				final JSONObject current = generationState.getObj(currentType);
				final JSONObject currentSuggested = current.getObj("Empfohlene " + type);
				for (final String suggestedName : currentSuggested.keySet()) {
					final JSONValue cur = ((JSONValue) currentSuggested.getUnsafe(suggestedName)).clone(suggested);
					if (cur instanceof JSONObject) {
						suggested.put(suggestedName, (JSONObject) cur);
					} else {
						suggested.put(suggestedName, (JSONArray) cur);
					}
				}
				final JSONObject currentInvalid = current.getObj("Ungeeignete " + type);
				for (final String invalidName : currentInvalid.keySet()) {
					final JSONValue cur = ((JSONValue) currentInvalid.getUnsafe(invalidName)).clone(actualInvalid);
					if (cur instanceof JSONObject) {
						actualInvalid.put(invalidName, (JSONObject) cur);
					} else {
						actualInvalid.put(invalidName, (JSONArray) cur);
					}
				}
			}
		}

		for (final String name : possibleProsOrCons.keySet()) {
			if (name.startsWith("temporary:") || specialHandledProsAndCons.contains(name)) {
				continue;
			}
			final JSONObject proOrCon = possibleProsOrCons.getObj(name);
			if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
				boolean foundEmpty = false;
				if (suggested.containsKey(name)) {
					final JSONArray actual = suggested.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i).clone(null);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						current.put("temporary:suppressEffects", true);
						final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"),
								current.containsKey("Freitext"), isSkills, isCheaper, false, true);
						if (newItem.isValid() || showAll.get()) {
							valid.put(newItem, validListener(newItem));
						} else {
							invalid.put(newItem, validListener(newItem));
						}
						allItems.add(newItem);
					}
				}
				if (actualInvalid.containsKey(name)) {
					final JSONArray actual = actualInvalid.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i).clone(null);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						current.put("temporary:suppressEffects", true);
						final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"),
								current.containsKey("Freitext"), isSkills, isCheaper, true, false);
						if (showAll.get()) {
							valid.put(newItem, validListener(newItem));
							allItems.add(newItem);
						}
					}
				}
				if (!foundEmpty) {
					final JSONObject actual = new JSONObject(null);
					actual.put("temporary:suppressEffects", true);
					final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, actual, false, false, false, isSkills, isCheaper, false, false);
					if (newItem.isValid() || showAll.get()) {
						valid.put(newItem, validListener(newItem));
					} else {
						invalid.put(newItem, validListener(newItem));
					}
					allItems.add(newItem);
				}
			} else {
				final boolean isSuggested = suggested.containsKey(name);
				final boolean isInvalid = actualInvalid.containsKey(name);
				final JSONObject actual = new JSONObject(null);
				actual.put("temporary:suppressEffects", true);
				final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, actual, false, false, false, isSkills, isCheaper, isInvalid, isSuggested);
				if (!currentProsOrCons.containsKey(name)) {
					if (newItem.isValid() || showAll.get()) {
						valid.put(newItem, validListener(newItem));
						allItems.add(newItem);
					} else if (!isInvalid) {
						invalid.put(newItem, validListener(newItem));
						allItems.add(newItem);
					}
				}
			}
		}

		possibleTable.sort();
	}

	public void setParent(final Node parent) {
		this.parent = parent;
		parent.setVisible(!valid.isEmpty());
		parent.setManaged(!valid.isEmpty());
	}

	private ChangeListener<Boolean> validListener(final ProConOrSkill item) {
		final ChangeListener<Boolean> validListener = new ChangeListener<>() {
			@Override
			public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue, final Boolean newValue) {
				if (showAll.get() || newValue || item.getProOrCon().containsKey("Auswahl") || item.getProOrCon().containsKey("Freitext")) {
					valid.put(item, this);
					invalid.remove(item);
				} else {
					invalid.put(item, this);
					valid.remove(item);
				}
			}
		};
		item.validProperty().addListener(validListener);
		return validListener;
	}
}
