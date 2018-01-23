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

import dsa41basis.util.HeroUtil;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import jsonant.event.JSONListener;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public abstract class ProConSkillSelector {

	@FXML
	protected VBox pane;

	@FXML
	protected ScrollPane possiblePane;

	@FXML
	protected TableView<ProConOrSkill> chosenTable;
	@FXML
	protected TableColumn<ProConOrSkill, String> chosenNameColumn;
	@FXML
	protected TableColumn<ProConOrSkill, String> chosenDescColumn;
	@FXML
	protected TableColumn<ProConOrSkill, String> chosenVariantColumn;
	@FXML
	protected TableColumn<ProConOrSkill, Integer> chosenValueColumn;
	@FXML
	protected TableColumn<ProConOrSkill, Integer> chosenCostColumn;
	@FXML
	protected TableColumn<ProConOrSkill, Boolean> chosenValidColumn;
	@FXML
	protected TableColumn<ProConOrSkill, Boolean> chosenSuggestedColumn;

	protected final JSONObject generationState;
	protected final IntegerProperty gp;
	protected final String type;

	protected final IntegerProperty pool = new SimpleIntegerProperty();

	private int currentCost;
	private int currentSECost;

	private final IntegerProperty conGP;
	private final IntegerProperty seGP;

	protected final JSONListener listener = o -> {
		initializeChosenTable();
		setCost();
	};

	public ProConSkillSelector(final JSONObject generationState, final IntegerProperty gp, final String type, final IntegerProperty conGP,
			final IntegerProperty seGP) {
		this.generationState = generationState;
		this.gp = gp;
		this.type = type;
		this.conGP = conGP;
		this.seGP = seGP;

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("ProConSkill.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		final ContextMenu chosenMenu = new ContextMenu();
		final MenuItem removeItem = new MenuItem("Entfernen");
		chosenMenu.getItems().add(removeItem);
		removeItem.setOnAction(o -> {
			final JSONObject hero = generationState.getObj("Held");
			final JSONObject target = hero.getObj(type);
			final ProConOrSkill current = chosenTable.getSelectionModel().getSelectedItem();
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

		chosenMenu.setOnShowing(e -> {
			final ProConOrSkill item = chosenTable.getSelectionModel().getSelectedItem();
			removeItem.setVisible(item != null && !item.isFixed());
		});

		chosenTable.setContextMenu(chosenMenu);

		chosenValueColumn.setOnEditCommit(t -> {
			final ProConOrSkill current = t.getRowValue();
			current.setValue(t.getNewValue());
			setCost();
		});

		ProConSkillUtil.setupTable(type, 2, chosenTable, chosenNameColumn, chosenDescColumn, chosenVariantColumn, chosenValueColumn, chosenValidColumn,
				chosenSuggestedColumn);

		chosenTable.getSortOrder().add(chosenNameColumn);
	}

	public void activate(final boolean forward) {
		final JSONObject hero = generationState.getObj("Held");
		final JSONObject currentProsOrCons = hero.getObj(type);

		currentProsOrCons.addListener(listener);

		if ("Sonderfertigkeiten".equals(type)) {
			hero.getObj("Verbilligte Sonderfertigkeiten").addListener(listener);
		}

		initializeChosenTable();
		activateGroupSelectors(hero, currentProsOrCons);

		final Tuple<Integer, Integer> cost = getCost();
		currentCost = cost._1;
		currentSECost = cost._2;

		pool.set(currentProsOrCons.getIntOrDefault("temporary:Pool", 0));

		incurCost(currentCost, currentSECost, false, forward);
	}

	protected abstract void activateGroupSelectors(JSONObject hero, JSONObject target);

	public void deactivate(final boolean forward) {
		if (!forward) {
			incurCost(currentCost, currentSECost, true, true);
		}
		final JSONObject hero = generationState.getObj("Held");
		hero.getObj(type).removeListener(listener);
		if ("Sonderfertigkeiten".equals(type)) {
			hero.getObj("Verbilligte Sonderfertigkeiten").removeListener(listener);
		}
		deactivateGroupSelectors(hero, hero.getObj(type));
	}

	protected abstract void deactivateGroupSelectors(JSONObject hero, JSONObject target);

	public Node getControl() {
		return pane;
	}

	private Tuple<Integer, Integer> getCost() {
		int cost = 0;
		int seCost = 0;
		for (final ProConOrSkill proOrCon : chosenTable.getItems()) {
			final JSONObject actual = proOrCon.getActual();
			double current = 0;
			final int additional = actual.getIntOrDefault("temporary:AdditionalLevels", 0);
			if (actual.containsKey("temporary:Chosen")) {
				current = proOrCon.getCost();
				current += handleBalanceSpecialCase(proOrCon, actual);
			} else {
				current += Math.round(proOrCon.getCost() / (double) proOrCon.getValue() * additional);
			}
			cost += current;
			if (proOrCon.getProOrCon().getBoolOrDefault("Schlechte Eigenschaft", false)) {
				seCost += current;
			}
		}
		return new Tuple<>(cost, seCost);
	}

	public IntegerProperty getPool() {
		return pool;
	}

	private int handleBalanceSpecialCase(final ProConOrSkill proOrCon, final JSONObject actual) {
		if ("Balance".equals(proOrCon.getName())) {
			final JSONObject pros = generationState.getObj("Held").getObj("Vorteile");
			if (pros.containsKey("Herausragende Balance")) return 0;
			final JSONObject skills = generationState.getObj("Held").getObj("Sonderfertigkeiten");
			if (skills.containsKey("Standfest")) {
				if (!skills.getObj("Standfest").containsKey("temporary:Chosen") && !skills.getObj("Standfest").containsKey("AutomatischDurch")) return -4;
			}
		} else if ("Herausragende Balance".equals(proOrCon.getName())) {
			final JSONObject skills = generationState.getObj("Held").getObj("Sonderfertigkeiten");
			if (skills.containsKey("Standfest")) {
				if (!skills.getObj("Standfest").containsKey("temporary:Chosen") && !skills.getObj("Standfest").containsKey("AutomatischDurch")) return -4;
			}

		}
		return 0;
	}

	protected void incurCost(final int cost, final int seCost, final boolean negative, final boolean updateGP) {
		final boolean negate = "Nachteile".equals(type);
		final boolean reduce = "Sonderfertigkeiten".equals(type);
		pool.set(pool.get() - (negative ? -1 : 1) * cost);
		if (pool.get() < 0) {
			final int value = (negate ? -1 : 1) * (int) Math.floor(pool.get() / (reduce ? 50.0 : 1));
			if (updateGP) {
				gp.set(gp.get() + value);
			}
			if (conGP != null) {
				conGP.set(conGP.get() + value);
				seGP.set(seGP.get() + seCost - (cost - value));
			}
			pool.set(0);
		} else {
			final int maxPool = generationState.getObj("Held").getObj(type).getIntOrDefault("temporary:Pool", 0);
			final int difference = maxPool - pool.get();
			if (difference < 0) {
				final int value = (negate ? -1 : 1) * (int) Math.floor(difference / (reduce ? 50.0 : 1));
				if (updateGP) {
					gp.set(gp.get() - value);
				}
				if (conGP != null) {
					conGP.set(conGP.get() - value);
					seGP.set(seGP.get() - seCost + cost - value);
				}
				pool.set(maxPool);
			}
		}
	}

	protected void initializeChosenTable() {
		final JSONObject hero = generationState.getObj("Held");
		final JSONObject currentProsOrCons = hero.getObj(type);
		final ObservableList<ProConOrSkill> items = chosenTable.getItems();
		items.clear();

		final boolean isSkills = "Sonderfertigkeiten".equals(type);

		for (final String name : currentProsOrCons.keySet()) {
			if (name.startsWith("temporary:")) {
				continue;
			}
			final JSONObject proOrCon = HeroUtil.findProConOrSkill(name)._1;
			if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
				final JSONArray current = currentProsOrCons.getArr(name);
				for (int i = 0; i < current.size(); ++i) {
					final JSONObject actual = current.getObj(i);
					items.add(new ProConOrSkill(name, hero, proOrCon, actual, !actual.containsKey("temporary:Chosen"),
							actual.containsKey("Auswahl") && !actual.containsKey("temporary:SetChoice"),
							actual.containsKey("Freitext") && !actual.containsKey("temporary:SetText"), isSkills, false, false, false, false));
				}
			} else {
				final JSONObject actual = currentProsOrCons.getObj(name);
				items.add(new ProConOrSkill(name, hero, proOrCon, actual, !actual.containsKey("temporary:Chosen"), false, false, isSkills, false, false,
						false, false));
			}
		}

		chosenTable.sort();

		chosenTable.setMinHeight((items.size() + 1) * 28 + 1);
	}

	public void setCost() {
		incurCost(currentCost, currentSECost, true, true);
		final Tuple<Integer, Integer> cost = getCost();
		currentCost = cost._1;
		currentSECost = cost._2;
		incurCost(currentCost, currentSECost, false, true);
	}
}
