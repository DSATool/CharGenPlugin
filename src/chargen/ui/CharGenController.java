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
package chargen.ui;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import chargen.attributes.Attributes;
import chargen.biography.Biography;
import chargen.choices.Choices;
import chargen.pros_cons_skills.ProConSkillSelectors;
import chargen.race_culture_profession.RKPSelectors;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.resources.Settings;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class CharGenController {

	public static List<Class<? extends TabController>> tabControllers = new ArrayList<>(List.of(RKPSelectors.class, Attributes.class, Choices.class,
			ProConSkillSelectors.class, Biography.class));

	private final List<TabController> controllers = new ArrayList<>(tabControllers.size());

	@FXML
	private Node pane;
	@FXML
	private TabPane tabPane;
	@FXML
	private VBox leftBox;
	@FXML
	private Button next;
	@FXML
	private Button prev;
	@FXML
	private Button done;

	private JSONObject generationState;
	private int currentController;

	public CharGenController() {
		final FXMLLoader fxmlLoader = new FXMLLoader();
		fxmlLoader.setController(this);

		try {
			pane = fxmlLoader.load(getClass().getResource("CharGen.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		init();
	}

	private void activateController(final int index, final boolean isLast, final boolean forward) {
		final TabController controller = controllers.get(index);
		generationState.put("SelectedController", index);
		controller.activate(forward);
		if (isLast) {
			done.disableProperty().bind(controller.canContinue().not());
			next.disableProperty().unbind();
			next.setDisable(true);
		} else {
			next.disableProperty().bind(controller.canContinue().not());
			done.disableProperty().unbind();
			done.setDisable(true);
		}
		prev.setDisable(index == 0);
	}

	private void applySpecialPros(final JSONObject hero) {
		final JSONObject pros = hero.getObj("Vorteile");
		if (pros.containsKey("Begabung für Talent")) {
			final JSONArray pro = pros.getArr("Begabung für Talent");
			for (int i = 0; i < pro.size(); ++i) {
				final String talentName = pro.getObj(i).getString("Auswahl");
				final Tuple<JSONObject, String> talentAndGroup = HeroUtil.findTalent(talentName);
				final Object talent = hero.getObj("Talente").getObj(talentAndGroup._2).getUnsafe(talentName);
				if (talent instanceof JSONObject) {
					final int taw = ((JSONObject) talent).getIntOrDefault("TaW", 0);
					if (taw == 0 && !talentAndGroup._1.getBoolOrDefault("Basis", false)) {
						((JSONObject) talent).put("TaW", 0);
						((JSONObject) talent).removeKey("aktiviert");
					} else {
						((JSONObject) talent).put("TaW", taw + 1);
					}
				} else if (talent instanceof JSONArray) {
					for (int j = 0; j < ((JSONArray) talent).size(); ++j) {
						final JSONObject variant = ((JSONArray) talent).getObj(i);
						final int taw = variant.getIntOrDefault("TaW", 0);
						if (taw == 0 && !talentAndGroup._1.getBoolOrDefault("Basis", false)) {
							variant.put("TaW", 0);
							variant.removeKey("aktiviert");
						} else {
							variant.put("TaW", taw + 1);
						}
					}
				}
			}
		}
		if (pros.containsKey("Begabung für Zauber")) {
			final JSONArray pro = pros.getArr("Begabung für Zauber");
			for (int i = 0; i < pro.size(); ++i) {
				final String spellName = pro.getObj(i).getString("Auswahl");
				final JSONObject spell = (JSONObject) HeroUtil.findActualTalent(hero, spellName)._1;
				if (spell != null) {
					for (final String repName : spell.keySet()) {
						final Object rep = spell.getUnsafe(repName);
						if (rep instanceof JSONObject) {
							((JSONObject) rep).put("ZfW", ((JSONObject) rep).getIntOrDefault("ZfW", 0) + 1);
						} else if (rep instanceof JSONArray) {
							for (int j = 0; j < ((JSONArray) rep).size(); ++j) {
								final JSONObject variant = ((JSONArray) rep).getObj(i);
								variant.put("ZfW", variant.getIntOrDefault("ZfW", 0) + 1);
							}
						}
					}
				}
			}
		}
	}

	@FXML
	private void cancel() {
		final Alert alert = new Alert(AlertType.WARNING);
		alert.setTitle("Abbrechen");
		alert.setHeaderText("Dadurch werden alle bisherigen Eingaben verworfen.");
		alert.setContentText("Soll wirklich abgebrochen werden?");
		alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		final Optional<ButtonType> result = alert.showAndWait();
		if (result.isPresent() && result.get().equals(ButtonType.OK)) {
			generationState.clear();
			init();
		}
	}

	public Node getRoot() {
		return pane;
	}

	public void init() {
		controllers.clear();
		tabPane.getTabs().clear();
		leftBox.getChildren().clear();

		final Label spacer = new Label();
		spacer.setMaxHeight(Double.POSITIVE_INFINITY);
		leftBox.getChildren().add(spacer);
		VBox.setVgrow(spacer, Priority.SOMETIMES);

		generationState = ResourceManager.getResource("chargen/Status");

		// Ensure nice order of entries
		final JSONObject hero = generationState.getObj("Held");
		hero.put("Spieler", hero.getStringOrDefault("Spieler", ""));
		hero.getObj("Biografie");
		hero.getObj("Eigenschaften");
		hero.getObj("Basiswerte");
		hero.getObj("Vorteile");
		hero.getObj("Nachteile");
		hero.getObj("Sonderfertigkeiten");
		hero.getObj("Verbilligte Sonderfertigkeiten");
		hero.getObj("Talente");

		final IntegerProperty gp = new SimpleIntegerProperty(
				generationState.getIntOrDefault("GP", Settings.getSettingIntOrDefault(110, "Heldenerschaffung", "GP")));
		gp.addListener((o, oldV, newV) -> generationState.put("GP", newV.intValue()));

		currentController = generationState.getIntOrDefault("SelectedController", 0);

		leftBox.getChildren().add(new Label("GP:"));
		final Label gpLabel = new Label();
		gpLabel.textProperty().bind(gp.asString());
		leftBox.getChildren().add(gpLabel);

		try {
			for (final Class<? extends TabController> controller : tabControllers) {
				controllers.add(controller.getDeclaredConstructor(JSONObject.class, TabPane.class, VBox.class, IntegerProperty.class)
						.newInstance(generationState, tabPane, leftBox, gp));
			}
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		if (controllers.size() > currentController) {
			activateController(currentController, controllers.size() == currentController + 1, false);
		}
	}

	@FXML
	private void next() {
		if (currentController < controllers.size() - 1) {
			controllers.get(currentController).deactivate(true);
			++currentController;
			activateController(currentController, currentController == controllers.size() - 1, true);
		}
	}

	@FXML
	private void previous() {
		if (currentController > 0) {
			controllers.get(currentController).deactivate(false);
			--currentController;
			activateController(currentController, false, false);
		}
	}

	private void removeEmptyTalents(final JSONObject hero) {
		final JSONObject talents = hero.getObj("Talente");
		for (final String groupName : talents.keySet()) {
			final JSONObject group = talents.getObj(groupName);
			final List<String> toRemove = new LinkedList<>();
			for (final String talentName : group.keySet()) {
				final JSONValue talent = (JSONValue) group.getUnsafe(talentName);
				if (talent instanceof JSONArray) {
					for (int i = talent.size() - 1; i >= 0; --i) {
						final JSONObject actual = ((JSONArray) talent).getObj(i);
						if (actual.size() == 1 && !actual.getBoolOrDefault("aktiviert", true)) {
							((JSONArray) talent).removeAt(i);
						}
					}
					if (talent.size() == 0) {
						toRemove.add(talentName);
					}
				} else {
					if (talent.size() == 1 && !((JSONObject) talent).getBoolOrDefault("aktiviert", true)) {
						toRemove.add(talentName);
					}
				}
			}
			for (final String key : toRemove) {
				group.removeKey(key);
			}
		}

		final JSONObject spells = hero.getObjOrDefault("Zauber", null);
		if (spells == null) return;
		final List<String> spellsToRemove = new LinkedList<>();
		for (final String spellName : spells.keySet()) {
			final JSONObject spell = spells.getObj(spellName);
			final List<String> repsToRemove = new LinkedList<>();
			for (final String rep : spell.keySet()) {
				final JSONValue representation = (JSONValue) spell.getUnsafe(rep);
				if (representation instanceof JSONArray) {
					for (int i = representation.size() - 1; i >= 0; --i) {
						final JSONObject actual = ((JSONArray) representation).getObj(i);
						if (actual.size() == 1 && !actual.getBoolOrDefault("aktiviert", true)) {
							((JSONArray) representation).removeAt(i);
						}
					}
					if (representation.size() == 0) {
						repsToRemove.add(rep);
					}
				} else {
					if (representation.size() == 1 && !((JSONObject) representation).getBoolOrDefault("aktiviert", true)) {
						repsToRemove.add(rep);
					}
				}
			}
			for (final String key : repsToRemove) {
				spell.removeKey(key);
			}
			if (spell.size() == 0) {
				spellsToRemove.add(spellName);
			}
		}
		for (final String key : spellsToRemove) {
			spells.removeKey(key);
		}
	}

	private void removeTemporaries(final JSONArray current) {
		for (int i = 0; i < current.size(); ++i) {
			final Object value = current.getUnsafe(i);
			if (value instanceof String) {
				if (((String) value).startsWith("temporary")) {
					current.removeAt(i);
					--i;
				}
			} else if (value instanceof JSONObject) {
				removeTemporaries((JSONObject) value);
				if (((JSONObject) value).size() == 0) {
					current.removeAt(i);
					--i;
				}
			} else if (value instanceof JSONArray) {
				removeTemporaries((JSONArray) value);
				if (((JSONArray) value).size() == 0) {
					current.removeAt(i);
					--i;
				}
			}
		}
	}

	private void removeTemporaries(final JSONObject current) {
		final List<String> toRemove = new LinkedList<>();
		for (final String key : current.keySet()) {
			if (key.startsWith("temporary")) {
				toRemove.add(key);
			} else {
				final Object value = current.getUnsafe(key);
				if (value instanceof JSONObject) {
					removeTemporaries((JSONObject) value);
				} else if (value instanceof JSONArray) {
					removeTemporaries((JSONArray) value);
				}
			}
		}
		for (final String key : toRemove) {
			current.removeKey(key);
		}
	}

	@FXML
	private void saveHero() {
		controllers.get(controllers.size() - 1).deactivate(true);
		final JSONObject hero = generationState.getObj("Held");
		removeTemporaries(hero);
		removeEmptyTalents(hero);
		applySpecialPros(hero);
		setAP(hero);
		setMoney(hero);
		hero.getObj("Basiswerte").getObj("Geschwindigkeit").put("Wert", 8);
		ResourceManager.moveResource(hero.clone(null), "characters/" + hero.getObj("Biografie").getString("Vorname"));
		generationState.clear();
		init();
	}

	private void setAP(final JSONObject hero) {
		final JSONObject attributes = hero.getObj("Eigenschaften");
		int ap = (attributes.getObj("KL").getInt("Wert") + attributes.getObj("IN").getInt("Wert")) * 20;
		final JSONObject pros = hero.getObj("Vorteile");
		if (pros.containsKey("Gebildet")) {
			ap += pros.getObj("Gebildet").getInt("Stufe") * 40;
		}
		final JSONObject cons = hero.getObj("Nachteile");
		if (cons.containsKey("Ungebildet")) {
			ap -= cons.getObj("Ungebildet").getInt("Stufe") * 40;
		}
		if (generationState.containsKey("Breitgefächerte Bildung")) {
			final int gp = generationState.getObj("Breitgefächerte Bildung").getIntOrDefault("Kosten", 0)
					+ ResourceManager.getResource("data/Vorteile").getObj("Breitgefächerte Bildung").getIntOrDefault("Kosten", 7);
			ap += gp * 50;
		} else if (generationState.containsKey("Veteran")) {
			final int gp = generationState.getObj("Veteran").getIntOrDefault("Kosten", 0)
					+ ResourceManager.getResource("data/Vorteile").getObj("Veteran").getIntOrDefault("Kosten", 3);
			ap += gp * 50;
		}
		final JSONObject biography = hero.getObj("Biografie");
		biography.put("Abenteuerpunkte", ap);
		biography.put("Abenteuerpunkte-Guthaben", ap);
	}

	private void setMoney(final JSONObject hero) {
		final JSONObject basicValues = hero.getObj("Basiswerte");
		final int so = basicValues.getObj("Sozialstatus").getInt("Wert");
		;
		int startMoney = so * so;
		if (HeroUtil.isNoble(hero)) {
			startMoney *= 2;
		}
		final JSONObject pros = hero.getObj("Vorteile");
		if (pros.containsKey("Ausrüstungsvorteil")) {
			final int level = pros.getObj("Ausrüstungsvorteil").getIntOrDefault("Stufe", 0);
			startMoney += level * so * 20;
			if (HeroUtil.isNoble(hero) || pros.containsKey("Breitgefächerte Bildung") || pros.containsKey("Veteran")) {
				startMoney += level * so * 10;
			}
		}
		final JSONObject money = hero.getObj("Besitz").getObj("Geld");
		money.put("Dukaten", startMoney / 10);
		money.put("Silbertaler", startMoney % 10);
	}
}
