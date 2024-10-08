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
package chargen.choices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import chargen.ui.TabController;
import dsa41basis.hero.Spell;
import dsa41basis.hero.Talent;
import dsa41basis.util.DSAUtil;
import dsa41basis.util.HeroUtil;
import dsatool.gui.GUIUtil;
import dsatool.resources.ResourceManager;
import dsatool.ui.ReactiveSpinner;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import jsonant.value.JSONValue;

public class Choices extends TabController {

	public static class ChoicePage {
		private final StringProperty text;
		private final BooleanProperty valid;

		public ChoicePage(final String text, final BooleanExpression valid) {
			this.text = new SimpleStringProperty(text);
			this.valid = new SimpleBooleanProperty();
			this.valid.bind(valid);
		}

		public ReadOnlyStringProperty textProperty() {
			return text;
		}

		public BooleanProperty validProperty() {
			return valid;
		}
	}

	private final StackPane pane = new StackPane();

	private final Tab tab;
	private final TableView<ChoicePage> choiceNames;
	private final VBox leftBox;

	private final List<BooleanProperty> toSelect = new ArrayList<>();

	private final Map<String, Talent> talents = new HashMap<>();

	private final List<String> languageTypes = List.of("Muttersprache", "Zweitsprache", "Lehrsprache");

	private ObjectProperty<Talent> ml;
	private ObjectProperty<Talent> sl;
	private ObjectProperty<Talent> tl;
	private ObjectProperty<Talent> mlWriting;
	private final IntegerProperty languageBonus = new SimpleIntegerProperty(0);
	private final IntegerProperty writingBonus = new SimpleIntegerProperty(0);

	private JSONObject hero;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Choices(final JSONObject generationState, final TabPane tabPane, final VBox leftBox, final IntegerProperty gp) {
		super(generationState, gp);
		this.leftBox = leftBox;

		tab = addTab(tabPane, "Auswahl", pane);

		choiceNames = new TableView<>();
		choiceNames.getColumns().add(new TableColumn<>());
		choiceNames.getColumns().add(new TableColumn<>());
		choiceNames.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
		choiceNames.getColumns().get(1).setMinWidth(0);
		choiceNames.getColumns().get(1).setMaxWidth(0);
		choiceNames.getColumns().get(1).setPrefWidth(0);
		choiceNames.getColumns().get(0).prefWidthProperty().bind(choiceNames.widthProperty());
		choiceNames.getColumns().get(0).setResizable(false);
		GUIUtil.cellValueFactories(choiceNames, "text", "valid");
		choiceNames.getStyleClass().add("remove-header");
		choiceNames.autosize();
		choiceNames.getColumns().get(1).setCellFactory(tableColumn -> (TableCell) new TableCell<ChoicePage, Boolean>() {
			@Override
			public void updateItem(final Boolean valid, final boolean empty) {
				super.updateItem(valid, empty);
				final TableRow<ChoicePage> row = getTableRow();
				if (!empty && valid) {
					row.getStyleClass().add("valid");
				} else {
					row.getStyleClass().remove("valid");
				}
			}
		});
		VBox.setVgrow(choiceNames, Priority.ALWAYS);

		choiceNames.getSelectionModel().selectedIndexProperty().addListener((o, oldV, newV) -> {
			if (newV.intValue() > -1) {
				pane.getChildren().get(newV.intValue()).setVisible(true);
				if (oldV.intValue() > -1) {
					pane.getChildren().get(oldV.intValue()).setVisible(false);
				}
			}
		});
	}

	@Override
	public void activate(final boolean forward) {
		tab.setDisable(false);
		tab.getTabPane().getSelectionModel().select(tab);

		leftBox.getChildren().add(0, choiceNames);

		pane.getChildren().clear();
		choiceNames.getItems().clear();
		talents.clear();
		toSelect.clear();

		hero = generationState.getObj("Held");
		final JSONObject race = generationState.getObj("Rasse");
		final JSONObject culture = generationState.getObj("Kultur");
		final JSONObject profession = generationState.getObj("Profession");
		final JSONObject bgb = generationState.getObjOrDefault("Breitgefächerte Bildung", null);

		final JSONObject pros = hero.getObj("Vorteile");
		createSingleInputs("Vorteile", pros, false, race, culture, profession, bgb);
		final JSONObject cons = hero.getObj("Nachteile");
		createSingleInputs("Nachteile", cons, false, race, culture, profession, bgb);
		final JSONObject skills = hero.getObj("Sonderfertigkeiten");
		createSingleInputs("Sonderfertigkeiten", skills, false, race, culture, profession, null);
		final JSONObject cheaperSkills = hero.getObj("Verbilligte Sonderfertigkeiten");
		createSingleInputs("Verbilligte Sonderfertigkeiten", cheaperSkills, false, race, culture, profession, bgb);

		if (bgb != null || generationState.containsKey("Veteran")) {
			final JSONObject bgbVeteran = bgb != null ? bgb : generationState.getObj("Veteran");
			final List<JSONObject> choices = new ArrayList<>();
			getChoices("Sonderfertigkeiten", choices, bgbVeteran);
			for (final JSONObject choice : choices) {
				createSingleChoiceInput("Verbilligte Sonderfertigkeiten", choice, cheaperSkills, false);
			}
		}

		pros.put("temporary:AppliedChoices", true);
		cons.put("temporary:AppliedChoices", true);
		skills.put("temporary:AppliedChoices", true);
		cheaperSkills.put("temporary:AppliedChoices", true);

		final List<JSONObject> valueChoices = new ArrayList<>();
		getChoices("Talente", valueChoices, race, culture, profession, null);

		for (final JSONObject choice : valueChoices) {
			if (choice.containsKey("Punkte")) {
				createPointChoiceInput(choice, false, false);
			} else {
				createValueChoiceInput(choice, false, false);
			}
		}

		final List<JSONObject> primarySpellChoices = new ArrayList<>();
		getChoices("Hauszauber", primarySpellChoices, race, culture, profession, null);

		for (final JSONObject choice : primarySpellChoices) {
			if (choice.containsKey("Punkte")) {
				createPointChoiceInput(choice, true, true);
			} else {
				createValueChoiceInput(choice, true, true);
			}
		}

		final List<JSONObject> spellChoices = new ArrayList<>();
		getChoices("Zauber", spellChoices, race, culture, profession, null);

		for (final JSONObject choice : spellChoices) {
			if (choice.containsKey("Punkte")) {
				createPointChoiceInput(choice, true, false);
			} else {
				createValueChoiceInput(choice, true, false);
			}
		}

		final int KL = hero.getObj("Eigenschaften").getObj("KL").getIntOrDefault("Wert", 0);
		languageBonus.set(race.getObj("Talente").getIntOrDefault("Muttersprache", 0) + culture.getObj("Talente").getIntOrDefault("Muttersprache", 0)
				+ profession.getObj("Talente").getIntOrDefault("Muttersprache", 0));

		writingBonus
				.set(race.getObj("Talente").getIntOrDefault("Muttersprache:Schrift", 0) + culture.getObj("Talente").getIntOrDefault("Muttersprache:Schrift", 0)
						+ profession.getObj("Talente").getIntOrDefault("Muttersprache:Schrift", 0));

		ml = new SimpleObjectProperty<>(null);
		sl = new SimpleObjectProperty<>(null);
		tl = new SimpleObjectProperty<>(null);
		mlWriting = new SimpleObjectProperty<>(null);
		final boolean[] addLanguageBoni = { forward, forward, forward, forward };

		ml.addListener((o, oldV, newV) -> {
			if (oldV != null) {
				oldV.getActual().removeKey("Muttersprache");
				if (oldV == tl.get()) {
					oldV.setValue(oldV.getValue() - 4 - languageBonus.get());
				} else {
					oldV.setValue(oldV.getValue() - KL + 2 - languageBonus.get());
				}
			}
			if (newV != null) {
				if (addLanguageBoni[0]) {
					if (newV == tl.get()) {
						newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + 4 + languageBonus.get());
					} else {
						newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + KL - 2 + languageBonus.get());
					}
				}
				addLanguageBoni[0] = true;
				newV.getActual().put("Muttersprache", true);
				if (talents.containsKey("Muttersprache")) {
					((ProxyTalent) talents.get("Muttersprache")).changeTalent(newV, hero.getObj("Talente").getObj("Sprachen und Schriften"));
				}
			}
		});
		sl.addListener((o, oldV, newV) -> {
			if (oldV != null) {
				oldV.getActual().removeKey("Zweitsprache");
				if (oldV != tl.get()) {
					oldV.setValue(oldV.getValue() - KL + 4);
				}
			}
			if (newV != null) {
				if (addLanguageBoni[1]) {
					if (newV != tl.get()) {
						newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + KL - 4);
					}
				}
				addLanguageBoni[1] = true;
				newV.getActual().put("Zweitsprache", true);
			}
		});
		tl.addListener((o, oldV, newV) -> {
			if (oldV != null) {
				oldV.getActual().removeKey("Lehrsprache");
				if (oldV == ml.get()) {
					oldV.setValue(oldV.getValue() - 4);
				} else if (oldV != sl.get()) {
					oldV.setValue(oldV.getValue() - KL + 4);
				}
			}
			if (newV != null) {
				if (addLanguageBoni[2]) {
					if (newV == ml.get()) {
						newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + 4);
					} else if (newV != sl.get()) {
						newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + KL - 4);
					}
				}
				addLanguageBoni[2] = true;
				newV.getActual().put("Lehrsprache", true);
			}
		});
		mlWriting.addListener((o, oldV, newV) -> {
			if (oldV != null) {
				oldV.getActual().removeKey("Muttersprache");
				oldV.setValue(oldV.getValue() - writingBonus.get());
			}
			if (newV != null) {
				if (addLanguageBoni[3]) {
					newV.setValue((newV.getValue() == Integer.MIN_VALUE ? 0 : newV.getValue()) + writingBonus.get());
				}
				addLanguageBoni[3] = true;
				newV.getActual().put("Muttersprache", true);
				if (talents.containsKey("Muttersprache:Schrift")) {
					((ProxyTalent) talents.get("Muttersprache:Schrift")).changeTalent(newV, hero.getObj("Talente").getObj("Sprachen und Schriften"));
				}
			}
		});
		languageBonus.addListener((o, oldV, newV) -> {
			final Talent language = ml.get();
			if (language != null) {
				language.setValue((language.getValue() == Integer.MIN_VALUE ? 0 : language.getValue()) - oldV.intValue() + newV.intValue());
			}
		});
		writingBonus.addListener((o, oldV, newV) -> {
			final Talent writing = mlWriting.get();
			if (writing != null) {
				writing.setValue((writing.getValue() == Integer.MIN_VALUE ? 0 : writing.getValue()) - oldV.intValue() + newV.intValue());
			}
		});

		createLanguageChoice(race.getObj("Sprachen"));
		createLanguageChoice(culture.getObj("Sprachen"));
		createLanguageChoice(profession.getObj("Sprachen"));

		final JSONArray inventory = hero.getObj("Besitz").getArr("Ausrüstung");
		createSingleInputs("Ausrüstung", inventory, true, race, culture, profession, null);

		if (!inventory.contains("temporary:AppliedChoices")) {
			inventory.add("temporary:AppliedChoices");
		}

		if (!choiceNames.getItems().isEmpty()) {
			choiceNames.getSelectionModel().select(0);
		}

		recalculateCanContinue();
	}

	private void createLanguageChoice(final JSONObject languages) {
		final List<Tuple<String, JSONArray>> choices = new ArrayList<>(3);
		final Set<String> actualLanguages = new LinkedHashSet<>();

		for (final String type : languageTypes) {
			final JSONArray languageChoices = languages.getArr(type);
			for (int i = 0; i < languageChoices.size(); ++i) {
				final JSONArray currentLanguages = languageChoices.getArr(i);
				if (currentLanguages.size() <= 1) {
					if (currentLanguages.size() == 1) {
						final String language = currentLanguages.getString(0);
						final Talent actualTalent = getTalent(language, null);
						switch (type) {
							case "Muttersprache":
								ml.set(actualTalent);
								mlWriting.set(getTalent(languages.getObj("Muttersprache:Schrift").getStringOrDefault(language, null), null));
								break;
							case "Zweitsprache":
								if (languages.getBoolOrDefault("Leittalent", false)) {
									actualTalent.setPrimaryTalent(true);
								}
								sl.set(actualTalent);
								break;
							case "Lehrsprache":
								if (languages.getBoolOrDefault("Leittalent", false)) {
									actualTalent.setPrimaryTalent(true);
								}
								tl.set(actualTalent);
								break;
						}
					}
					continue;
				}
				choices.add(new Tuple<>(type, currentLanguages));
				for (int j = 0; j < currentLanguages.size(); ++j) {
					actualLanguages.add(currentLanguages.getString(j));
				}
			}
		}

		if (choices.isEmpty()) return;

		final ScrollPane scrollPane = new ScrollPane();
		final GridPane input = new GridPane();
		scrollPane.setContent(input);
		scrollPane.setVisible(false);
		scrollPane.setFitToHeight(true);
		scrollPane.setFitToWidth(true);
		input.setAlignment(Pos.CENTER);
		input.setHgap(5);
		input.setVgap(5);

		final int numChoices = choices.size();

		JSONArray chosen;
		if (languages.containsKey("Ausgewählt")) {
			chosen = languages.getArr("Ausgewählt");
		} else {
			chosen = new JSONArray(languages);
			for (int i = 0; i < numChoices; ++i) {
				chosen.add(-1);
			}
			languages.put("Ausgewählt", chosen);
		}

		final ToggleGroup[] groups = new ToggleGroup[numChoices];
		BooleanExpression allValid = new SimpleBooleanProperty(true);

		int i = 0;
		for (final Tuple<String, JSONArray> choice : choices) {
			final int finalI = i;
			final BooleanProperty isValid = new SimpleBooleanProperty(false);
			toSelect.add(isValid);
			allValid = allValid.and(isValid);
			groups[i] = new ToggleGroup();
			groups[i].selectedToggleProperty().addListener((o, oldV, newV) -> {
				final int index = GridPane.getRowIndex((Node) newV);

				if (oldV == null) {
					isValid.set(true);
					recalculateCanContinue();
				}
				for (int j = 0; j < numChoices; j++) {
					if (finalI != j) {
						final ToggleGroup group = groups[j];
						if (group.getSelectedToggle() != null && index == GridPane.getRowIndex((Node) group.getSelectedToggle())) {
							group.selectToggle(group.getToggles().get((group.getToggles().indexOf(group.getSelectedToggle()) + 1) % group.getToggles().size()));
						}
					}
				}
				chosen.set(finalI, index - 1);
			});
			final Label label = new Label(choice._1);
			label.setAlignment(Pos.CENTER);
			input.add(label, i + 2, 0);
			++i;
		}

		i = 0;
		for (final String language : actualLanguages) {
			input.add(new Label(language), 0, i + 1);

			final Talent actualTalent = getTalent(language, null);

			final Label currentValue = new Label();
			currentValue.setPrefWidth(20);
			currentValue.setAlignment(Pos.CENTER);
			input.add(currentValue, 1, i + 1);
			currentValue.textProperty()
					.bind(Bindings.when(actualTalent.valueProperty().lessThan(Integer.MIN_VALUE + 1)).then("n.a.")
							.otherwise(actualTalent.valueProperty().asString()));

			for (int j = 0; j < choices.size(); ++j) {
				final Tuple<String, JSONArray> choice = choices.get(j);
				final JSONArray langsFromChoice = choice._2;
				if (langsFromChoice.contains(language)) {
					final RadioButton check = new RadioButton();
					check.setToggleGroup(groups[j]);
					input.add(check, j + 2, i + 1);
					GridPane.setHalignment(check, HPos.CENTER);
					check.selectedProperty().addListener((o, oldV, newV) -> {
						actualTalent.setPrimaryTalent(false);
						if (newV) {
							switch (choice._1) {
								case "Muttersprache":
									ml.set(actualTalent);
									mlWriting.set(getTalent(languages.getObj("Muttersprache:Schrift").getStringOrDefault(language, null), null));
									break;
								case "Zweitsprache":
									if (languages.getBoolOrDefault("Leittalent", false)) {
										actualTalent.setPrimaryTalent(newV);
									}
									sl.set(actualTalent);
									break;
								case "Lehrsprache":
									if (languages.getBoolOrDefault("Leittalent", false)) {
										actualTalent.setPrimaryTalent(newV);
									}
									tl.set(actualTalent);
									break;
							}
						}
					});
					if (chosen.getInt(j).equals(i)) {
						check.setSelected(true);
					}
				}
			}
			++i;
		}

		choiceNames.getItems().add(new ChoicePage(String.join(", ", actualLanguages), allValid));
		pane.getChildren().add(scrollPane);
	}

	private void createPointChoiceInput(final JSONObject choices, final boolean spells, final boolean primarySpells) {
		final boolean useComplexity = choices.getBoolOrDefault("Verrechnungspunkte", false);
		final IntegerProperty points = new SimpleIntegerProperty(choices.getIntOrDefault("Punkte", 0));

		final VBox box = new VBox();
		box.setVisible(false);
		box.setFillWidth(true);

		final Label pointsLabel = new Label();
		pointsLabel.textProperty().bind(points.asString());
		final HBox panel = new HBox();
		panel.getChildren().add(new Label("Verteile "));
		panel.getChildren().add(pointsLabel);
		panel.getChildren().add(new Label((useComplexity ? " Verrechnungspunkte" : " Punkte")
				+ (choices.containsKey("Anzahl:Maximum") ? " auf maximal " + choices.getInt("Anzahl:Maximum") + (spells ? " Zauber" : " Talente") : "")
				+ (choices.containsKey("Maximum") ? " (je maximal " + choices.getInt("Maximum") + " Punkte)" : "")));
		box.getChildren().add(panel);

		final ScrollPane scrollPane = new ScrollPane();
		final GridPane input = new GridPane();
		scrollPane.setContent(input);
		scrollPane.setFitToWidth(true);
		input.setAlignment(Pos.CENTER);
		input.setHgap(5);
		input.setVgap(5);

		final JSONValue actualChoices = spells ? choices.getObj("Wahl") : choices.getArr("Wahl");

		final JSONObject initialChosen = choices.getObjOrDefault("Ausgewählt", null);
		JSONObject chosen;
		if (initialChosen == null) {
			chosen = new JSONObject(choices);
			choices.put("Ausgewählt", chosen);
		} else {
			chosen = initialChosen;
		}

		final StringBuilder names = new StringBuilder();

		final IntegerProperty canSelect = new SimpleIntegerProperty(choices.getIntOrDefault("Anzahl:Maximum", actualChoices.size()));

		String[] spellNames = null;
		final boolean needsPrimarySpells = spells && choices.getIntOrDefault("Hauszauber", 0) > 0;
		final IntegerProperty availablePrimarySpells = new SimpleIntegerProperty(choices.getIntOrDefault("Hauszauber", 0));
		final JSONArray chosenPrimarySpells;
		if (spells) {
			final Set<String> keySet = ((JSONObject) actualChoices).keySet();
			spellNames = keySet.toArray(new String[keySet.size()]);
			if (needsPrimarySpells) {
				final Label primarySpellsLabel = new Label();
				primarySpellsLabel.textProperty().bind(availablePrimarySpells.asString());
				final HBox primarySpellsPanel = new HBox();
				primarySpellsPanel.getChildren().add(new Label("Wähle "));
				primarySpellsPanel.getChildren().add(primarySpellsLabel);
				primarySpellsPanel.getChildren().add(new Label(" Hauszauber"));
				box.getChildren().add(primarySpellsPanel);

				input.add(new Label("Hauszauber"), 3, 0);

				if (choices.containsKey("Ausgewählt:Hauszauber")) {
					chosenPrimarySpells = choices.getArr("Ausgewählt:Hauszauber");
				} else {
					chosenPrimarySpells = new JSONArray(choices);
					for (int i = 0; i < actualChoices.size(); ++i) {
						chosenPrimarySpells.add(false);
					}
					choices.put("Ausgewählt:Hauszauber", chosenPrimarySpells);
				}
			} else {
				chosenPrimarySpells = null;
			}
		} else {
			chosenPrimarySpells = null;
		}

		final boolean needsPrimaryTalents = choices.getIntOrDefault("Leittalente", 0) > 0 && hero.getObj("Nachteile").containsKey("Elfische Weltsicht");
		final IntegerProperty availablePrimaryTalents = new SimpleIntegerProperty(choices.getIntOrDefault("Leittalente", 0));
		final JSONArray chosenPrimaryTalents;
		if (needsPrimaryTalents) {
			final Label primaryTalentsLabel = new Label();
			primaryTalentsLabel.textProperty().bind(availablePrimaryTalents.asString());
			final HBox primaryTalentsPanel = new HBox();
			primaryTalentsPanel.getChildren().add(new Label("Wähle "));
			primaryTalentsPanel.getChildren().add(primaryTalentsLabel);
			primaryTalentsPanel.getChildren().add(new Label(" Leittalente"));
			box.getChildren().add(primaryTalentsPanel);

			input.add(new Label("Leittalent"), needsPrimarySpells ? 4 : 3, 0);

			if (choices.containsKey("Ausgewählt:Leittalente")) {
				chosenPrimaryTalents = choices.getArr("Ausgewählt:Leittalente");
			} else {
				chosenPrimaryTalents = new JSONArray(choices);
				for (int i = 0; i < actualChoices.size(); ++i) {
					chosenPrimaryTalents.add(false);
				}
				choices.put("Ausgewählt:Leittalente", chosenPrimaryTalents);
			}
		} else {
			chosenPrimaryTalents = null;
		}

		final SimpleBooleanProperty isValid = new SimpleBooleanProperty(false);
		toSelect.add(isValid);
		BooleanBinding valid = points.isEqualTo(0);
		if (needsPrimarySpells) {
			valid = valid.and(availablePrimarySpells.isEqualTo(0));
		}
		if (needsPrimaryTalents) {
			valid = valid.and(availablePrimaryTalents.isEqualTo(0));
		}
		isValid.bind(valid);

		int current = 0;
		int row = 1;
		for (int i = 0; i < actualChoices.size(); ++i) {
			final String name = spells ? spellNames[i] : ((JSONArray) actualChoices).getString(i);
			List<String> actualNames = new LinkedList<>();

			if ("Fremdsprache".equals(name)) {
				final JSONObject langs = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
				for (final String lang : langs.keySet()) {
					if (!langs.getObj(lang).getBoolOrDefault("Schrift", false)) {
						actualNames.add(lang);
					}
				}
			} else if ("Fremdschrift".equals(name)) {
				final JSONObject langs = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
				for (final String lang : langs.keySet()) {
					if (langs.getObj(lang).getBoolOrDefault("Schrift", false)) {
						actualNames.add(lang);
					}
				}
			} else {
				actualNames = Collections.singletonList(name);
			}

			final String rep = spells ? ((JSONObject) actualChoices).getString(name) : null;

			for (final String actualName : actualNames) {
				final Talent actualTalent = getTalent(actualName, rep);

				final String talentName = actualTalent.getName();
				names.append(talentName);
				names.append(", ");
				final Label nameLabel = new Label(talentName);
				input.add(nameLabel, 0, row);
				nameLabel.setMinWidth(250);
				nameLabel.setPadding(new Insets(0, 10, 0, 10));
				if (spells) {
					nameLabel.setText(talentName + " (" + ((Spell) actualTalent).getComplexity() + ")");
					JSONArray traits = actualTalent.getTalent().getArrOrDefault("Merkmale", null);
					if (traits == null) {
						traits = ((JSONObject) actualTalent.getTalent().getParent()).getArr("Merkmale");
					}
					nameLabel.setTooltip(new Tooltip("(" + String.join(", ", traits.getStrings()) + ")"));
				}

				final String choiceName = spells ? talentName + rep : talentName;

				final Label currentValue = new Label(actualTalent.getValue() == Integer.MIN_VALUE ? "n.a." : Integer.toString(actualTalent.getValue()));
				currentValue.setMinWidth(25);
				currentValue.setAlignment(Pos.CENTER);
				input.add(currentValue, 1, row);
				actualTalent.valueProperty().addListener((o, oldV, newV) -> {
					if (newV.intValue() == Integer.MIN_VALUE) {
						currentValue.setText("n.a.");
					} else {
						currentValue.setText(newV.toString());
					}
				});

				final ObservableList<String> possible = FXCollections.observableArrayList("n.a.");
				for (int j = choices.getIntOrDefault("Minimum", useComplexity ? 0 : 1); j <= choices.getIntOrDefault("Maximum",
						choices.getIntOrDefault("Punkte", 0)); ++j) {
					possible.add(Integer.toString(j));
				}
				final ReactiveSpinner<String> value = new ReactiveSpinner<>(possible);
				value.setMinWidth(70);
				value.setPrefWidth(70);
				input.add(value, 2, row);

				canSelect.addListener((o, oldV, newV) -> {
					if (newV.intValue() <= 0 && "n.a.".equals(value.getValue())) {
						value.setDisable(true);
					} else {
						value.setDisable(false);
					}
				});

				final ValueChoice actualChoice = new ValueChoice(actualTalent, points, useComplexity, rep, primarySpells);
				final int chosenValue = chosen.getIntOrDefault(choiceName, -1);
				value.getValueFactory().setValue(chosenValue == -1 ? "n.a." : Integer.toString(chosenValue));
				if (chosenValue == -1) {
					actualChoice.value = null;
				} else {
					actualChoice.value = Integer.parseInt(value.getValue());
					canSelect.set(canSelect.get() - 1);
				}
				actualChoice.apply(hero, true);
				final int finalC = current;
				value.valueProperty().addListener((o, oldV, newV) -> {
					actualChoice.unapply(hero);
					actualChoice.value = "n.a.".equals(value.getValue()) ? null : Integer.parseInt(value.getValue());
					actualChoice.apply(hero, false);
					if ("n.a.".equals(value.getValue())) {
						chosen.removeKey(choiceName);
					} else {
						chosen.put(choiceName, Integer.parseInt(value.getValue()));
					}
					if ("n.a.".equals(newV) && !"n.a.".equals(oldV)) {
						canSelect.set(canSelect.get() + 1);
					} else if ("n.a.".equals(oldV) && !"n.a.".equals(newV)) {
						canSelect.set(canSelect.get() - 1);
					}
					recalculateCanContinue();
				});
				actualTalent.valueProperty().addListener((o, oldV, newV) -> {
					if (newV.intValue() == Integer.MIN_VALUE && "0".equals(value.getValue())) {
						actualTalent.setValue(0);
						actualTalent.getActual().put("temporary:ChoiceOnly", true);
					}
				});

				final CheckBox primarySpell = needsPrimarySpells ? new CheckBox() : null;
				final CheckBox primaryTalent = needsPrimaryTalents ? new CheckBox() : null;

				if (needsPrimarySpells) {
					input.add(primarySpell, 3, row);
					GridPane.setHalignment(primarySpell, HPos.CENTER);

					final SimpleBooleanProperty isExternallySet = new SimpleBooleanProperty(false);

					availablePrimarySpells.addListener((o, oldV, newV) -> {
						if (isExternallySet.get() || newV.intValue() <= 0 && !primarySpell.isSelected()) {
							primarySpell.setDisable(true);
						} else {
							primarySpell.setDisable(false);
						}
					});

					final boolean[] isSetup = { true };

					primarySpell.selectedProperty().addListener((o, oldV, newV) -> {
						if (!isExternallySet.get()) {
							chosenPrimarySpells.set(finalC, newV);
							((Spell) actualTalent).setPrimarySpell(newV);
							if (needsPrimaryTalents && !primaryTalent.isSelected()) {
								primaryTalent.setDisable(newV);
							}
							availablePrimarySpells.set(availablePrimarySpells.get() - (newV ? 1 : -1));
							if (!"n.a.".equals(value.getValue()) && !isSetup[0]) {
								points.set(points.get() + (Integer.parseInt(value.getValue()) + 1) * (newV ? 1 : -1));
							}
							recalculateCanContinue();
						}
					});

					if (((Spell) actualTalent).isPrimarySpell()) {
						if (!chosenPrimarySpells.getBool(finalC)) {
							isExternallySet.set(true);
							primarySpell.setDisable(true);
						}
						primarySpell.setSelected(true);
					}

					isSetup[0] = false;

					((Spell) actualTalent).primarySpellProperty().addListener((o, oldV, newV) -> {
						if (newV && !chosenPrimarySpells.getBool(finalC)) {
							isExternallySet.set(true);
							primarySpell.setDisable(true);
							if (needsPrimaryTalents) {
								primaryTalent.setDisable(true);
							}
						}
						primarySpell.setSelected(newV);
						if (!newV) {
							isExternallySet.set(false);
							if (availablePrimarySpells.get() > 0 && (!needsPrimaryTalents || !primaryTalent.isSelected())) {
								primarySpell.setDisable(false);
							}
							if (needsPrimaryTalents && !primaryTalent.isSelected() && availablePrimaryTalents.get() > 0) {
								primaryTalent.setDisable(false);
							}
						}
					});
				}

				if (needsPrimaryTalents) {
					input.add(primaryTalent, needsPrimarySpells ? 4 : 3, row);
					GridPane.setHalignment(primaryTalent, HPos.CENTER);

					final SimpleBooleanProperty isExternallySet = new SimpleBooleanProperty(false);

					availablePrimaryTalents.addListener((o, oldV, newV) -> {
						if (isExternallySet.get() || newV.intValue() <= 0 && !primaryTalent.isSelected()
								|| needsPrimarySpells && primarySpell.isSelected()) {
							primaryTalent.setDisable(true);
						} else {
							primaryTalent.setDisable(false);
						}
					});

					primaryTalent.selectedProperty().addListener((o, oldV, newV) -> {
						if (!isExternallySet.get()) {
							chosenPrimaryTalents.set(finalC, newV);
							actualTalent.setPrimaryTalent(newV);
							if (needsPrimarySpells) {
								primarySpell.setDisable(newV);
							}
							availablePrimaryTalents.set(availablePrimaryTalents.get() - (newV ? 1 : -1));
							recalculateCanContinue();
						}
					});

					if (actualTalent.isPrimaryTalent()) {
						if (!chosenPrimaryTalents.getBool(finalC)) {
							isExternallySet.set(true);
							primaryTalent.setDisable(true);
						}
						primaryTalent.setSelected(true);
					}

					actualTalent.primaryTalentProperty().addListener((o, oldV, newV) -> {
						if (newV && !chosenPrimaryTalents.getBool(finalC)) {
							isExternallySet.set(true);
							primaryTalent.setDisable(true);
						}
						primaryTalent.setSelected(newV);
						if (!newV) {
							isExternallySet.set(false);
							if (availablePrimaryTalents.get() > 0 && (!needsPrimarySpells || !primarySpell.isSelected())) {
								primaryTalent.setDisable(false);
							}
							if (needsPrimarySpells && availablePrimarySpells.get() > 0) {
								primarySpell.setDisable(false);
							}
						}
					});
				}
				++row;
			}
			++current;
		}

		// Issue a change in canSelect to disable excess controls
		canSelect.set(canSelect.get() + 1);
		canSelect.set(canSelect.get() - 1);
		availablePrimarySpells.set(availablePrimarySpells.get() + 1);
		availablePrimarySpells.set(availablePrimarySpells.get() - 1);
		availablePrimaryTalents.set(availablePrimaryTalents.get() + 1);
		availablePrimaryTalents.set(availablePrimaryTalents.get() - 1);

		names.delete(names.length() - 2, names.length());

		box.getChildren().add(scrollPane);
		choiceNames.getItems().add(new ChoicePage(names.toString(), isValid));
		pane.getChildren().add(box);
	}

	private void createSingleChoiceInput(final String targetName, final JSONObject choices, final JSONValue target, final boolean isEquipment) {
		final ScrollPane scrollPane = new ScrollPane();
		final GridPane input = new GridPane();
		scrollPane.setContent(input);
		scrollPane.setVisible(false);
		scrollPane.setFitToHeight(true);
		scrollPane.setFitToWidth(true);
		input.setAlignment(Pos.CENTER);
		input.setHgap(5);
		input.setVgap(5);

		final String modifiedTargetName = switch (targetName) {
			case "Vorteile" -> "n Vorteil";
			case "Nachteile" -> "n Nachteil";
			case "Sonderfertigkeiten" -> " Sonderfertigkeit";
			case "Verbilligte Sonderfertigkeiten" -> " verbilligte Sonderfertigkeit";
			default -> "n Ausrüstungsgegenstand";
		};
		input.add(new Label("Wähle eine" + modifiedTargetName + " aus:"), 0, 0, 2, 1);

		final StringBuilder names = new StringBuilder();
		final ToggleGroup group = new ToggleGroup();

		final SimpleBooleanProperty isValid = new SimpleBooleanProperty(false);
		toSelect.add(isValid);

		group.selectedToggleProperty().addListener((o, oldV, newV) -> {
			if (newV != null) {
				isValid.set(true);
				recalculateCanContinue();
			}
			choices.put("Ausgewählt", group.getToggles().indexOf(newV));
		});

		final int[] i = { 0 };
		for (final String choiceName : choices.keySet()) {
			if ("Ausgewählt".equals(choiceName)) {
				continue;
			}

			final Consumer<JSONObject> create = choice -> {
				final String name;
				if (isEquipment) {
					name = choiceName;
				} else {
					name = DSAUtil.printProOrCon(choice, choiceName, HeroUtil.findProConOrSkill(choiceName)._1, true);
				}
				names.append(name);
				names.append(", ");
				input.add(new Label(name), 0, i[0] + 2);
				final RadioButton check = new RadioButton();
				check.setToggleGroup(group);
				input.add(check, 1, i[0] + 2);
				final Choice actualChoice = isEquipment ? new InventoryChoice(choiceName, choice)
						: new ProConSkillChoice(choiceName, choice, (JSONObject) target, "Verbilligte Sonderfertigkeiten".equals(targetName));
				if (choices.getIntOrDefault("Ausgewählt", -1) == i[0]) {
					check.setSelected(true);
					actualChoice.apply(hero, isEquipment ? ((JSONArray) target).contains("temporary:AppliedChoices")
							: ((JSONObject) target).containsKey("temporary:AppliedChoices"));
				}
				final int currentI = i[0];
				check.selectedProperty().addListener((o, oldV, newV) -> {
					if (newV) {
						actualChoice.apply(hero, false);
						choices.put("Ausgewählt", currentI);
					} else {
						actualChoice.unapply(hero);
					}
				});
				++i[0];
			};

			final JSONObject actual = isEquipment ? null : HeroUtil.findProConOrSkill(choiceName)._1;
			if (actual != null && (actual.containsKey("Auswahl") || actual.containsKey("Freitext"))) {
				final JSONArray choice = choices.getArr(choiceName);
				for (int j = 0; j < choice.size(); ++j) {
					create.accept(choice.getObj(j));
				}
			} else {
				create.accept(choices.getObj(choiceName));
			}
		}

		names.delete(names.length() - 2, names.length());

		choiceNames.getItems().add(new ChoicePage(names.toString(), isValid));
		pane.getChildren().add(scrollPane);
	}

	private void createSingleInputs(final String name, final JSONValue target, final boolean isEquipment, final JSONObject race, final JSONObject culture,
			final JSONObject profession, final JSONObject bgb) {
		final List<JSONObject> choices = new ArrayList<>();
		getChoices(name, choices, race, culture, profession, bgb);

		for (final JSONObject choice : choices) {
			createSingleChoiceInput(name, choice, target, isEquipment);
		}
	}

	private void createValueChoiceInput(final JSONObject choice, final boolean spells, final boolean primarySpells) {
		final ScrollPane scrollPane = new ScrollPane();
		final GridPane input = new GridPane();
		scrollPane.setContent(input);
		scrollPane.setVisible(false);
		scrollPane.setFitToHeight(true);
		scrollPane.setFitToWidth(true);
		input.setAlignment(Pos.CENTER);
		input.setHgap(5);
		input.setVgap(5);

		final StringBuilder names = new StringBuilder();
		int indention = 0;
		int additionalRows = 0;

		final JSONArray alternatives = choice.getArrOrDefault("Oder", null);

		final int numGroups = alternatives == null ? 1 : alternatives.size();
		@SuppressWarnings("unchecked")
		final LinkedList<ToggleGroup>[] groups = new LinkedList[numGroups];
		for (int i = 0; i < numGroups; ++i) {
			groups[i] = new LinkedList<>();
		}

		final SimpleBooleanProperty isValid = new SimpleBooleanProperty(false);
		toSelect.add(isValid);

		boolean allZero = true;

		for (int i = 0; i < (alternatives == null ? 1 : alternatives.size()); ++i) {
			final int finalI = i;

			final JSONObject choices = alternatives == null ? choice : alternatives.getObj(i);

			final JSONValue actualChoices = spells ? choices.getObj("Wahl") : choices.getArr("Wahl");
			final JSONArray values = choices.getArr("Werte");
			final int numValues = values.size();

			JSONArray chosen;
			if (choices.containsKey("Ausgewählt")) {
				chosen = choices.getArr("Ausgewählt");
			} else {
				chosen = new JSONArray(choices);
				for (int j = 0; j < numValues; ++j) {
					chosen.add(-1);
				}
				choices.put("Ausgewählt", chosen);
			}

			for (int j = 0; j < numValues; ++j) {
				final int finalJ = j;
				final ToggleGroup current = new ToggleGroup();
				groups[i].add(current);
				current.selectedToggleProperty().addListener((o, oldV, newV) -> {
					final int index = newV == null ? -1 : newV.getToggleGroup().getToggles().indexOf(newV);

					if (!choices.getBoolOrDefault("Mehrfach", false)) {
						for (int k = 0; k < numValues; k++) {
							if (finalJ != k) {
								final ToggleGroup col = groups[finalI].get(k);
								if (index == col.getToggles().indexOf(col.getSelectedToggle())) {
									col.selectToggle(col.getToggles().get((index + 1) % actualChoices.size()));
								}
							}
						}
					}
					if (index != -1) {
						groups: for (int k = 0; k < numGroups; ++k) {
							if (k == finalI) {
								final LinkedList<ToggleGroup> group = groups[k];
								for (final ToggleGroup tg : group) {
									if (tg.getSelectedToggle() == null) {
										isValid.set(false);
										canContinue.set(false);
										continue groups;
									}
								}
								isValid.set(true);
								recalculateCanContinue();
							} else {
								final LinkedList<ToggleGroup> group = groups[k];
								for (final ToggleGroup tg : group) {
									tg.selectToggle(null);
								}
							}
						}
					}
					chosen.set(finalJ, index);
				});
				final int value = values.getInt(j);
				if (value != 0) {
					allZero = false;
				}
				final Label label = new Label(value != 0 ? Util.getSignedIntegerString(value) : "L");
				label.setAlignment(Pos.CENTER);
				input.add(label, j + 2 + indention, 0);
			}

			String[] spellNames = null;
			String rep = null;
			if (spells) {
				final Set<String> keySet = ((JSONObject) actualChoices).keySet();
				spellNames = keySet.toArray(new String[keySet.size()]);
			}
			int current = 0;
			for (int j = 0; j < actualChoices.size(); ++j) {
				final String name = spells ? spellNames[j] : ((JSONArray) actualChoices).getString(j);
				List<String> actualNames = new LinkedList<>();
				if ("Fremdsprache".equals(name)) {
					final JSONObject langs = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
					for (final String lang : langs.keySet()) {
						if (!langs.getObj(lang).getBoolOrDefault("Schrift", false)) {
							actualNames.add(lang);
						}
					}
				} else if ("Fremdschrift".equals(name)) {
					final JSONObject langs = ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften");
					for (final String lang : langs.keySet()) {
						if (langs.getObj(lang).getBoolOrDefault("Schrift", false)) {
							actualNames.add(lang);
						}
					}
				} else {
					actualNames = Collections.singletonList(name);
				}
				if (spells) {
					rep = ((JSONObject) actualChoices).getString(name);
				}
				for (final String actualName : actualNames) {
					final Talent actualTalent = getTalent(actualName, rep);

					if (allZero && actualTalent.getActual().getBoolOrDefault("temporary:RKPPrimaryTalent", false)) {
						continue;
					}

					input.add(new Label(actualTalent.getDisplayName()), 0, current + 1 + additionalRows);
					names.append(actualTalent.getName());
					names.append(", ");

					final Label currentValue = new Label();
					currentValue.setPrefWidth(25);
					currentValue.setAlignment(Pos.CENTER);
					input.add(currentValue, 1, current + 1 + additionalRows);
					currentValue.textProperty().bind(Bindings.when(actualTalent.valueProperty().lessThan(Integer.MIN_VALUE + 1)).then("n.a.")
							.otherwise(actualTalent.valueProperty().asString()));

					for (int k = 0; k < numValues; ++k) {
						final RadioButton check = new RadioButton();
						check.setToggleGroup(groups[i].get(k));
						input.add(check, k + 2 + indention, current + 1 + additionalRows);

						final int value = values.getInt(k);
						if (value == 0 && actualTalent.getActual().getBoolOrDefault("temporary:RKPPrimaryTalent", false)) {
							check.setDisable(true);
						}

						final TalentChoice actualChoice = new TalentChoice(actualTalent, value,
								primarySpells || choices.getArrOrDefault("Hauszauber", new JSONArray(null)).contains(k));
						if (chosen.getInt(k).equals(current)) {
							check.setSelected(true);
							actualChoice.apply(hero, true);
						}
						check.selectedProperty().addListener((o, oldV, newV) -> {
							if (choices.getBoolOrDefault("Leittalent", false)) {
								actualTalent.setPrimaryTalent(newV);
							}
							if (newV) {
								actualChoice.apply(hero, false);
							} else {
								actualChoice.unapply(hero);
							}
						});
					}
					++current;
				}
			}

			indention += numValues;
			additionalRows += actualChoices.size();
		}

		names.delete(names.length() - 2, names.length());

		choiceNames.getItems().add(new ChoicePage(names.toString(), isValid));
		pane.getChildren().add(scrollPane);
	}

	@Override
	public void deactivate(final boolean forward) {
		tab.setDisable(true);

		if (!forward) {
			ml.set(null);
			sl.set(null);
			tl.set(null);
			mlWriting.set(null);
		}

		leftBox.getChildren().remove(0);
	}

	private void getChoices(final String choice, final List<JSONObject> choices, final JSONObject source) {
		final JSONObject actual = source.getObjOrDefault(choice, null);
		if (actual != null && actual.containsKey("Wahl")) {
			final JSONArray currentChoices = actual.getArr("Wahl");
			for (int i = 0; i < currentChoices.size(); ++i) {
				choices.add(currentChoices.getObj(i));
			}
		}
	}

	private void getChoices(final String choice, final List<JSONObject> singleChoices, final JSONObject race, final JSONObject culture,
			final JSONObject profession, final JSONObject bgb) {
		getChoices(choice, singleChoices, race);
		getChoices(choice, singleChoices, culture);
		getChoices(choice, singleChoices, profession);
		if (bgb != null) {
			getChoices(choice, singleChoices, bgb);
		}
	}

	private Talent getTalent(final String name, final String representation) {
		if (name == null) return null;
		Talent actualTalent;
		if (talents.containsKey(name))
			return talents.get(name);
		else if ("Muttersprache".equals(name)) {
			actualTalent = new ProxyTalent("Muttersprache", ml.get() == null ? null : getTalent(ml.get().getName(), null),
					hero.getObj("Talente").getObj("Sprachen und Schriften"), languageBonus);
		} else if ("Muttersprache:Schrift".equals(name)) {
			actualTalent = new ProxyTalent("L/S Muttersprache", mlWriting.get() == null ? null : getTalent(mlWriting.get().getName(), null),
					hero.getObj("Talente").getObj("Sprachen und Schriften"), writingBonus);
		} else {
			final Tuple<JSONObject, String> talentAndGroup = HeroUtil.findTalent(name);
			final JSONObject currentTalent = talentAndGroup._1;
			final JSONObject group = representation != null ? hero.getObj("Zauber") : hero.getObj("Talente").getObj(talentAndGroup._2);
			JSONValue actual = HeroUtil.findActualTalent(hero, name)._1;
			JSONObject talentGroup = ResourceManager.getResource("data/Talentgruppen").getObj(talentAndGroup._2);
			if ("Sprachen und Schriften".equals(talentAndGroup._2)) {
				talentGroup = talentGroup
						.getObj(ResourceManager.getResource("data/Talente").getObj("Sprachen und Schriften").getObj(name).getBoolOrDefault("Schrift", false)
								? "Schriften" : "Sprachen");
			}
			if (actual == null) {
				final JSONObject talent;

				if (representation != null) {
					actual = new JSONObject(group);
					group.put(name, (JSONObject) actual);
					if (currentTalent.containsKey("Auswahl") || currentTalent.containsKey("Freitext")) {
						final JSONArray rep = new JSONArray(actual);
						talent = new JSONObject(rep);
						rep.add(talent);
						((JSONObject) actual).put(representation, rep);
					} else {
						talent = new JSONObject(actual);
						((JSONObject) actual).put(representation, talent);
					}
					talent.put("aktiviert", false);
					actualTalent = Spell.getSpell(name, currentTalent, talent, (JSONObject) actual, group, representation);
				} else {
					if (currentTalent.containsKey("Auswahl") || currentTalent.containsKey("Freitext")) {
						actual = new JSONArray(group);
						group.put(name, (JSONArray) actual);
						talent = new JSONObject(actual);
						((JSONArray) actual).add(talent);
					} else {
						talent = new JSONObject(group);
						group.put(name, talent);
					}
					if (!currentTalent.getBoolOrDefault("Basis", false)) {
						talent.put("aktiviert", false);
					}
					actualTalent = Talent.getTalent(name, talentGroup, currentTalent, talent, group);
				}
				talent.put("temporary:ChoiceOnly", true);
			} else if (currentTalent.containsKey("Auswahl") || currentTalent.containsKey("Freitext")) {
				actualTalent = representation != null
						? Spell.getSpell(name, currentTalent, ((JSONObject) actual).getArr(representation).getObj(0), (JSONObject) actual, group,
								representation)
						: Talent.getTalent(name, talentGroup, currentTalent, ((JSONArray) actual).getObj(0), group);
			} else {
				actualTalent = representation != null
						? Spell.getSpell(name, currentTalent, ((JSONObject) actual).getObj(representation), (JSONObject) actual, group, representation)
						: Talent.getTalent(name, talentGroup, currentTalent, (JSONObject) actual, group);
			}
		}
		talents.put(name, actualTalent);
		return actualTalent;
	}

	private void recalculateCanContinue() {
		for (final BooleanProperty page : toSelect) {
			if (!page.get()) {
				canContinue.set(false);
				return;
			}
		}
		canContinue.set(true);
	}
}
