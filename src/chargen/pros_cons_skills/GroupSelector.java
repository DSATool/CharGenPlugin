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

import java.util.ArrayList;
import java.util.List;

import dsa41basis.util.HeroUtil;
import dsatool.util.ErrorLogger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import jsonant.event.JSONListener;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

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
	private final List<ProConOrSkill> invalid = new ArrayList<>();
	private final List<ProConOrSkill> neverValid = new ArrayList<>();

	private final JSONListener listener = o -> {
		initializePossibleTable();
	};

	public GroupSelector(final JSONObject generationState, final String type, final ProConSkillSelector parent, final JSONObject possibleProsOrCons,
			final BooleanProperty showAll, final int additionalSpace) {
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

		final ContextMenu possibleMenu = new ContextMenu();

		final MenuItem addItem = new MenuItem("Hinzufügen");
		possibleMenu.getItems().add(addItem);
		addItem.setOnAction(o -> {
			final JSONObject hero = generationState.getObj("Held");
			final JSONObject target = hero.getObj(type);
			final ProConOrSkill skill = possibleTable.getSelectionModel().getSelectedItem();
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
			}
			skill.getActual().removeKey("Stufe");
			HeroUtil.applyEffect(hero, name, skill.getProOrCon(), skill.getActual());
			target.notifyListeners(null);
		});

		if ("Sonderfertigkeiten".equals(type)) {
			final MenuItem cheaperItem = new MenuItem("Verbilligen");
			possibleMenu.getItems().add(cheaperItem);
			cheaperItem.setOnAction(o -> {
				final JSONObject hero = generationState.getObj("Held");
				final JSONObject target = hero.getObj("Verbilligte Sonderfertigkeiten");
				final ProConOrSkill skill = possibleTable.getSelectionModel().getSelectedItem();
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
				}
				skill.getActual().removeKey("Stufe");
				target.notifyListeners(null);
			});
		}

		possibleTable.setContextMenu(possibleMenu);

		possibleValueColumn.setOnEditCommit(t -> t.getRowValue().setValue(t.getNewValue()));

		if (!"Vorteile".equals(type) && !"Nachteile".equals(type)) {
			possibleValueColumn.setVisible(false);
			possibleValueColumn.setMinWidth(0);
			possibleValueColumn.setMaxWidth(0);
		}

		ProConSkillUtil.setupTable(type, additionalSpace, possibleTable, possibleNameColumn, possibleDescColumn, possibleVariantColumn, possibleValueColumn,
				possibleValidColumn, possibleSuggestedColumn);

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
		final ObservableList<ProConOrSkill> items = possibleTable.getItems();
		items.clear();
		invalid.clear();
		neverValid.clear();

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
					final Object cur = currentSuggested.getUnsafe(suggestedName);
					if (cur instanceof JSONObject) {
						suggested.put(suggestedName, (JSONObject) cur);
					} else {
						suggested.put(suggestedName, (JSONArray) cur);
					}
				}
				final JSONObject currentInvalid = current.getObj("Ungeeignete " + type);
				for (final String invalidName : currentInvalid.keySet()) {
					final Object cur = currentInvalid.getUnsafe(invalidName);
					if (cur instanceof JSONObject) {
						actualInvalid.put(invalidName, (JSONObject) cur);
					} else {
						actualInvalid.put(invalidName, (JSONArray) cur);
					}
				}
			}
		}

		for (final String name : possibleProsOrCons.keySet()) {
			final JSONObject proOrCon = possibleProsOrCons.getObj(name);
			if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
				boolean foundEmpty = false;
				if (suggested.containsKey(name)) {
					final JSONArray actual = suggested.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"),
								current.containsKey("Freitext"), isSkills, isCheaper, false, true, false);
						if (newItem.isValid() || showAll.get()) {
							items.add(newItem);
						} else {
							invalid.add(newItem);
						}
					}
				}
				if (actualInvalid.containsKey(name)) {
					final JSONArray actual = actualInvalid.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"),
								current.containsKey("Freitext"), isSkills, isCheaper, true, false, false);
						if (showAll.get()) {
							items.add(newItem);
						} else {
							neverValid.add(newItem);
						}
					}
				}
				if (!foundEmpty) {
					final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, new JSONObject(null), false, false, false, isSkills, isCheaper, false,
							false, false);
					if (newItem.isValid() || showAll.get()) {
						items.add(newItem);
					} else {
						invalid.add(newItem);
					}
				}
			} else if (!currentProsOrCons.containsKey(name)) {
				final boolean isSuggested = suggested.containsKey(name);
				final boolean isInvalid = actualInvalid.containsKey(name);
				final ProConOrSkill newItem = new ProConOrSkill(name, hero, proOrCon, new JSONObject(null), false, false, false, isSkills, isCheaper, isInvalid,
						isSuggested, false);
				if (newItem.isValid() || showAll.get()) {
					items.add(newItem);
				} else if (isInvalid) {
					neverValid.add(newItem);
				} else {
					invalid.add(newItem);
				}
			}
		}

		possibleTable.sort();

		possibleTable.setMinHeight(items.size() * 28 + 26);
		possibleTable.setMaxHeight(items.size() * 28 + 26);

		if (parent != null) {
			parent.setVisible(!possibleTable.getItems().isEmpty());
			parent.setManaged(!possibleTable.getItems().isEmpty());
		}

		for (final ProConOrSkill item : possibleTable.getItems()) {
			registerInvalidListener(item);
		}

		for (final ProConOrSkill item : invalid) {
			registerValidListener(item);
		}
	}

	private void registerInvalidListener(final ProConOrSkill item) {
		final ChangeListener<Boolean> invalidListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue, final Boolean newValue) {
				if (!newValue && !showAll.get()) {
					observable.removeListener(this);

					possibleTable.setMinHeight(possibleTable.getItems().size() * 28 - 2);
					possibleTable.setMaxHeight(possibleTable.getItems().size() * 28 - 2);

					possibleTable.getItems().remove(item);

					possibleTable.sort();

					if (parent != null) {
						parent.setVisible(!possibleTable.getItems().isEmpty());
						parent.setManaged(!possibleTable.getItems().isEmpty());
					}
					invalid.add(item);

					registerValidListener(item);
				}
			}
		};
		item.validProperty().addListener(invalidListener);
	}

	private void registerValidListener(final ProConOrSkill item) {
		final ChangeListener<Boolean> validListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue, final Boolean newValue) {
				if (newValue) {
					observable.removeListener(this);

					possibleTable.setMinHeight(possibleTable.getItems().size() * 28 + 54);
					possibleTable.setMaxHeight(possibleTable.getItems().size() * 28 + 54);

					possibleTable.getItems().add(item);

					possibleTable.sort();

					if (parent != null) {
						parent.setVisible(true);
						parent.setManaged(true);
					}
					invalid.remove(item);

					registerInvalidListener(item);
				}
			}
		};
		item.validProperty().addListener(validListener);
	}

	public void setParent(final Node parent) {
		this.parent = parent;
	}
}
