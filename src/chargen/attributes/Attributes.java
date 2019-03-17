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
package chargen.attributes;

import java.util.HashMap;
import java.util.Map;

import chargen.ui.TabController;
import dsatool.resources.ResourceManager;
import dsatool.resources.Settings;
import dsatool.ui.ReactiveSpinner;
import dsatool.util.Util;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class Attributes extends TabController {

	private final GridPane grid = new GridPane();
	private final Label infoLabel = new Label();
	private final Tab tab;
	private final VBox leftBox;

	private final IntegerProperty attributesGP = new SimpleIntegerProperty();

	private final Map<String, Integer> extremes = new HashMap<>();

	public Attributes(final JSONObject generationState, final TabPane tabPane, final VBox leftBox, final IntegerProperty gp) {
		super(generationState, gp);
		this.leftBox = leftBox;

		final BorderPane pane = new BorderPane();
		pane.setCenter(grid);
		pane.setBottom(infoLabel);

		tab = addTab(tabPane, "Eigenschaften", pane);

		grid.setAlignment(Pos.CENTER);
		grid.setHgap(5);
		grid.setVgap(5);

		infoLabel.setPrefHeight(160);
	}

	@Override
	public void activate(final boolean forward) {
		tab.setDisable(false);
		tab.getTabPane().getSelectionModel().select(tab);

		final int maxGP = Settings.getSettingIntOrDefault(100, "Heldenerschaffung", "GP für Eigenschaften");
		attributesGP.set(0);

		final ObservableList<Node> items = leftBox.getChildren();
		items.add(1, new Label("GP in Eigenchaften: "));
		final Label GPLabel = new Label();
		GPLabel.textProperty().bind(attributesGP.asString().concat("/" + maxGP));
		items.add(2, GPLabel);

		grid.getChildren().clear();
		grid.add(new Label("Eigenschaft"), 0, 0);
		grid.add(new Label("Mod."), 1, 0);
		grid.add(new Label("Min."), 2, 0);
		grid.add(new Label("Max."), 3, 0);
		grid.add(new Label("Wert"), 4, 0);

		final JSONObject pros = ResourceManager.getResource("data/Vorteile");
		final JSONObject cons = ResourceManager.getResource("data/Nachteile");
		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		final JSONObject hero = generationState.getObj("Held");
		final JSONObject actualPros = hero.getObj("Vorteile");
		final JSONObject actualCons = hero.getObj("Nachteile");
		final JSONObject actualAttributes = hero.getObj("Eigenschaften");

		final int min = Settings.getSettingIntOrDefault(8, "Heldenerschaffung", "Minimum für Eigenschaften");
		final int max = Settings.getSettingIntOrDefault(14, "Heldenerschaffung", "Maximum für Eigenschaften");

		final JSONObject mods = getMods();
		final JSONObject mins = getMins();

		int i = 1;
		for (final String attributeName : attributes.keySet()) {
			final int curMod = mods.getIntOrDefault(attributeName, 0);
			final int curMin = min + curMod;
			final int curMax = max + curMod;
			final int requirementsMin = mins.getIntOrDefault(attributeName, 0);
			grid.add(new Label(attributeName), 0, i);
			grid.add(new Label(Util.getSignedIntegerString(curMod)), 1, i);
			grid.add(new Label(mins.getIntOrDefault(attributeName, curMin).toString()), 2, i);
			grid.add(new Label(Integer.toString(curMax)), 3, i);
			final JSONObject actualAttribute = actualAttributes.getObj(attributeName);
			final int toChoose = Math.max(actualAttribute.getIntOrDefault("Wert", 0), Math.max(curMin, requirementsMin));
			final ReactiveSpinner<Integer> attributeSpinner = new ReactiveSpinner<>(Math.max(requirementsMin, curMin - 1), 99, toChoose);
			final JSONObject attribute = attributes.getObj(attributeName);
			final int cost = getCost(curMod, toChoose, Integer.MIN_VALUE, curMax, pros, cons, actualPros, actualCons, attribute);
			gp.set(gp.get() - cost + actualAttribute.getIntOrDefault("temporary:GP", 0));
			attributesGP.set(attributesGP.get() + Math.min(toChoose, curMax) - curMod);
			actualAttribute.put("temporary:GP", cost);
			actualAttribute.put("Wert", toChoose);
			actualAttribute.put("Start", toChoose);
			if (toChoose < curMin) {
				extremes.put(attributeName, -1);
			} else if (toChoose > curMax) {
				extremes.put(attributeName, toChoose - curMax);
			}
			attributeSpinner.valueProperty().addListener((o, oldV, newV) -> {
				if (newV < curMin) {
					extremes.put(attributeName, -1);
				} else if (newV > curMax) {
					extremes.put(attributeName, newV - curMax);
				} else {
					extremes.remove(attributeName);
				}
				updateInfo();
				final int difference = getCost(oldV, newV, curMin, curMax, pros, cons, actualPros, actualCons, attribute);
				gp.set(gp.get() - difference);
				attributesGP.set(attributesGP.get() + Math.min(newV, curMax) - Math.min(oldV, curMax));
				actualAttribute.put("temporary:GP", actualAttribute.getIntOrDefault("temporary:GP", 0) + difference);
				actualAttribute.put("Wert", newV);
				actualAttribute.put("Start", newV);
			});
			grid.add(attributeSpinner, 4, i);
			++i;
		}
		updateInfo();

		final int soMin = getSOMin();
		final int soMax = getSOMax();

		grid.add(new Label("Sozialstatus"), 0, i);
		grid.add(new Label(""), 1, i);
		grid.add(new Label(Integer.toString(soMin)), 2, i);
		grid.add(new Label(Integer.toString(soMax)), 3, i);
		final ReactiveSpinner<Integer> soSpinner = new ReactiveSpinner<>(soMin, soMax);
		final JSONObject so = hero.getObj("Basiswerte").getObj("Sozialstatus");
		final int currentSO = Math.min(so.getIntOrDefault("Wert", soMin), soMax);
		gp.set(gp.get() - currentSO + soMin + so.getIntOrDefault("temporary:GP", 0));
		so.put("temporary:GP", currentSO - soMin);
		so.put("Wert", currentSO);
		soSpinner.valueProperty().addListener((o, oldV, newV) -> {
			gp.set(gp.get() + oldV - newV);
			so.put("temporary:GP", so.getIntOrDefault("temporary:GP", 0) + newV - oldV);
			so.put("Wert", newV);
		});
		grid.add(soSpinner, 4, i);

		canContinue.bind(attributesGP.lessThanOrEqualTo(maxGP));
	}

	@Override
	public void deactivate(final boolean forward) {
		tab.setDisable(true);

		leftBox.getChildren().remove(1, 3);
	}

	private int getCost(int oldValue, int newValue, final int min, final int max, final JSONObject pros, final JSONObject cons, final JSONObject actualPros,
			final JSONObject actualCons, final JSONObject attribute) {
		int difference = 0;
		if (oldValue == min - 1) {
			final String badAttribute = attribute.getString("Miserable Eigenschaft");
			difference += cons.getObj(badAttribute).getIntOrDefault("Kosten", 1);
			actualCons.removeKey(badAttribute);
			oldValue = min;
		}
		if (newValue == min - 1) {
			final String badAttribute = attribute.getString("Miserable Eigenschaft");
			difference -= cons.getObj(badAttribute).getIntOrDefault("Kosten", 1);
			actualCons.put(badAttribute, new JSONObject(actualCons));
			newValue = min;
		}
		if (oldValue > max) {
			final String goodAttribute = attribute.getString("Herausragende Eigenschaft");
			final int n = oldValue - max;
			difference -= (pros.getObj(goodAttribute).getIntOrDefault("Kosten", 1) + n - 1) * n;
			actualPros.removeKey(goodAttribute);
		}
		if (newValue > max) {
			final String goodAttribute = attribute.getString("Herausragende Eigenschaft");
			final int n = newValue - max;
			difference += (pros.getObj(goodAttribute).getIntOrDefault("Kosten", 1) + n - 1) * n;
			final JSONObject newPro = new JSONObject(actualPros);
			newPro.put("Stufe", n);
			actualPros.put(goodAttribute, newPro);
		}
		difference += newValue - oldValue;

		return difference;
	}

	private JSONObject getMins() {
		final JSONObject result = new JSONObject(null);
		for (final String current : new String[] { "Rasse", "Kultur", "Profession", "Breitgefächerte Bildung", "Veteran" }) {
			if (!generationState.containsKey(current)) {
				continue;
			}
			final JSONObject prerequisites = generationState.getObj(current).getObj("Voraussetzungen");
			final JSONObject attributes = prerequisites.getObj("Eigenschaften");
			for (final String attribute : attributes.keySet()) {
				result.put(attribute, Math.max(result.getIntOrDefault(attribute, 0), attributes.getInt(attribute)));
			}
			if (prerequisites.containsKey("Wahl")) {
				final Map<String, Integer> fromChoice = new HashMap<>();
				final JSONArray choices = prerequisites.getArr("Wahl");
				for (int i = 0; i < choices.size(); ++i) {
					final JSONObject choice = choices.getObj(i);
					if (choice.containsKey("Eigenschaften")) {
						final JSONObject choiceAttributes = choice.getObj("Eigenschaften");
						for (final String attribute : choiceAttributes.keySet()) {
							fromChoice.put(attribute, Math.min(fromChoice.getOrDefault(attribute, Integer.MAX_VALUE), choiceAttributes.getInt(attribute)));
						}
					}
				}
				for (final String attribute : fromChoice.keySet()) {
					result.put(attribute, Math.max(result.getIntOrDefault(attribute, 0), fromChoice.get(attribute)));
				}
			}
		}
		return result;
	}

	private JSONObject getMods() {
		final JSONObject result = new JSONObject(null);
		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject attributes = generationState.getObj(current).getObj("Eigenschaften");
			for (final String attribute : attributes.keySet()) {
				result.put(attribute, result.getIntOrDefault(attribute, 0) + attributes.getInt(attribute));
			}
		}
		return result;
	}

	private int getSOMax() {
		int result = 21;
		for (final String current : new String[] { "Rasse", "Kultur", "Profession", "Breitgefächerte Bildung" }) {
			if (!generationState.containsKey(current)) {
				continue;
			}
			result = Math.min(result, generationState.getObj(current).getIntOrDefault("Sozialstatus:Maximum", 13));
		}
		return result;
	}

	private int getSOMin() {
		int result = 1;
		for (final String current : new String[] { "Rasse", "Kultur", "Profession", "Breitgefächerte Bildung" }) {
			if (!generationState.containsKey(current)) {
				continue;
			}
			result = Math.max(result, generationState.getObj(current).getObj("Basiswerte").getIntOrDefault("Sozialstatus", 1));
		}
		return result;
	}

	private void updateInfo() {
		final StringBuilder newInfo = new StringBuilder("\n");
		final JSONObject attributes = ResourceManager.getResource("data/Eigenschaften");
		for (final String attributeName : attributes.keySet()) {
			if (extremes.containsKey(attributeName)) {
				final JSONObject attribute = attributes.getObj(attributeName);
				final int val = extremes.get(attributeName);
				if (val < 0) {
					newInfo.append(attributeName);
					newInfo.append(" zu niedrig: Erhalte ");
					newInfo.append(attribute.getString("Miserable Eigenschaft"));
					newInfo.append('\n');
				} else if (val > 0) {
					newInfo.append(attributeName);
					newInfo.append(" zu hoch: Erhalte ");
					newInfo.append(val);
					newInfo.append(" Stufe");
					if (val > 1) {
						newInfo.append('n');
					}
					newInfo.append(' ');
					newInfo.append(attribute.getString("Herausragende Eigenschaft"));
					newInfo.append('\n');
				}
			}
		}
		newInfo.deleteCharAt(newInfo.length() - 1);
		infoLabel.setText(newInfo.toString());
	}
}
