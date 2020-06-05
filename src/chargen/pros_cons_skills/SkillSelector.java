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
import dsatool.resources.ResourceManager;
import dsatool.util.ErrorLogger;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class SkillSelector extends ProConSkillSelector {

	/* This is the table for cheaper skills! */
	@FXML
	private TableView<ProConOrSkill> possibleTable;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleNameColumn;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleDescColumn;
	@FXML
	private TableColumn<ProConOrSkill, String> possibleVariantColumn;
	@FXML
	private TableColumn<ProConOrSkill, Integer> possibleCostColumn;
	@FXML
	private TableColumn<ProConOrSkill, Integer> possibleValueColumn;
	@FXML
	private TableColumn<ProConOrSkill, Boolean> possibleValidColumn;
	@FXML
	private TableColumn<ProConOrSkill, Boolean> possibleSuggestedColumn;

	@FXML
	private ScrollPane chosenPane;

	private final List<GroupSelector> selectors = new ArrayList<>();

	private int cheaperCost;

	protected final IntegerProperty cheaperPool = new SimpleIntegerProperty();

	public SkillSelector(final JSONObject generationState, final IntegerProperty gp, final String type, final BooleanProperty showAll) {
		super(generationState, gp, type, null, null);
		final VBox box = new VBox(2);
		box.setMaxWidth(Double.POSITIVE_INFINITY);
		box.setFillWidth(true);
		possiblePane.setContent(box);

		final JSONObject skills = ResourceManager.getResource("data/Sonderfertigkeiten");
		for (final String groupName : skills.keySet()) {
			final GroupSelector selector = new GroupSelector(generationState, type, skills.getObj(groupName), showAll, 0);
			final Node titledPane = new TitledPane(groupName, selector.getControl());
			selector.setParent(titledPane);
			box.getChildren().add(titledPane);
			selectors.add(selector);
		}

		final JSONObject liturgies = ResourceManager.getResource("data/Liturgien");
		final GroupSelector liturgiesSelector = new GroupSelector(generationState, type, liturgies, showAll, 0);
		final Node liturgiesTitledPane = new TitledPane("Liturgien", liturgiesSelector.getControl());
		liturgiesSelector.setParent(liturgiesTitledPane);
		box.getChildren().add(liturgiesTitledPane);
		selectors.add(liturgiesSelector);

		final JSONObject rituals = ResourceManager.getResource("data/Rituale");
		for (final String groupName : rituals.keySet()) {
			final GroupSelector selector = new GroupSelector(generationState, type, rituals.getObj(groupName), showAll, 0);
			final Node titledPane = new TitledPane(groupName, selector.getControl());
			selector.setParent(titledPane);
			box.getChildren().add(titledPane);
			selectors.add(selector);
		}

		chosenValueColumn.setVisible(false);
		DoubleBinding width = chosenTable.widthProperty().subtract(2);
		width = width.subtract(chosenDescColumn.widthProperty()).subtract(chosenVariantColumn.widthProperty()).subtract(chosenCostColumn.widthProperty());
		chosenNameColumn.prefWidthProperty().bind(width);

		chosenPane.setPrefHeight(50);

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("ProCon.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		possibleTable.setRowFactory(t -> {
			final TableRow<ProConOrSkill> row = new TableRow<>();

			final ContextMenu cheaperMenu = new ContextMenu();

			final MenuItem addItem = new MenuItem("Hinzufügen");
			cheaperMenu.getItems().add(addItem);
			addItem.setOnAction(o -> {
				final JSONObject hero = generationState.getObj("Held");
				final JSONObject target = hero.getObj("Sonderfertigkeiten");
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
				target.notifyListeners(null);
			});

			final MenuItem removeItem = new MenuItem("Entfernen");
			cheaperMenu.getItems().add(removeItem);
			removeItem.setOnAction(o -> {
				final JSONObject hero = generationState.getObj("Held");
				final JSONObject target = hero.getObj("Verbilligte Sonderfertigkeiten");
				final ProConOrSkill current = row.getItem();
				if (current.getProOrCon().containsKey("Auswahl") || current.getProOrCon().containsKey("Freitext")) {
					target.getArr(current.getName()).remove(current.getActual());
					if (target.getArr(current.getName()).size() == 0) {
						target.removeKey(current.getName());
					}
				} else {
					final JSONObject actual = target.getObj(current.getName());
					if (actual.containsKey("temporary:Cheaper")) {
						final JSONObject cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
						final JSONObject cheaper = new JSONObject(cheaperSkills);
						final int numCheaper = actual.getInt("temporary:Cheaper");
						if (numCheaper > 1) {
							cheaper.put("Verbilligungen", numCheaper);
						}
						cheaperSkills.put(current.getName(), cheaper);
					}
					target.removeKey(current.getName());
				}
				target.notifyListeners(null);
			});

			row.contextMenuProperty().bind(
					Bindings.when(Bindings.createBooleanBinding(() -> {
						final ProConOrSkill cheaperSkill = row.getItem();
						return cheaperSkill != null && !cheaperSkill.isFixed();
					}, row.itemProperty())).then(cheaperMenu).otherwise((ContextMenu) null));

			return row;
		});

		possibleValueColumn.setOnEditCommit(t -> t.getRowValue().setValue(t.getNewValue()));

		ProConSkillUtil.setupTable("Verbilligte Sonderfertigkeiten", 0, possibleTable, possibleNameColumn, possibleDescColumn, possibleVariantColumn,
				possibleValueColumn, possibleValidColumn, possibleSuggestedColumn);

		possibleValueColumn.setText("Anzahl");

		possibleTable.getSortOrder().add(possibleNameColumn);

		possibleValueColumn.setOnEditCommit(t -> {
			final ProConOrSkill current = t.getRowValue();
			current.setNumCheaper(t.getNewValue());
			setCost();
		});

		possibleValueColumn.setCellValueFactory(new PropertyValueFactory<>("numCheaper"));
		possibleCostColumn.setCellValueFactory(
				d -> new SimpleIntegerProperty((int) (d.getValue().getBaseCost() * (2 - 1 / Math.pow(2, d.getValue().getNumCheaper() - 1)))).asObject());

		final ScrollPane cheaperPane = new ScrollPane(possibleTable);
		cheaperPane.setHbarPolicy(ScrollBarPolicy.NEVER);
		cheaperPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
		cheaperPane.setFitToWidth(true);
		cheaperPane.setPrefHeight(50);
		VBox.setVgrow(cheaperPane, Priority.ALWAYS);

		pane.getChildren().add(cheaperPane);
	}

	@Override
	public void activate(final boolean forward) {
		super.activate(forward);
		final JSONObject currentProsOrCons = generationState.getObj("Held").getObj("Verbilligte Sonderfertigkeiten");

		currentProsOrCons.addListener(listener);

		cheaperCost = getCheaperCost();

		cheaperPool.set(currentProsOrCons.getIntOrDefault("temporary:Pool", 0));

		cheaperPool.set(cheaperPool.get() - cheaperCost);
	}

	@Override
	protected void activateGroupSelectors(final JSONObject hero, final JSONObject target) {
		for (final GroupSelector selector : selectors) {
			selector.activate(hero, target);
		}
	}

	@Override
	public void deactivate(final boolean forward) {
		super.deactivate(forward);
		if (!forward) {
			cheaperPool.set(cheaperPool.get() + cheaperCost);
		}
	}

	@Override
	protected void deactivateGroupSelectors(final JSONObject hero, final JSONObject target) {
		for (final GroupSelector selector : selectors) {
			selector.deactivate(hero, target);
		}
	}

	private int getCheaperCost() {
		int cost = 0;
		for (final ProConOrSkill cheaperSkill : possibleTable.getItems()) {
			final JSONObject actual = cheaperSkill.getActual();
			double current = 0;
			final int additional = actual.getIntOrDefault("temporary:AdditionalLevels", 0);
			final int numCheaper = cheaperSkill.getNumCheaper();
			if (actual.containsKey("temporary:Chosen")) {
				current = cheaperSkill.getBaseCost() * (2 - 1 / Math.pow(2, numCheaper - 1));
			} else {
				current = cheaperSkill.getBaseCost() * (1 / Math.pow(2, numCheaper - additional - 1) - 1 / Math.pow(2, numCheaper - 1));
			}
			cost += current;
		}
		return cost;
	}

	public IntegerProperty getCheaperPool() {
		return cheaperPool;
	}

	@Override
	protected void initializeChosenTable() {
		super.initializeChosenTable();

		final JSONObject hero = generationState.getObj("Held");
		final JSONObject currentProsOrCons = hero.getObj("Verbilligte Sonderfertigkeiten");
		final ObservableList<ProConOrSkill> items = possibleTable.getItems();
		items.clear();

		for (final String name : currentProsOrCons.keySet()) {
			if (name.startsWith("temporary:")) {
				continue;
			}
			final JSONObject proOrCon = HeroUtil.findProConOrSkill(name)._1;
			if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
				final JSONArray current = currentProsOrCons.getArr(name);
				for (int i = 0; i < current.size(); ++i) {
					final JSONObject actual = current.getObj(i);
					actual.put("temporary:suppressEffects", true);
					items.add(new ProConOrSkill(name, hero, proOrCon, actual, !actual.containsKey("temporary:Chosen"),
							actual.containsKey("Auswahl") && !actual.containsKey("temporary:SetChoice"),
							actual.containsKey("Freitext") && !actual.containsKey("temporary:SetText"), false, true, false, false));
				}
			} else {
				final JSONObject actual = currentProsOrCons.getObj(name);
				actual.put("temporary:suppressEffects", true);
				items.add(new ProConOrSkill(name, hero, proOrCon, actual, !actual.containsKey("temporary:Chosen"), false, false, false, true, false, false));
			}
		}

		possibleTable.sort();

		possibleTable.setMinHeight((items.size() + 1) * 28 + 1);
	}

	@Override
	public void setCost() {
		super.setCost();
		cheaperPool.set(cheaperPool.get() + cheaperCost);
		cheaperCost = getCheaperCost();
		cheaperPool.set(cheaperPool.get() - cheaperCost);
	}
}