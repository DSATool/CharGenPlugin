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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import chargen.race_culture_profession.BGBVeteranSelector.BGBVeteran;
import chargen.ui.TabController;
import chargen.util.ChargenUtil;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.util.Tuple;
import dsatool.util.Tuple3;
import dsatool.util.Tuple5;
import javafx.beans.property.IntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class RKPSelectors extends TabController {

	private static final JSONObject dummyObj = new JSONObject(null);
	private static final JSONArray dummyArr = new JSONArray(null);

	public static RadioButton male;
	public static RadioButton female;

	private static String basicValueKey(final String value) {
		return switch (value) {
			case "Sozialstatus", "Geschwindigkeit" -> "Wert";
			case "Karmaenergie" -> "Permanent";
			default -> "Modifikator";
		};
	}

	static Integer getInt(final RKP source, final String name, final Integer defaultValue) {
		return source.data.getIntOrDefault(name, source.parent != null ? getInt(source.parent, name, defaultValue) : defaultValue);
	}

	private static List<String> getModifications(RKP source) {
		final List<String> result = new LinkedList<>();
		while (source != null) {
			if (result.isEmpty() || !source.name.equals(result.get(0))) {
				result.add(0, source.name);
			}
			source = source.parent;
		}
		result.remove(0);
		return result;
	}

	static String getName(RKP source) {
		while (source.parent != null) {
			source = source.parent;
		}
		return source.name;
	}

	private final VBox leftBox;

	private Label raceLabel;
	private Label cultureLabel;
	private Label professionLabel;
	private Label bgbVeteranLabel;
	private Label bgbVeteranProfessionLabel;

	private final RKPSelector raceSelector;
	private final RKPSelector cultureSelector;
	private final RKPSelector professionSelector;
	private final BGBVeteranSelector bgbVeteranSelector;

	private boolean changedRace;
	private boolean changedCulture;
	private boolean changedProfession;
	private boolean changedBgbVeteran;

	private final Tab raceTab;
	private final Tab cultureTab;
	private final Tab professionTab;
	private final Tab bgbVeteranTab;

	private int raceCost = 0;
	private int cultureCost = 0;
	private int professionCost = 0;
	private int bgbVeteranCost = 0;

	private final List<Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>> specialCases = new ArrayList<>(
			List.of(
					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"Geode", "Rasse",
							hero -> "Geode".equals(hero.getObj("Biografie").getString("Profession")),
							race -> {
								race.getObj("Vorteile").removeKey("Schwer zu verzaubern");
								race.getObj("Nachteile").removeKey("Goldgier");
							},
							(race, unalteredRace) -> {
								final JSONObject unalteredPros = unalteredRace.getObj("Vorteile");
								if (unalteredPros.containsKey("Schwer zu verzaubern")) {
									final JSONObject pros = race.getObj("Vorteile");
									pros.put("Schwer zu verzaubern", unalteredPros.getObj("Schwer zu verzaubern").clone(pros));
								}
								final JSONObject unalteredCons = unalteredRace.getObj("Nachteile");
								if (unalteredCons.containsKey("Goldgier")) {
									final JSONObject cons = race.getObj("Nachteile");
									cons.put("Goldgier", unalteredPros.getObj("Goldgier").clone(cons));
								}
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"ZwergischerMagier", "Rasse",
							hero -> "Zwerg".equals(hero.getObj("Biografie").getString("Rasse"))
									&& "Magier".equals(hero.getObj("Biografie").getString("Profession")),
							race -> {
								final JSONObject pros = race.getObj("Vorteile");
								pros.removeKey("Schwer zu verzaubern");
								pros.put("Eisenaffine Aura", new JSONObject(pros));
								final JSONObject mr = pros.getObjOrDefault("Hohe Magieresistenz", new JSONObject(pros));
								mr.put("Stufe", mr.getIntOrDefault("Stufe", 0) + 3);
								pros.put("Hohe Magieresistenz", mr);

								final JSONObject cons = race.getObj("Nachteile");
								JSONArray choices;
								if (cons.containsKey("Wahl")) {
									choices = cons.getArr("Wahl");
									if (choices.getObjs().stream().anyMatch(choice -> choice.containsKey("Unfähigkeit für Merkmal"))) return;
								} else {
									choices = new JSONArray(cons);
									cons.put("Wahl", choices);
								}
								final JSONObject choice = new JSONObject(choices);
								final JSONArray traits = new JSONArray(choice);
								for (final String traitName : new String[] { "Eigenschaften", "Einfluss", "Form", "Heilung", "Hellsicht", "Herrschaft" }) {
									final JSONObject trait = new JSONObject(traits);
									trait.put("Auswahl", traitName);
									traits.add(trait);
								}
								choice.put("Unfähigkeit für Merkmal", traits);
								choices.add(choice);
								choices.add(choice.clone(choices));
							},
							(race, unalteredRace) -> {
								final JSONObject unalteredPros = unalteredRace.getObj("Vorteile");
								final JSONObject pros = generationState.getObj("Rasse").getObj("Vorteile");
								if (unalteredPros.containsKey("Schwer zu verzaubern")) {
									pros.put("Schwer zu verzaubern", unalteredPros.getObj("Schwer zu verzaubern").clone(pros));
								}
								if (!unalteredPros.containsKey("Eisenaffine Aura")) {
									pros.removeKey("Eisenaffine Aura");
								}
								if (unalteredPros.containsKey("Hohe Magieresistenz")) {
									pros.put("Hohe Magieresistenz", unalteredPros.getObj("Hohe Magieresistenz").clone(pros));
								} else {
									pros.removeKey("Hohe Magieresistenz");
								}

								final JSONArray choices = race.getObj("Nachteile").getArr("Wahl");
								final JSONObject choice = new JSONObject(choices);
								final JSONArray traits = new JSONArray(choice);
								for (final String traitName : new String[] { "Eigenschaften", "Einfluss", "Form", "Heilung", "Hellsicht", "Herrschaft" }) {
									final JSONObject trait = new JSONObject(traits);
									trait.put("Auswahl", traitName);
									traits.add(trait);
								}
								choices.remove(choice);
								choices.remove(choice);
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"TocamuyacSchamane", "Kultur",
							hero -> "Medizinmann".equals(hero.getObj("Biografie").getString("Profession"))
									&& hero.getObj("Biografie").getArr("Profession:Modifikation").contains("Tocamuyac")
									&& "Tocamuyac".equals(hero.getObj("Biografie").getString("Kultur")),
							culture -> {
								culture.getObj("Vorteile").removeKey("Richtungssinn");
							},
							(culture, unalteredCulture) -> {
								final JSONObject unalteredPros = unalteredCulture.getObj("Vorteile");
								if (unalteredPros.containsKey("Richtungssinn")) {
									final JSONObject pros = culture.getObj("Vorteile");
									pros.put("Richtungssinn", unalteredPros.getObj("Richtungssinn").clone(pros));
								}
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"SchamaneMitTotenangst", "Kultur",
							hero -> "Medizinmann".equals(hero.getObj("Biografie").getString("Profession"))
									&& hero.getObj("Biografie").getArr("Profession:Modifikation").contains("Waldinsel-Utulus")
									&& "Waldinsel-Utulus".equals(hero.getObj("Biografie").getString("Kultur"))
									|| "Kasknus".equals(hero.getObj("Biografie").getString("Profession"))
											&& "Nivesenstämme".equals(hero.getObj("Biografie").getString("Kultur"))
									|| "Brenoch-Dûn".equals(hero.getObj("Biografie").getString("Profession"))
											&& "Gjalskerland".equals(hero.getObj("Biografie").getString("Kultur")),
							culture -> {
								culture.getObj("Nachteile").removeKey("Totenangst");
							},
							(culture, unalteredCulture) -> {
								final JSONObject unalteredCons = unalteredCulture.getObj("Nachteile");
								if (unalteredCons.containsKey("Totenangst")) {
									final JSONObject cons = culture.getObj("Nachteile");
									cons.put("Totenangst", unalteredCons.getObj("Totenangst").clone(cons));
								}
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"DarnaSchamane", "Kultur",
							hero -> "Medizinmann".equals(hero.getObj("Biografie").getString("Profession"))
									&& hero.getObj("Biografie").getArr("Profession:Modifikation").contains("Darna")
									&& "Darna".equals(hero.getObj("Biografie").getString("Kultur")),
							culture -> {
								culture.getObj("Vorteile").removeKey("Viertelzauberer");
							},
							(culture, unalteredCulture) -> {
								final JSONObject unalteredPros = unalteredCulture.getObj("Vorteile");
								if (unalteredPros.containsKey("Viertelzauberer")) {
									final JSONObject pros = culture.getObj("Vorteile");
									pros.put("Viertelzauberer", unalteredPros.getObj("Viertelzauberer").clone(pros));
								}
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"GoblinSchamamin", "Rasse",
							hero -> "Goblin-Schamanin".equals(hero.getObj("Biografie").getString("Profession"))
									&& "Goblin".equals(hero.getObj("Biografie").getString("Rasse")),
							race -> {
								race.getObj("Nachteile").removeKey("Unstet");
							},
							(race, unalteredRace) -> {
								final JSONObject unalteredCons = unalteredRace.getObj("Nachteile");
								if (unalteredCons.containsKey("Unstet")) {
									final JSONObject cons = race.getObj("Nachteile");
									cons.put("Unstet", unalteredCons.getObj("Unstet").clone(cons));
								}
							}),

					new Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>>(
							"ElfischeSiedlungMitNichtmagischerProfession", "Profession",
							hero -> {
								if (!"Elfische Siedlung".equals(hero.getObj("Biografie").getString("Kultur"))) return false;
								final JSONObject pros = generationState.getObj("Profession").getObj("Vorteile");
								if (pros.containsKey("Vollzauberer") || pros.containsKey("Halbzauberer") || pros.containsKey("Viertelzauberer")) return false;
								JSONObject additionalPros = null;
								if (generationState.containsKey("Breitgefächerte Bildung")) {
									additionalPros = generationState.getObj("Breitgefächerte Bildung").getObj("Vorteile");
								} else if (generationState.containsKey("Veteran")) {
									additionalPros = generationState.getObj("Veteran").getObj("Vorteile");
								}
								if (additionalPros != null && (additionalPros.containsKey("Vollzauberer") || additionalPros.containsKey("Halbzauberer")
										|| additionalPros.containsKey("Viertelzauberer")))
									return false;
								return true;
							},
							profession -> {
								final JSONArray spells = profession.getObj("Zauber").getArr("Wahl");
								final JSONObject newChoices = generationState.getObj("Kultur").getObj("Zauber").getArr("Wahl").getObj(0).clone(spells);
								newChoices.put("Punkte", 60);
								newChoices.removeKey("Anzahl:Maximum");
								newChoices.put("Hauszauber", 4);
								newChoices.removeKey("Leittalente");
								spells.add(newChoices);
							},
							(profession, unalteredProfession) -> {
								final JSONArray spells = profession.getObj("Zauber").getArr("Wahl");
								final JSONObject newChoices = ResourceManager.getResource("Kulturen").getObj("Elfische Siedlung").getObj("Zauber")
										.getArr("Wahl")
										.getObj(0).clone(spells);
								newChoices.put("Punkte", 60);
								newChoices.removeKey("Anzahl:Maximum");
								newChoices.put("Hauszauber", 4);
								newChoices.removeKey("Leittalente");
								spells.remove(newChoices);
							})));

	public RKPSelectors(final JSONObject generationState, final TabPane tabPane, final VBox leftBox, final IntegerProperty gp) {
		super(generationState, gp);

		this.leftBox = leftBox;
		raceSelector = new RKPSelector(this::updateRace, ResourceManager.getResource("data/Rassen"), t -> new RKP(RKP.Type.Race, t._1, t._2, t._3));
		raceTab = addTab(tabPane, "Rasse", raceSelector.getControl());
		cultureSelector = new RKPSelector(this::updateCulture, ResourceManager.getResource("data/Kulturen"), t -> new RKP(RKP.Type.Culture, t._1, t._2, t._3));
		cultureTab = addTab(tabPane, "Kultur", cultureSelector.getControl());
		professionSelector = new RKPSelector(this::updateProfession, ResourceManager.getResource("data/Professionen"),
				t -> new RKP(RKP.Type.Profession, t._1, t._2, t._3));
		professionTab = addTab(tabPane, "Profession", professionSelector.getControl());
		bgbVeteranSelector = new BGBVeteranSelector(this::updateBGBVeteran, generationState);
		bgbVeteranTab = addTab(tabPane, "BGB/Veteran", bgbVeteranSelector.getControl());
	}

	@Override
	public void activate(final boolean forward) {
		final ObservableList<Node> items = leftBox.getChildren();

		items.add(0, new Label("Geschlecht:"));
		final ToggleGroup gender = new ToggleGroup();
		male = new RadioButton("männlich");
		male.setToggleGroup(gender);
		VBox.setMargin(male, new Insets(0, 0, 0, 10));
		items.add(1, male);
		female = new RadioButton("weiblich");
		female.setToggleGroup(gender);
		VBox.setMargin(female, new Insets(0, 0, 0, 10));
		items.add(2, female);

		final JSONObject bio = generationState.getObj("Held").getObj("Biografie");
		if (bio.containsKey("Geschlecht")) {
			final boolean isFemale = "weiblich".equals(bio.getStringOrDefault("Geschlecht", "männlich"));
			male.setSelected(!isFemale);
			female.setSelected(isFemale);
		} else {
			male.setSelected(true);
		}

		male.selectedProperty().addListener((o, oldV, newV) -> {
			generationState.getObj("Held").getObj("Biografie").put("Geschlecht", newV ? "männlich" : "weiblich");
			raceSelector.refreshList();
			cultureSelector.refreshList();
			professionSelector.refreshList();
			bgbVeteranSelector.refreshList();
		});

		items.add(0, new Label("Rasse: "));
		raceLabel = new Label();
		raceLabel.setWrapText(true);
		VBox.setMargin(raceLabel, new Insets(0, 0, 0, 10));
		if (generationState.containsKey("Rasse")) {
			final JSONObject race = generationState.getObj("Rasse");
			raceCost = race.getIntOrDefault("Kosten", 0);
			raceSelector.select(race.getString("Name"), race.getArrOrDefault("Modifikation", null));
			raceLabel.setText(RKPString(raceSelector.getCurrentChoice(), raceSelector.getCurrentVariants(), false));
		}
		changedRace = false;
		items.add(1, raceLabel);

		items.add(2, new Label("Kultur: "));
		cultureLabel = new Label();
		cultureLabel.setWrapText(true);
		VBox.setMargin(cultureLabel, new Insets(0, 0, 0, 10));
		if (generationState.containsKey("Kultur")) {
			final JSONObject culture = generationState.getObj("Kultur");
			cultureCost = culture.getIntOrDefault("Kosten", 0);
			cultureSelector.select(culture.getString("Name"), culture.getArrOrDefault("Modifikation", null));
			cultureLabel.setText(RKPString(cultureSelector.getCurrentChoice(), cultureSelector.getCurrentVariants(), false));
		}
		changedCulture = false;
		items.add(3, cultureLabel);

		items.add(4, new Label("Profession: "));
		professionLabel = new Label();
		professionLabel.setWrapText(true);
		VBox.setMargin(professionLabel, new Insets(0, 0, 0, 10));
		if (generationState.containsKey("Profession")) {
			final JSONObject profession = generationState.getObj("Profession");
			professionCost = profession.getIntOrDefault("Kosten", 0);
			professionSelector.select(profession.getString("Name"), profession.getArrOrDefault("Modifikation", null));
			professionLabel.setText(RKPString(professionSelector.getCurrentChoice(), professionSelector.getCurrentVariants(), false));
		}
		changedProfession = false;
		items.add(5, professionLabel);

		bgbVeteranLabel = new Label();
		items.add(6, bgbVeteranLabel);
		bgbVeteranProfessionLabel = new Label();
		bgbVeteranProfessionLabel.setWrapText(true);
		VBox.setMargin(bgbVeteranProfessionLabel, new Insets(0, 0, 0, 10));
		if (generationState.containsKey("Breitgefächerte Bildung") || generationState.containsKey("Veteran")) {
			final BGBVeteran type = generationState.containsKey("Veteran") ? BGBVeteran.VETERAN : BGBVeteran.BGB;
			final JSONObject profession = generationState.getObj(type == BGBVeteran.BGB ? "Breitgefächerte Bildung" : "Veteran");
			final int cost = type == BGBVeteran.BGB
					? ResourceManager.getResource("data/Vorteile").getObj("Breitgefächerte Bildung").getIntOrDefault("Kosten", 7)
					: ResourceManager.getResource("data/Vorteile").getObj("Veteran").getIntOrDefault("Kosten", 3);
			bgbVeteranCost = profession.getIntOrDefault("Kosten", 0) + cost;
			bgbVeteranSelector.setType(type);
			bgbVeteranSelector.select(profession.getString("Name"), profession.getArrOrDefault("Modifikation", null));
			final String variantString = RKPString(bgbVeteranSelector.getCurrentChoice(), bgbVeteranSelector.getCurrentVariants(), type == BGBVeteran.VETERAN);
			bgbVeteranLabel.setText(type == BGBVeteran.BGB ? "Breitgefächerte Bildung:" : variantString.length() != 0 ? "Veteran:" : "Veteran");
			bgbVeteranProfessionLabel.setText(variantString);
		} else {
			bgbVeteranLabel.setText("");
			bgbVeteranProfessionLabel.setText("");
		}
		changedBgbVeteran = false;
		items.add(7, bgbVeteranProfessionLabel);

		raceTab.setDisable(false);
		cultureTab.setDisable(false);
		professionTab.setDisable(false);
		bgbVeteranTab.setDisable(false);
		raceTab.getTabPane().getSelectionModel().select(raceTab);

		updateCanContinue();
	}

	private JSONObject buildCulture(final RKP culture, final List<RKP> variants) {
		final JSONObject result = buildRKP(culture, variants);

		String culturalKnowledge = getString(culture, "Kulturkunde");
		result.put("Namen", getArr(culture, "Namen").clone(result));

		for (final RKP variant : variants) {
			if (variant.data.containsKey("Kulturkunde")) {
				culturalKnowledge = variant.data.getString("Kulturkunde");
			}
			if (variant.data.containsKey("Namen")) {
				result.put("Namen", variant.data.getArr("Namen").clone(result));
			}
		}

		final JSONArray skill = result.getObj("Sonderfertigkeiten").getArr("Kulturkunde");
		final JSONObject newSkill = new JSONObject(skill);
		newSkill.put("Auswahl", culturalKnowledge);
		skill.add(newSkill);

		return result;
	}

	private JSONObject buildProfession(final RKP profession, final List<RKP> variants) {
		final JSONObject result = buildRKP(profession, variants);

		result.put("Zeitaufwendig", getBool(profession, "Zeitaufwendig"));

		for (final RKP variant : variants) {
			if (variant.data.containsKey("Zeitaufwendig")) {
				result.put("Zeitaufwendig", variant.data.getBool("Zeitaufwendig"));
			}
		}

		if (result.getInt("Kosten") > 15) {
			result.put("Zeitaufwendig", true);
		}

		return result;
	}

	private JSONObject buildRace(final RKP race, final List<RKP> variants) {
		final JSONObject result = buildRKP(race, variants);

		final boolean hasParent = race.parent != null;

		collectObj(race, "Augenfarbe", result);
		if (race.data.containsKey("Schuppenfarbe") || hasParent && race.parent.data.containsKey("Schuppenfarbe")) {
			collectObj(race, "Schuppenfarbe", result);
		} else {
			collectObj(race, "Haarfarbe", result);
			result.put("Hautfarbe", getArr(race, "Hautfarbe").clone(result));
		}
		collectObj(race, "Größe", result);
		collectObj(race, "Gewicht", result);

		for (final RKP variant : variants) {
			collectVariantObj(variant, "Augenfarbe", result);
			collectVariantObj(variant, "Haarfarbe", result);
			if (variant.data.containsKey("Hautfarbe")) {
				result.put("Hautfarbe", variant.data.getArr("Hautfarbe").clone(result));
			}
			collectVariantObj(variant, "Schuppenfarbe", result);
			collectVariantObj(variant, "Größe", result);
			collectVariantObj(variant, "Gewicht", result);
		}

		return result;
	}

	private JSONObject buildRKP(final RKP source, final List<RKP> variants) {
		final JSONObject result = new JSONObject(generationState);

		result.put("Name", getName(source));
		final JSONArray modifications = getModifications(result, source, variants);
		if (modifications.size() > 0) {
			result.put("Modifikation", modifications);
		}

		result.put("Kosten", getInt(source, "Kosten", 0));
		collectObj(source, "Voraussetzungen", result);
		result.put("Sozialstatus:Maximum", getInt(source, "Sozialstatus:Maximum", 21));
		collectObj(source, "Eigenschaften", result);
		collectObj(source, "Basiswerte", result);
		collectObj(source, "Vorteile", result);
		collectObj(source, "Nachteile", result);
		collectObj(source, "Empfohlene Vorteile", result);
		collectObj(source, "Empfohlene Nachteile", result);
		collectObj(source, "Ungeeignete Vorteile", result);
		collectObj(source, "Ungeeignete Nachteile", result);
		collectObj(source, "Sonderfertigkeiten", result);
		collectObj(source, "Verbilligte Sonderfertigkeiten", result);
		collectArr(source, "Leittalente", result);

		RKP current = source;
		final JSONObject languages = new JSONObject(result);
		final JSONObject attributes = new JSONObject(result);
		final JSONObject basicValues = new JSONObject(result);
		final JSONObject talents = new JSONObject(result);
		final JSONObject primarySpells = new JSONObject(result);
		final JSONObject spells = new JSONObject(result);
		while (current != null) {
			ChargenUtil.collectLanguages(languages, current.data.getObjOrDefault("Sprachen", null));
			ChargenUtil.collectModifications(attributes, current.data.getObjOrDefault("Eigenschaften", null));
			ChargenUtil.collectModifications(basicValues, current.data.getObjOrDefault("Basiswerte", null));
			ChargenUtil.collectModifications(talents, current.data.getObjOrDefault("Talente", null));
			ChargenUtil.collectSpellModifications(primarySpells, current.data.getObjOrDefault("Hauszauber", null));
			ChargenUtil.collectSpellModifications(spells, current.data.getObjOrDefault("Zauber", null));
			current = current.parent;
		}
		result.put("Sprachen", languages);
		result.put("Eigenschaften", attributes);
		result.put("Basiswerte", basicValues);
		result.put("Talente", talents);
		result.put("Hauszauber", primarySpells);
		result.put("Zauber", spells);

		collectObj(source, "Ausrüstung", result);

		for (final RKP variant : variants) {
			result.put("Kosten", result.getInt("Kosten") + getInt(variant, "Kosten", 0));
			collectVariantObj(source, "Voraussetzungen", result);
			if (variant.data.containsKey("Sozialstatus:Maximum")) {
				result.put("Sozialstatus:Maximum", variant.data.getInt("Sozialstatus:Maximum"));
			}
			collectVariantObj(variant, "Vorteile", result);
			collectVariantObj(variant, "Nachteile", result);
			collectVariantObj(variant, "Empfohlene Vorteile", result);
			collectVariantObj(variant, "Empfohlene Nachteile", result);
			collectVariantObj(variant, "Ungeeignete Vorteile", result);
			collectVariantObj(variant, "Ungeeignete Nachteile", result);
			collectVariantObj(variant, "Sonderfertigkeiten", result);
			collectVariantObj(variant, "Verbilligte Sonderfertigkeiten", result);
			collectVariantArr(variant, "Leittalente", result);
			if (variant.data.containsKey("Sprachen")) {
				ChargenUtil.collectLanguages(result.getObj("Sprachen"), variant.data.getObj("Sprachen"));
			}
			if (variant.data.containsKey("Eigenschaften")) {
				ChargenUtil.collectModifications(result.getObj("Eigenschaften"), variant.data.getObj("Eigenschaften"));
			}
			if (variant.data.containsKey("Basiswerte")) {
				ChargenUtil.collectModifications(result.getObj("Basiswerte"), variant.data.getObj("Basiswerte"));
			}
			if (variant.data.containsKey("Talente")) {
				ChargenUtil.collectModifications(result.getObj("Talente"), variant.data.getObj("Talente"));
			}
			if (variant.data.containsKey("Hauszauber")) {
				ChargenUtil.collectSpellModifications(result.getObj("Hauszauber"), variant.data.getObj("Hauszauber"));
			}
			if (variant.data.containsKey("Zauber")) {
				ChargenUtil.collectSpellModifications(result.getObj("Zauber"), variant.data.getObj("Zauber"));
			}
			collectVariantObj(variant, "Ausrüstung", result);
		}

		return result;
	}

	private void collectArr(final RKP source, final String name, final JSONObject target) {
		target.put(name, getArr(source, name).clone(target));
	}

	private void collectBasicValues(final JSONObject hero, final int soGP) {
		final JSONObject basicValues = hero.getObj("Basiswerte");

		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject currentValues = generationState.getObj(current).getObjOrDefault("Basiswerte", null);
			if (currentValues != null) {
				for (final String valueName : currentValues.keySet()) {
					if ("Wahl".equals(valueName)) {
						continue;
					}
					final JSONObject value = basicValues.getObj(valueName);
					final String key = basicValueKey(valueName);
					value.put(key, value.getIntOrDefault(key, 0) + currentValues.getInt(valueName));
				}
			}
		}

		if (generationState.containsKey("Breitgefächerte Bildung")) {
			final JSONObject currentValues = generationState.getObj("Breitgefächerte Bildung").getObjOrDefault("Basiswerte", null);
			if (currentValues != null) {
				for (final String valueName : new String[] { "Lebensenergie", "Ausdauer", "Initiative-Basis", "Magieresistenz" }) {
					final JSONObject value = basicValues.getObj(valueName);
					final String key = basicValueKey(valueName);
					value.put(key, value.getIntOrDefault(key, 0) + (currentValues.getIntOrDefault(valueName, 0) + 1) / 2);
				}
				final JSONObject value = basicValues.getObj("Sozialstatus");
				final String key = basicValueKey("Sozialstatus");
				value.put(key, Math.max(value.getIntOrDefault(key, 0), currentValues.getIntOrDefault("Sozialstatus", 0)));
			}
		} else if (generationState.containsKey("Veteran")) {
			final JSONObject currentValues = generationState.getObj("Veteran").getObjOrDefault("Basiswerte", null);
			if (currentValues != null) {
				for (final String valueName : new String[] { "Lebensenergie", "Ausdauer", "Initiative-Basis", "Magieresistenz" }) {
					final JSONObject value = basicValues.getObj(valueName);
					final String key = basicValueKey(valueName);
					value.put(key, value.getIntOrDefault(key, 0) + (currentValues.getIntOrDefault(valueName, 0) + 1) / 2);
				}
			}
		}

		if (soGP != 0) {
			final JSONObject so = basicValues.getObj("Sozialstatus");
			final String key = basicValueKey("Sozialstatus");
			so.put(key, so.getInt(key) + soGP);
			so.put("temporary:GP", soGP);
		}
	}

	private void collectEquipment(final JSONObject hero) {
		final JSONObject posessions = hero.getObj("Besitz");

		final JSONArray inventory = new JSONArray(posessions);
		posessions.put("Ausrüstung", inventory);

		final JSONObject items = ResourceManager.getResource("data/Ausruestung");

		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject currentInventory = generationState.getObj(current).getObjOrDefault("Ausrüstung", null);
			if (currentInventory != null) {
				for (final String name : currentInventory.keySet()) {
					if ("Wahl".equals(name)) {
						continue;
					}
					final JSONObject currentItem = currentInventory.getObj(name);
					JSONObject item;
					if (items.containsKey(name)) {
						item = items.getObj(name).clone(inventory);
						item.addAll(currentItem, true);
					} else {
						item = currentItem.clone(inventory);
					}
					if (!item.containsKey("Name")) {
						item.put("Name", name);
					}

					if (item.containsKey("Bannschwert") && item.getObj("Bannschwert").getObj("Rituale").containsKey("Bannschwert")) {
						item.removeKey("Bruchfaktor");
						if (item.containsKey("Nahkampfwaffe")) {
							item.getObj("Nahkampfwaffe").removeKey("Bruchfaktor");
						}
					}

					inventory.add(item);
				}
			}
		}
	}

	private void collectObj(final RKP source, final String name, final JSONObject target) {
		target.put(name, getObj(source, name).clone(target));
	}

	private void collectProsOrCons(String category, final JSONObject hero) {
		JSONObject prosOrCons = hero.getObj(category);

		for (final String currentType : new String[] { "Rasse", "Kultur", "Profession", "Breitgefächerte Bildung", "Veteran" }) {
			if (!generationState.containsKey(currentType) || "Veteran".equals(currentType) && !"Sonderfertigkeiten".equals(category)) {
				continue;
			}
			final JSONObject currentCategory = generationState.getObj(currentType).getObjOrDefault(category, null);
			boolean onlyNew = false;
			if ("Sonderfertigkeiten".equals(category)) {
				if ("Breitgefächerte Bildung".equals(currentType) || "Veteran".equals(currentType)) {
					category = "Verbilligte Sonderfertigkeiten";
					prosOrCons = hero.getObj("Verbilligte Sonderfertigkeiten");
					if ("Veteran".equals(currentType)) {
						onlyNew = true;
					}
				}
			}
			if (currentCategory != null) {
				for (final String name : currentCategory.keySet()) {
					if ("Wahl".equals(name)) {
						continue;
					}
					final JSONObject proOrCon = HeroUtil.findProConOrSkill(name)._1;
					if (proOrCon == null) {
						continue;
					}
					if (proOrCon.containsKey("Auswahl") || proOrCon.containsKey("Freitext")) {
						final JSONArray actualArray = currentCategory.getArr(name);
						if (prosOrCons.containsKey(name)) {
							final JSONArray proOrConArray = prosOrCons.getArr(name);
							for (int i = 0; i < actualArray.size(); ++i) {
								final JSONObject current = actualArray.getObj(i);
								final JSONObject match = ChargenUtil.match(proOrConArray, current, proOrCon.containsKey("Auswahl"),
										proOrCon.containsKey("Freitext"));
								if (match != null) {
									if ("Verbilligte Sonderfertigkeiten".equals(category)) {
										if (onlyNew) {
											final JSONObject professionMatch = ChargenUtil.match(
													generationState.getObj("Profession").getObj("Sonderfertigkeiten").getArrOrDefault(name, null), current,
													proOrCon.containsKey("Auswahl"), proOrCon.containsKey("Freitext"));
											if (professionMatch != null) {
												continue;
											}
										}
										current.put("Verbilligungen", current.getIntOrDefault("Verbilligungen", 1) + 1);
									} else if (proOrCon.getBoolOrDefault("Abgestuft", false)) {
										HeroUtil.unapplyEffect(hero, name, proOrCon, match);
										match.put("Stufe", match.getIntOrDefault("Stufe", 0) + currentCategory.getObj(name).getIntOrDefault("Stufe", 0));
										HeroUtil.applyEffect(hero, name, proOrCon, match);
									} else {
										prosOrCons.put("temporary:Pool",
												prosOrCons.getIntOrDefault("temporary:Pool", 0) + proOrCon.getIntOrDefault("Kosten", 0));
									}
								} else {
									proOrConArray.add(current.clone(proOrConArray));
									if (!"Verbilligte Sonderfertigkeiten".equals(category)) {
										HeroUtil.applyEffect(hero, name, proOrCon, current);
									}
								}
							}
						} else {
							prosOrCons.put(name, currentCategory.getArr(name).clone(prosOrCons));
							final JSONArray current = prosOrCons.getArr(name);
							if (!"Verbilligte Sonderfertigkeiten".equals(category)) {
								for (int i = 0; i < current.size(); ++i) {
									HeroUtil.applyEffect(hero, name, proOrCon, current.getObj(i));
								}
							}
						}
					} else {
						switch (name) {
							case "Vollzauberer":
								if (prosOrCons.containsKey("Vollzauberer")) {
									gp.set(gp.get() + (int) Math.round(proOrCon.getIntOrDefault("Kosten", 0) * 0.3));
									break;
								} else if (prosOrCons.containsKey("Halbzauberer")) {
									gp.set(gp.get() + (int) Math
											.round(ResourceManager.getResource("data/Vorteile").getObj("Halbzauberer").getIntOrDefault("Kosten", 0) * 0.3));
									HeroUtil.unapplyEffect(hero, "Halbzauberer", ResourceManager.getResource("data/Vorteile").getObj("Halbzauberer"),
											prosOrCons.getObj("Halbzauberer"));
									prosOrCons.removeKey("Halbzauberer");
								} else if (prosOrCons.containsKey("Viertelzauberer")) {
									gp.set(gp.get() + (int) Math
											.round(ResourceManager.getResource("data/Vorteile").getObj("Viertelzauberer").getIntOrDefault("Kosten", 0) * 0.3));
									HeroUtil.unapplyEffect(hero, "Viertelzauberer", ResourceManager.getResource("data/Vorteile").getObj("Viertelzauberer"),
											prosOrCons.getObj("Viertelzauberer"));
									prosOrCons.removeKey("Viertelzauberer");
								}
								prosOrCons.put(name, currentCategory.getObj(name).clone(prosOrCons));
								HeroUtil.applyEffect(hero, name, proOrCon, prosOrCons.getObj(name));
								break;
							case "Halbzauberer":
								if (prosOrCons.containsKey("Vollzauberer") || prosOrCons.containsKey("Halbzauberer")) {
									gp.set(gp.get() + (int) Math.round(proOrCon.getIntOrDefault("Kosten", 0) * 0.3));
									break;
								} else if (prosOrCons.containsKey("Viertelzauberer")) {
									gp.set(gp.get() + (int) Math
											.round(ResourceManager.getResource("data/Vorteile").getObj("Viertelzauberer").getIntOrDefault("Kosten", 0) * 0.3));
									HeroUtil.unapplyEffect(hero, "Viertelzauberer", ResourceManager.getResource("data/Vorteile").getObj("Viertelzauberer"),
											prosOrCons.getObj("Viertelzauberer"));
									prosOrCons.removeKey("Viertelzauberer");
								}
								prosOrCons.put(name, currentCategory.getObj(name).clone(prosOrCons));
								HeroUtil.applyEffect(hero, name, proOrCon, prosOrCons.getObj(name));
								break;
							case "Viertelzauberer":
								if (prosOrCons.containsKey("Vollzauberer") || prosOrCons.containsKey("Halbzauberer")
										|| prosOrCons.containsKey("Viertelzauberer")) {
									gp.set(gp.get() + (int) Math.round(proOrCon.getIntOrDefault("Kosten", 0) * 0.3));
								} else {
									prosOrCons.put(name, currentCategory.getObj(name).clone(prosOrCons));
									HeroUtil.applyEffect(hero, name, proOrCon, prosOrCons.getObj(name));
								}
								break;
							default:
								if (prosOrCons.containsKey(name)) {
									if ("Verbilligte Sonderfertigkeiten".equals(category)) {
										if (!onlyNew || !generationState.getObj("Profession").getObj("Sonderfertigkeiten").containsKey(name)) {
											final JSONObject skill = prosOrCons.getObj(name);
											skill.put("Verbilligungen", skill.getIntOrDefault("Verbilligungen", 1) + 1);
										}
									} else if (proOrCon.getBoolOrDefault("Abgestuft", false)) {
										final JSONObject actual = prosOrCons.getObj(name);
										HeroUtil.unapplyEffect(hero, name, proOrCon, actual);
										actual.put("Stufe", actual.getIntOrDefault("Stufe", 0) + currentCategory.getObj(name).getIntOrDefault("Stufe", 0));
										HeroUtil.applyEffect(hero, name, proOrCon, actual);
									} else {
										prosOrCons.put("temporary:Pool",
												prosOrCons.getIntOrDefault("temporary:Pool", 0) + proOrCon.getIntOrDefault("Kosten", 0));
									}
								} else {
									prosOrCons.put(name, currentCategory.getObj(name).clone(prosOrCons));
									if (!"Verbilligte Sonderfertigkeiten".equals(category)) {
										HeroUtil.applyEffect(hero, name, proOrCon, prosOrCons.getObj(name));
									}
								}
								break;
						}
					}
				}
			}
		}
	}

	private void collectSpells(final JSONObject hero) {
		final Map<Tuple3<String, String, Boolean>, List<Integer>> spellList = new HashMap<>();
		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject currentPrimarySpells = generationState.getObj(current).getObjOrDefault("Hauszauber", null);
			if (currentPrimarySpells != null) {
				for (final String spellName : currentPrimarySpells.keySet()) {
					if ("Wahl".equals(spellName)) {
						continue;
					}
					final JSONObject currentSpell = HeroUtil.findTalent(spellName)._1;
					final JSONObject spell = currentPrimarySpells.getObj(spellName);
					for (final String representation : spell.keySet()) {
						final Tuple3<String, String, Boolean> id = new Tuple3<>(spellName, representation, true);
						if (currentSpell.containsKey("Auswahl") || currentSpell.containsKey("Freitext")) {
							final List<Integer> old = spellList.getOrDefault(id, new ArrayList<>());
							final JSONArray modifications = spell.getArr(representation);
							spellList.put(id, old);
							for (int i = 0; i < modifications.size(); ++i) {
								old.add(modifications.getInt(i));
							}
						} else {
							List<Integer> old = spellList.getOrDefault(id, null);
							if (old == null) {
								final Tuple3<String, String, Boolean> nid = new Tuple3<>(spellName, representation, false);
								old = spellList.getOrDefault(nid, Collections.singletonList(0));
								spellList.remove(nid);
							}
							spellList.put(id, Collections.singletonList(old.get(0) + spell.getInt(representation)));
						}
					}
				}
			}
			final JSONObject currentSpells = generationState.getObj(current).getObjOrDefault("Zauber", null);
			if (currentSpells != null) {
				for (final String spellName : currentSpells.keySet()) {
					if ("Wahl".equals(spellName)) {
						continue;
					}
					final JSONObject currentSpell = HeroUtil.findTalent(spellName)._1;
					final JSONObject spell = currentSpells.getObj(spellName);
					for (final String representation : spell.keySet()) {
						Tuple3<String, String, Boolean> id = new Tuple3<>(spellName, representation, false);
						if (currentSpell.containsKey("Auswahl") || currentSpell.containsKey("Freitext")) {
							final List<Integer> old = spellList.getOrDefault(id, new ArrayList<>());
							final JSONArray modifications = spell.getArr(representation);
							spellList.put(id, old);
							for (int i = 0; i < modifications.size(); ++i) {
								old.add(modifications.getInt(i));
							}
						} else {
							List<Integer> old = spellList.getOrDefault(id, null);
							if (old == null) {
								final Tuple3<String, String, Boolean> nid = new Tuple3<>(spellName, representation, true);
								old = spellList.getOrDefault(nid, null);
								if (old != null) {
									id = nid;
								} else {
									old = Collections.singletonList(0);
								}
							}
							spellList.put(id, Collections.singletonList(old.get(0) + spell.getInt(representation)));
						}
					}
				}
			}
		}

		final JSONObject spells = hero.getObj("Zauber");

		for (final Tuple3<String, String, Boolean> id : spellList.keySet()) {
			final JSONObject spell = spells.getObj(id._1);
			final JSONObject currentSpell = HeroUtil.findTalent(id._1)._1;
			if (currentSpell.containsKey("Auswahl") || currentSpell.containsKey("Freitext")) {
				final JSONArray rep = spell.getArr(id._2);
				final List<Integer> modifications = spellList.get(id);
				for (final Integer mod : modifications) {
					final JSONObject current = new JSONObject(rep);
					current.put("ZfW", mod);
					if (id._3) {
						current.put("Hauszauber", true);
					}
					rep.add(current);
				}
			} else {
				final JSONObject rep = new JSONObject(spell);
				rep.put("ZfW", spellList.get(id).get(0));
				if (id._3) {
					rep.put("Hauszauber", true);
				}
				spell.put(id._2, rep);
			}
		}
	}

	private void collectTalents(final JSONObject hero) {
		final JSONObject actualTalents = hero.getObj("Talente");

		final Map<String, List<Integer>> talentList = new HashMap<>();
		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject currentTalents = generationState.getObj(current).getObjOrDefault("Talente", null);
			final JSONArray primaryTalents = generationState.getObj(current).getArrOrDefault("Leittalente", null);
			if (currentTalents != null) {
				for (final String talentName : currentTalents.keySet()) {
					if ("Wahl".equals(talentName) || "Muttersprache".equals(talentName) || "Muttersprache:Schrift".equals(talentName)) {
						continue;
					}
					final Tuple<JSONObject, String> talentAndGroup = HeroUtil.findTalent(talentName);
					if (talentAndGroup._1.containsKey("Auswahl") || talentAndGroup._1.containsKey("Freitext")) {
						talentList.put(talentName, talentList.getOrDefault(talentName, new ArrayList<>()));
						final JSONArray modifications = currentTalents.getArr(talentName);
						for (int i = 0; i < modifications.size(); ++i) {
							talentList.get(talentName).add(modifications.getInt(i));
						}
					} else {
						talentList.put(talentName, Collections
								.singletonList(talentList.getOrDefault(talentName, Collections.singletonList(0)).get(0) + currentTalents.getInt(talentName)));
						if (primaryTalents != null && primaryTalents.contains(talentName)) {
							final JSONObject talent = actualTalents.getObj(talentAndGroup._2).getObj(talentName);
							talent.put("Leittalent", true);
							talent.put("temporary:RKPPrimaryTalent", true);
						}
					}
				}
			}
		}

		for (final String talentName : talentList.keySet()) {
			final Tuple<JSONObject, String> talentAndGroup = HeroUtil.findTalent(talentName);
			final String groupName = talentAndGroup._2;
			if (groupName == null) {
				continue;
			}
			if (talentAndGroup._1.containsKey("Auswahl") || talentAndGroup._1.containsKey("Freitext")) {
				final JSONArray talent = actualTalents.getObj(groupName).getArr(talentName);
				for (final int mod : talentList.get(talentName)) {
					final JSONObject currentTalent = new JSONObject(talent);
					currentTalent.put("TaW", mod);
					talent.add(currentTalent);
				}
			} else {
				final JSONObject talent = actualTalents.getObj(groupName).getObj(talentName);
				talent.put("TaW", talentList.get(talentName).get(0));
			}
		}

		final JSONObject languages = actualTalents.getObj("Sprachen und Schriften");
		for (final String current : new String[] { "Rasse", "Kultur", "Profession" }) {
			final JSONObject actualLanguages = generationState.getObj(current).getObj("Sprachen");
			if (actualLanguages == null) {
				continue;
			}
			for (final String type : new String[] { "Muttersprache", "Zweitsprache", "Lehrsprache" }) {
				final JSONArray choices = actualLanguages.getArrOrDefault(type, null);
				if (choices != null) {
					for (int i = 0; i < choices.size(); ++i) {
						final JSONArray choice = choices.getArr(i);
						if (choice != null && choice.size() == 1) {
							final String name = choice.getString(0);
							final JSONObject actualLanguage = languages.getObj(name);
							actualLanguage.put(type, true);
						}
					}
				}
			}
		}

		final JSONObject talents = ResourceManager.getResource("data/Talente");
		for (final String groupName : talents.keySet()) {
			final JSONObject talentGroup = talents.getObj(groupName);
			for (final String talentName : talentGroup.keySet()) {
				final JSONObject talent = talentGroup.getObj(talentName);
				if (talent.getBoolOrDefault("Basis", false)) {
					final JSONObject actualTalent = actualTalents.getObj(groupName).getObj(talentName);
					if (!actualTalent.containsKey("TaW")) {
						actualTalent.put("TaW", 0);
					}
				}
			}
		}
	}

	private void collectVariantArr(final RKP source, final String name, final Consumer<JSONArray> action) {
		if (source.data.containsKey(name)) {
			action.accept(source.data.getArr(name));
		}
	}

	private void collectVariantArr(final RKP source, final String name, final JSONObject target) {
		collectVariantArr(source, name, o -> target.put(name, o.clone(target)));
	}

	private void collectVariantObj(final RKP source, final String name, final Consumer<JSONObject> action) {
		if (source.data.containsKey(name)) {
			action.accept(source.data.getObj(name));
		}
	}

	private void collectVariantObj(final RKP source, final String name, final JSONObject target) {
		collectVariantObj(source, name, o -> target.put(name, o.clone(target)));
	}

	@Override
	public void deactivate(final boolean forward) {
		raceTab.setDisable(true);
		cultureTab.setDisable(true);
		professionTab.setDisable(true);
		bgbVeteranTab.setDisable(true);

		final ObservableList<Node> items = leftBox.getChildren();
		items.remove(0, 11);

		final JSONObject hero = generationState.getObj("Held");
		final JSONObject biography = hero.getObj("Biografie");

		if (changedRace) {
			final RKP race = raceSelector.getCurrentChoice();
			final List<RKP> raceVariants = raceSelector.getCurrentVariants();

			biography.put("Rasse", getName(race));

			final JSONArray modifications = getModifications(biography, race, raceVariants);
			if (modifications.size() != 0) {
				biography.put("Rasse:Modifikation", modifications);
			} else {
				biography.removeKey("Rasse:Modifikation");
			}
		}

		if (changedCulture) {
			final RKP culture = cultureSelector.getCurrentChoice();
			final List<RKP> cultureVariants = cultureSelector.getCurrentVariants();

			biography.put("Kultur", getName(culture));

			final JSONArray modifications = getModifications(biography, culture, cultureVariants);
			if (modifications.size() != 0) {
				biography.put("Kultur:Modifikation", modifications);
			} else {
				biography.removeKey("Kultur:Modifikation");
			}
		}

		if (changedProfession) {
			final RKP profession = professionSelector.getCurrentChoice();
			final List<RKP> professionVariants = professionSelector.getCurrentVariants();

			biography.put("Profession", getName(profession));

			final JSONArray modifications = getModifications(biography, profession, professionVariants);
			if (modifications.size() != 0) {
				biography.put("Profession:Modifikation", modifications);
			} else {
				biography.removeKey("Profession:Modifikation");
			}
		}

		if (changedRace || changedCulture || changedProfession || changedBgbVeteran) {
			final JSONObject basicValues = hero.getObj("Basiswerte");
			final int soGP = basicValues.getObj("Sozialstatus").getIntOrDefault("temporary:GP", 0);
			basicValues.clear();
			hero.getObj("Vorteile").clear();
			hero.getObj("Nachteile").clear();
			hero.getObj("Sonderfertigkeiten").clear();
			hero.getObj("Verbilligte Sonderfertigkeiten").clear();
			hero.getObj("Talente").clear();
			hero.removeKey("Zauber");
			hero.removeKey("Besitz");

			handleSpecialCases(hero);

			collectBasicValues(hero, soGP);

			collectProsOrCons("Vorteile", hero);
			collectProsOrCons("Nachteile", hero);
			collectProsOrCons("Sonderfertigkeiten", hero);
			collectProsOrCons("Verbilligte Sonderfertigkeiten", hero);

			collectTalents(hero);
			collectSpells(hero);

			collectEquipment(hero);
		}

		final JSONObject pros = hero.getObj("Vorteile");
		final RKP profession = bgbVeteranSelector.getCurrentChoice();
		final List<RKP> professionVariants = professionSelector.getCurrentVariants();

		switch (bgbVeteranSelector.getType()) {
			case VETERAN:
				final JSONObject veteran = new JSONObject(pros);
				final JSONArray veternModifications = getModifications(veteran, profession, professionVariants);
				if (veternModifications.size() != 0) {
					veteran.put("Profession:Modifikation", veternModifications);
				} else {
					veteran.removeKey("Profession:Modifikation");
				}
				pros.put("Veteran", veteran);
				pros.removeKey("Breitgefächerte Bildung");
				break;
			case BGB:
				final JSONObject bgb = new JSONObject(pros);
				bgb.put("Profession", getName(profession));
				final JSONArray bgbModifications = getModifications(bgb, profession, professionVariants);
				if (bgbModifications.size() != 0) {
					bgb.put("Profession:Modifikation", bgbModifications);
				} else {
					bgb.removeKey("Profession:Modifikation");
				}
				pros.put("Breitgefächerte Bildung", bgb);
				pros.removeKey("Veteran");
				break;
			default:
				pros.removeKey("Breitgefächerte Bildung");
				pros.removeKey("Veteran");
				break;
		}
	}

	private JSONArray getArr(final RKP source, final String name) {
		return source.data.getArrOrDefault(name, source.parent != null ? getArr(source.parent, name) : dummyArr);
	}

	private boolean getBool(final RKP source, final String name) {
		return source.data.getBoolOrDefault(name, source.parent != null ? getBool(source.parent, name) : false);
	}

	private int getCost(final RKP choice, final List<RKP> variants) {
		if (choice == null) return 0;
		int result = choice.getCost(0);
		for (final RKP variant : variants) {
			result += variant.getCost(0);
		}
		return result;
	}

	private JSONArray getModifications(final JSONValue parent, final RKP rkp, final List<RKP> variants) {
		final JSONArray modifications = new JSONArray(parent);
		for (final String name : getModifications(rkp)) {
			modifications.add(name);
		}
		for (final RKP variant : variants) {
			modifications.add(variant.name);
		}
		return modifications;
	}

	private JSONObject getObj(final RKP source, final String name) {
		return source.data.getObjOrDefault(name, source.parent != null ? getObj(source.parent, name) : dummyObj);
	}

	private String getString(final RKP source, final String name) {
		return source.data.getStringOrDefault(name, source.parent != null ? getString(source.parent, name) : "");
	}

	private void handleSpecialCases(final JSONObject hero) {
		final JSONObject unalteredRKP = new JSONObject(null);
		unalteredRKP.put("Rasse", buildRace(raceSelector.getCurrentChoice(), raceSelector.getCurrentVariants()).clone(unalteredRKP));
		unalteredRKP.put("Kultur", buildRace(cultureSelector.getCurrentChoice(), cultureSelector.getCurrentVariants()).clone(unalteredRKP));
		unalteredRKP.put("Profession", buildRace(professionSelector.getCurrentChoice(), professionSelector.getCurrentVariants()).clone(unalteredRKP));

		for (final Tuple5<String, String, Predicate<JSONObject>, Consumer<JSONObject>, BiConsumer<JSONObject, JSONObject>> specialCase : specialCases) {
			final String name = "temporary:Spezialfall" + specialCase._1;
			final String type = specialCase._2;
			final Predicate<JSONObject> applicable = specialCase._3;
			final Consumer<JSONObject> apply = specialCase._4;
			final BiConsumer<JSONObject, JSONObject> unapply = specialCase._5;

			final JSONObject modified = generationState.getObj(type);
			if (applicable.test(hero)) {
				if (!modified.getBoolOrDefault(name, false)) {
					apply.accept(modified);
					modified.put(name, true);
				}
			} else {
				if (modified.getBoolOrDefault(name, false)) {
					unapply.accept(modified, unalteredRKP.getObj(type));
					modified.removeKey(name);
				}
			}
		}
	}

	private boolean isPossibleProfession(final RKP race, final RKP culture, final List<RKP> cultureVariants, final RKP profession) {
		if (profession == null) return true;

		if (race != null) {
			RKP current = profession;

			while (current != null) {
				final JSONObject requirements = current.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getObjOrDefault("Rassen", null);
				if (requirements != null) {
					final JSONObject requiredRaces = requirements.getObjOrDefault("Muss", requirements.getObjOrDefault("Wahl", null));
					if (requiredRaces != null && !requiredRaces.containsKey(race.name)) return false;
				}
				current = current.parent;
			}
		}

		if (culture != null) {
			RKP current = profession;
			JSONArray possible = culture.getSuggestedOrPossible()._2;

			if (culture.parent != null && possible == null) {
				possible = culture.parent.getSuggestedOrPossible()._2;
			}
			if (cultureVariants != null) {
				for (final RKP variant : cultureVariants) {
					final Tuple<JSONArray, JSONArray> variantSuggestedOrPossible = variant.getSuggestedOrPossible();
					if (variantSuggestedOrPossible._2 != null) {
						possible = variantSuggestedOrPossible._2;
					}
				}
			}

			while (current != null) {
				final JSONObject requirements = current.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getObjOrDefault("Kulturen", null);
				if (requirements != null) {
					final JSONObject requiredCultures = requirements.getObjOrDefault("Muss", requirements.getObjOrDefault("Wahl", null));
					if (requiredCultures != null && !requiredCultures.containsKey(culture.name)) return false;
				}
				if (possible != null && possible.contains(current.name)) return true;
				current = current.parent;
			}
			return possible == null;
		}

		return true;
	}

	private boolean isPossibleRace(final RKP race, final RKP culture, final RKP profession, final RKP secondProfession) {
		if (race == null) return true;

		if (profession != null) {
			RKP current = profession;

			while (current != null) {
				final JSONObject requirements = current.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getObjOrDefault("Rassen", null);
				if (requirements != null) {
					final JSONObject requiredRaces = requirements.getObjOrDefault("Muss", requirements.getObjOrDefault("Wahl", null));
					if (requiredRaces != null) {
						if (!requiredRaces.containsKey(race.name))
							return false;
						break;
					}
				}
				current = current.parent;
			}
		}

		if (secondProfession != null) {
			RKP current = secondProfession;

			while (current != null) {
				final JSONObject requirements = current.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getObjOrDefault("Rassen", null);
				if (requirements != null) {
					final JSONObject requiredRaces = requirements.getObjOrDefault("Muss", requirements.getObjOrDefault("Wahl", null));
					if (requiredRaces != null) {
						if (!requiredRaces.containsKey(race.name))
							return false;
						break;
					}
				}
				current = current.parent;
			}
		}

		if (culture != null) {
			JSONArray possible = race.getSuggestedOrPossible()._2;
			if (race.parent != null && possible == null) {
				possible = race.parent.getSuggestedOrPossible()._2;
			}
			RKP current = culture;
			while (current != null) {
				if (possible != null)
					return possible.contains(current.name);
				current = current.parent;
			}
		}

		return true;
	}

	private boolean isSuggestedRace(final RKP race, final RKP culture) {
		if (race == null || culture == null) return false;

		JSONArray suggested = race.getSuggestedOrPossible()._1;
		if (race.parent != null && suggested == null) {
			suggested = race.parent.getSuggestedOrPossible()._1;
		}
		if (suggested == null) return false;
		RKP current = culture;
		while (current != null) {
			if (suggested.contains(current.name)) return true;
			current = current.parent;
		}
		return false;
	}

	private String RKPString(final RKP rkp, final List<RKP> variants, final boolean skipRootName) {
		if (rkp == null) return "";
		boolean first = true;
		final StringBuilder result = new StringBuilder();

		RKP root = rkp;
		final Stack<String> names = new Stack<>();

		names.push(rkp.getName(!male.isSelected()));
		while (root.parent != null) {
			root = root.parent;
			if (names.isEmpty() || !root.name.equals(names.peek())) {
				names.push(root.getName(!male.isSelected()));
			}
		}

		if (!names.isEmpty()) {
			final String rootName = names.pop();
			if (!skipRootName) {
				result.append(rootName);
			}
		}

		while (!names.isEmpty()) {
			if (first) {
				if (!skipRootName) {
					result.append(" (");
				}
				first = false;
			} else {
				result.append(", ");
			}
			result.append(names.pop());
		}

		if (variants != null) {
			for (final RKP variant : variants) {
				if (first) {
					if (!skipRootName) {
						result.append(" (");
					}
					first = false;
				} else {
					result.append(", ");
				}
				result.append(variant.getName(!male.isSelected()));
			}
		}
		if (!first && !skipRootName) {
			result.append(')');
		}
		return result.toString();
	}

	private void updateBGBVeteran() {
		changedBgbVeteran = true;

		final BGBVeteran type = bgbVeteranSelector.getType();
		final RKP profession = bgbVeteranSelector.getCurrentChoice();
		final List<RKP> variants = bgbVeteranSelector.getCurrentVariants();

		if (profession == null) {
			bgbVeteranLabel.setText("");
			bgbVeteranProfessionLabel.setText("");
			generationState.removeKey("Breitgefächerte Bildung");
			generationState.removeKey("Veteran");
		} else {
			switch (type) {
				case VETERAN:
					final String variantString = RKPString(profession, variants, true);
					bgbVeteranLabel.setText(variantString.length() != 0 ? "Veteran:" : "Veteran");
					bgbVeteranProfessionLabel.setText(variantString);
					generationState.put("Veteran", buildProfession(profession, variants));
					break;
				case BGB:
					bgbVeteranLabel.setText("Breitgefächerte Bildung:");
					bgbVeteranProfessionLabel.setText(RKPString(profession, variants, false));
					generationState.put("Breitgefächerte Bildung", buildProfession(profession, variants));
					break;
				default:
					bgbVeteranLabel.setText("");
					bgbVeteranProfessionLabel.setText("");
					generationState.removeKey("Breitgefächerte Bildung");
					generationState.removeKey("Veteran");
			}
		}

		final RKP culture = cultureSelector.getCurrentChoice();
		final RKP secondProfession = professionSelector.getCurrentChoice();
		raceSelector.updateSuggestedPossible(race -> isSuggestedRace(race, culture), race -> isPossibleRace(race, culture, secondProfession, profession));

		updateCultureSuggestedOrPossible();
		updateCanContinue();

		updateGenderValidity();

		final int cost = switch (type) {
			case BGB -> ResourceManager.getResource("data/Vorteile").getObj("Breitgefächerte Bildung").getIntOrDefault("Kosten", 7);
			case VETERAN -> ResourceManager.getResource("data/Vorteile").getObj("Veteran").getIntOrDefault("Kosten", 3);
			default -> 0;
		};

		gp.set(gp.get() + bgbVeteranCost);
		bgbVeteranCost = (type != BGBVeteran.NONE ? getCost(profession, variants) : 0) + cost;
		gp.set(gp.get() - bgbVeteranCost);
	}

	private void updateCanContinue() {
		final boolean rkp = raceSelector.getCurrentChoice() != null && cultureSelector.getCurrentChoice() != null
				&& professionSelector.getCurrentChoice() != null;
		final boolean bgbVeteran = bgbVeteranSelector.getType() == BGBVeteran.NONE || bgbVeteranSelector.getCurrentChoice() != null;
		canContinue.set(rkp && bgbVeteran);
	}

	private void updateCulture() {
		changedCulture = true;

		final RKP culture = cultureSelector.getCurrentChoice();
		final List<RKP> variants = cultureSelector.getCurrentVariants();
		cultureLabel.setText(RKPString(culture, variants, false));

		if (culture == null) {
			generationState.removeKey("Kultur");
		} else {
			generationState.put("Kultur", buildCulture(culture, variants));
		}

		raceSelector.updateSuggestedPossible(race -> isSuggestedRace(race, culture),
				race -> isPossibleRace(race, culture, professionSelector.getCurrentChoice(), bgbVeteranSelector.getCurrentChoice()));

		final RKP race = raceSelector.getCurrentChoice();
		professionSelector.updateSuggestedPossible(profession -> false, profession -> isPossibleProfession(race, culture, variants, profession));
		bgbVeteranSelector.setSuggestedPossible(profession -> false, profession -> isPossibleProfession(race, culture, variants, profession));

		updateCanContinue();
		bgbVeteranSelector.updateValid();

		updateGenderValidity();

		gp.set(gp.get() + cultureCost);
		cultureCost = getCost(culture, variants);
		gp.set(gp.get() - cultureCost);
	}

	private void updateCultureSuggestedOrPossible() {
		RKP race = raceSelector.getCurrentChoice();
		final List<RKP> raceVariants = raceSelector.getCurrentVariants();
		final RKP profession = professionSelector.getCurrentChoice();
		final RKP secondProfession = bgbVeteranSelector.getCurrentChoice();

		final Tuple<JSONArray, JSONArray> fromRace = new Tuple<>(null, null);

		while (race != null) {
			final Tuple<JSONArray, JSONArray> suggestedOrPossible = race.getSuggestedOrPossible();
			if (fromRace._1 == null) {
				fromRace._1 = suggestedOrPossible._1;
			}
			if (fromRace._2 == null) {
				fromRace._2 = suggestedOrPossible._2;
			}
			if (fromRace._1 != null && fromRace._2 != null) {
				break;
			}
			race = race.parent;
		}
		if (raceVariants != null) {
			for (final RKP variant : raceVariants) {
				final Tuple<JSONArray, JSONArray> variantSuggestedOrPossible = variant.getSuggestedOrPossible();
				if (variantSuggestedOrPossible._1 != null) {
					fromRace._1 = variantSuggestedOrPossible._1;
				}
				if (variantSuggestedOrPossible._2 != null) {
					fromRace._2 = variantSuggestedOrPossible._2;
				}
			}
		}

		cultureSelector.updateSuggestedPossible(culture -> {
			if (fromRace._1 == null) return false;
			RKP current = culture;
			while (current != null) {
				if (fromRace._1.contains(current.name) && isPossibleProfession(null, culture, null, profession)
						&& isPossibleProfession(null, culture, null, secondProfession))
					return true;
				current = current.parent;
			}
			return false;
		}, culture -> {
			boolean raceOk = false;
			if (fromRace._2 == null) {
				raceOk = true;
			} else {
				RKP current = culture;
				while (current != null) {
					if (fromRace._2.contains(current.name)) {
						raceOk = true;
						break;
					}
					current = current.parent;
				}
			}

			final boolean professionOk = isPossibleProfession(null, culture, null, profession);

			final boolean bgbVeteranOk = isPossibleProfession(null, culture, null, secondProfession);

			return raceOk && professionOk && bgbVeteranOk;
		});
	}

	private void updateGenderValidity() {
		male.getStyleClass().remove("invalid");
		female.getStyleClass().remove("invalid");
		updateGenderValidity(raceSelector.getCurrentChoice(), raceSelector.getCurrentVariants());
		updateGenderValidity(cultureSelector.getCurrentChoice(), cultureSelector.getCurrentVariants());
		updateGenderValidity(professionSelector.getCurrentChoice(), professionSelector.getCurrentVariants());
		updateGenderValidity(bgbVeteranSelector.getCurrentChoice(), bgbVeteranSelector.getCurrentVariants());
	}

	private void updateGenderValidity(final RKP choice, final List<RKP> variants) {
		if (variants != null) {
			for (final RKP variant : variants) {
				final String requirement = variant.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getStringOrDefault("Geschlecht", null);
				if (requirement != null) {
					if ("männlich".equals(requirement)) {
						female.getStyleClass().add("invalid");
					} else {
						male.getStyleClass().add("invalid");
					}
					return;
				}
			}
		}

		if (choice != null) {
			final String requirement = choice.data.getObjOrDefault("Voraussetzungen", new JSONObject(null)).getStringOrDefault("Geschlecht", null);
			if (requirement != null) {
				if ("männlich".equals(requirement)) {
					female.getStyleClass().add("invalid");
				} else {
					male.getStyleClass().add("invalid");
				}
			}
		}
	}

	private void updateProfession() {
		changedProfession = true;
		final RKP profession = professionSelector.getCurrentChoice();
		final List<RKP> variants = professionSelector.getCurrentVariants();
		professionLabel.setText(RKPString(profession, variants, false));

		if (profession == null) {
			generationState.removeKey("Profession");
		} else {
			generationState.put("Profession", buildProfession(profession, variants));
		}

		final RKP culture = cultureSelector.getCurrentChoice();
		final RKP secondProfession = bgbVeteranSelector.getCurrentChoice();
		raceSelector.updateSuggestedPossible(race -> isSuggestedRace(race, culture), race -> isPossibleRace(race, culture, profession, secondProfession));

		updateCultureSuggestedOrPossible();
		updateCanContinue();

		updateGenderValidity();

		bgbVeteranSelector.setProfession(profession);

		gp.set(gp.get() + professionCost);
		professionCost = getCost(profession, variants);
		gp.set(gp.get() - professionCost);
	}

	private void updateRace() {
		changedRace = true;
		final RKP race = raceSelector.getCurrentChoice();
		final List<RKP> variants = raceSelector.getCurrentVariants();
		raceLabel.setText(RKPString(race, variants, false));

		if (race == null) {
			generationState.removeKey("Rasse");
		} else {
			generationState.put("Rasse", buildRace(race, variants));
		}

		updateCultureSuggestedOrPossible();
		updateCanContinue();
		bgbVeteranSelector.updateValid();

		updateGenderValidity();

		gp.set(gp.get() + raceCost);
		raceCost = getCost(race, variants);
		gp.set(gp.get() - raceCost);
	}
}
