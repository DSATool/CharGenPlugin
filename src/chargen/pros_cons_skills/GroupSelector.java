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

import chargen.util.ChargenUtil;
import dsa41basis.util.HeroUtil;
import dsa41basis.util.RequirementsUtil;
import dsatool.util.ErrorLogger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
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

	private final JSONListener listener = o -> {
		initializePossibleTable();
	};

	public GroupSelector(final JSONObject generationState, final String type, final ProConSkillSelector parent, final JSONObject possibleProsOrCons,
			final int additionalSpace) {
		this.generationState = generationState;
		this.type = type;
		this.possibleProsOrCons = possibleProsOrCons;

		final boolean isCheaperSkills = "Verbilligte Sonderfertigkeiten".equals(type);

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
			if (!isCheaperSkills) {
				HeroUtil.applyEffect(hero, name, skill.getProOrCon(), skill.getActual());
			}
			target.notifyListeners(null);
		});
		possibleTable.setContextMenu(possibleMenu);

		possibleValueColumn.setOnEditCommit(t -> t.getTableView().getItems().get(t.getTablePosition().getRow()).setValue(t.getNewValue()));

		ProConSkillUtil.setupTable(type, additionalSpace, possibleTable, possibleNameColumn, possibleDescColumn, possibleVariantColumn, possibleValueColumn,
				possibleValidColumn, possibleSuggestedColumn);
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
		final ObservableList<ProConOrSkill> items = FXCollections.observableArrayList();
		final SortedList<ProConOrSkill> sorted = new SortedList<>(items);
		sorted.setComparator((l, r) -> ChargenUtil.comparator.compare(l.getName(), r.getName()));

		possibleTable.setItems(sorted);

		final boolean isSkills = "Sonderfertigkeiten".equals(type);
		final boolean isCheaper = "Verbilligte Sonderfertigkeiten".equals(type);

		JSONObject suggested = new JSONObject(null);
		final JSONObject invalid = new JSONObject(null);

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
						invalid.put(invalidName, (JSONObject) cur);
					} else {
						invalid.put(invalidName, (JSONArray) cur);
					}
				}
			}
		}

		for (final String name : possibleProsOrCons.keySet()) {
			final JSONObject proOrCon = possibleProsOrCons.getObj(name);
			if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
				boolean foundEmpty = false;
				final boolean isValid = isCheaper
						|| RequirementsUtil.isRequirementFulfilled(hero, proOrCon.getObjOrDefault("Voraussetzungen", null), null, null)
								&& (isSkills ? proOrCon.getIntOrDefault("Verbreitung", 1) > 3 : true);
				if (suggested.containsKey(name)) {
					final JSONArray actual = suggested.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						items.add(new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"), current.containsKey("Freitext"),
								isValid, true, false));
					}
				}
				if (invalid.containsKey(name)) {
					final JSONArray actual = invalid.getArr(name);
					for (int i = 0; i < actual.size(); ++i) {
						final JSONObject current = actual.getObj(i);
						if (!current.containsKey("Auswahl") && !current.containsKey("Freitext")) {
							foundEmpty = true;
						}
						items.add(new ProConOrSkill(name, hero, proOrCon, current, false, current.containsKey("Auswahl"), current.containsKey("Freitext"),
								false, false, false));
					}
				}
				if (!foundEmpty) {
					items.add(new ProConOrSkill(name, hero, proOrCon, new JSONObject(null), false, false, false, isValid, false, false));
				}
			} else if (!currentProsOrCons.containsKey(name)) {
				final boolean isValid = isCheaper
						|| RequirementsUtil.isRequirementFulfilled(hero, proOrCon.getObjOrDefault("Voraussetzungen", null), null, null)
								&& !invalid.containsKey(name) && (isSkills ? proOrCon.getIntOrDefault("Verbreitung", 1) > 3 : true);
				final boolean isSuggested = suggested.containsKey(name);
				items.add(new ProConOrSkill(name, hero, proOrCon, new JSONObject(null), false, false, false, isValid, isSuggested, false));
			}
		}

		possibleTable.setMinHeight((items.size() + 1) * 28 + 1);
		possibleTable.setMaxHeight((items.size() + 1) * 28 + 1);
	}
}
